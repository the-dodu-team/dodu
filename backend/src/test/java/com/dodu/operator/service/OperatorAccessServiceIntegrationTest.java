package com.dodu.operator.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dodu.common.exception.BusinessException;
import com.dodu.common.exception.ErrorType;
import com.dodu.operator.repository.OperatorAccessRepository;
import com.dodu.operator.service.OperatorAccessService.IssuedCredential;
import com.dodu.participant.domain.AccessCode;
import com.dodu.participant.domain.SecretHasher;
import com.dodu.participant.repository.ParticipantAccessRepository;
import com.dodu.participant.service.ParticipantEntryService;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * 운영자 접근 코드 발급·재발급·철회 통합 검증(service + repository + 실제 DB).
 * 입장 쪽은 실제 {@link ParticipantEntryService}를 써서 "발급한 코드로 들어가지는지, 폐기·철회 뒤엔
 * 막히는지"를 끝까지 확인합니다.
 */
@Testcontainers
class OperatorAccessServiceIntegrationTest {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    private static final Instant NOW = Instant.parse("2026-10-20T03:00:00Z");
    private static final byte[] PEPPER = "test-only-pepper-0123456789abcdef0123456789abcdef".getBytes();
    private static final String DEVICE_A = "device-key-aaaaaaaaaaaaaaaa";
    private static final String OPERATOR = "test-operator";

    private static JdbcClient jdbc;
    private static SecretHasher hasher;
    private static OperatorAccessService operatorService;
    private static ParticipantEntryService entryService;

    @BeforeAll
    static void setUp() {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        jdbc = JdbcClient.create(dataSource);
        hasher = new SecretHasher(PEPPER);
        Clock clock = Clock.fixed(NOW, ZoneId.of("Asia/Seoul"));
        DataSourceTransactionManager tm = new DataSourceTransactionManager(dataSource);
        ParticipantAccessRepository accessRepository = new ParticipantAccessRepository(jdbc);
        SecureRandom random = new SecureRandom();

        operatorService = new OperatorAccessService(
                new OperatorAccessRepository(jdbc), accessRepository, hasher, random, clock, tm);
        entryService = new ParticipantEntryService(accessRepository, hasher, random, clock, tm);
    }

    @BeforeEach
    void clean() {
        jdbc.sql("truncate operator_audit_log, notification_subscription, participant_session, "
                + "participant_device, access_credential, participant, study_period cascade").update();
    }

    // ---------- 최초 발급 ----------

    @Test
    void issuedCodeAllowsEntryAndStoresOnlyHash() {
        UUID participantId = seedParticipantWithoutCredential();

        IssuedCredential issued = operatorService.issue(participantId, OPERATOR);

        assertEquals(1, count("select count(*) from access_credential where revoked_at is null"));
        // 원문 코드는 저장되지 않고 해시만 저장됩니다.
        String normalized = AccessCode.normalize(issued.code()).orElseThrow();
        assertEquals(1, count("select count(*) from access_credential where secret_hash = '"
                + hasher.hash(normalized) + "'"));
        assertEquals(0, count("select count(*) from access_credential where secret_hash = '"
                + normalized + "'"));
        assertEquals(43, entryService.enter(issued.code(), DEVICE_A).sessionToken().length());
    }

    @Test
    void issueIsRejectedWhenActiveCredentialExists() {
        UUID participantId = seedParticipantWithoutCredential();
        operatorService.issue(participantId, OPERATOR);

        BusinessException e = assertThrows(BusinessException.class,
                () -> operatorService.issue(participantId, OPERATOR));

        assertEquals(ErrorType.CONFLICT, e.type());
        assertEquals(OperatorAccessService.ACTIVE_CREDENTIAL_EXISTS, e.code());
        assertEquals(1, count("select count(*) from access_credential"));
    }

    @Test
    void issueForUnknownParticipantIsNotFound() {
        BusinessException e = assertThrows(BusinessException.class,
                () -> operatorService.issue(UUID.randomUUID(), OPERATOR));

        assertEquals(ErrorType.NOT_FOUND, e.type());
        assertEquals(OperatorAccessService.PARTICIPANT_NOT_FOUND, e.code());
    }

    @Test
    void blankOperatorRefIsRejectedBeforeAnyChange() {
        UUID participantId = seedParticipantWithoutCredential();

        for (String bad : new String[] {null, "", "   ", "x".repeat(129)}) {
            BusinessException e = assertThrows(BusinessException.class,
                    () -> operatorService.issue(participantId, bad));
            assertEquals(OperatorAccessService.OPERATOR_REF_INVALID, e.code());
        }
        assertEquals(0, count("select count(*) from access_credential"));
        assertEquals(0, count("select count(*) from operator_audit_log"));
    }

    // ---------- 재발급 ----------

    @Test
    void reissueRevokesOldCodeEndsSessionAndSubscriptionAndLinksNewCredential() {
        UUID participantId = seedParticipantWithoutCredential();
        IssuedCredential first = operatorService.issue(participantId, OPERATOR);
        entryService.enter(first.code(), DEVICE_A);
        jdbc.sql("""
                insert into notification_subscription
                    (participant_id, participant_session_id, subscription_key_hash, lifecycle_status, activated_at)
                select participant_id, id, 'sub-hash', 'Active', now() from participant_session
                """).update();

        IssuedCredential second = operatorService.reissue(participantId, OPERATOR);

        assertNotEquals(first.credentialId(), second.credentialId());
        // 구 코드는 막히고 새 코드는 입장됩니다.
        BusinessException oldCode = assertThrows(BusinessException.class,
                () -> entryService.enter(first.code(), DEVICE_A));
        assertEquals(ParticipantEntryService.INVALID_CODE, oldCode.code());
        assertEquals(43, entryService.enter(second.code(), DEVICE_A).sessionToken().length());
        // 구 코드는 폐기되고 새 코드가 구 코드를 가리킵니다.
        assertEquals(1, count("select count(*) from access_credential where id = '" + first.credentialId()
                + "' and revoked_at is not null"));
        assertEquals(1, count("select count(*) from access_credential where id = '" + second.credentialId()
                + "' and replaces_credential_id = '" + first.credentialId() + "'"));
        assertEquals(1, count("select count(*) from access_credential where revoked_at is null"));
        // 재발급 때 열려 있던 세션은 CREDENTIAL_REVOKED로, 구독은 비활성으로 종료됩니다.
        assertEquals(1, count("select count(*) from participant_session "
                + "where lifecycle_status = 'Ended' and revoke_reason = 'CREDENTIAL_REVOKED'"));
        assertEquals(1, count("select count(*) from notification_subscription where lifecycle_status = 'Inactive'"));
    }

    @Test
    void reissueWithoutActiveCredentialIsRejected() {
        UUID participantId = seedParticipantWithoutCredential();

        BusinessException e = assertThrows(BusinessException.class,
                () -> operatorService.reissue(participantId, OPERATOR));

        assertEquals(ErrorType.CONFLICT, e.type());
        assertEquals(OperatorAccessService.NO_ACTIVE_CREDENTIAL, e.code());
        assertEquals(0, count("select count(*) from access_credential"));
    }

    @Test
    void reissueCanRepeatAndOnlyLatestCodeWorks() {
        UUID participantId = seedParticipantWithoutCredential();
        IssuedCredential c1 = operatorService.issue(participantId, OPERATOR);
        IssuedCredential c2 = operatorService.reissue(participantId, OPERATOR);
        IssuedCredential c3 = operatorService.reissue(participantId, OPERATOR);

        for (IssuedCredential old : List.of(c1, c2)) {
            assertThrows(BusinessException.class, () -> entryService.enter(old.code(), DEVICE_A));
        }
        assertEquals(43, entryService.enter(c3.code(), DEVICE_A).sessionToken().length());
        assertEquals(3, count("select count(*) from access_credential"));
        assertEquals(1, count("select count(*) from access_credential where revoked_at is null"));
    }

    // ---------- 철회 ----------

    @Test
    void withdrawEndsEverythingAndBlocksEntry() {
        UUID participantId = seedParticipantWithoutCredential();
        IssuedCredential issued = operatorService.issue(participantId, OPERATOR);
        entryService.enter(issued.code(), DEVICE_A);
        Instant requestedAt = NOW.minusSeconds(3600);

        OperatorAccessService.WithdrawResult result =
                operatorService.withdraw(participantId, requestedAt, OPERATOR);

        assertEquals(1, result.endedSessions());
        assertEquals(1, count("select count(*) from participant where id = '" + participantId
                + "' and withdrawn_at is not null and access_disabled_at is not null "
                + "and withdrawal_requested_at is not null and withdrawal_requested_at < withdrawn_at"));
        assertEquals(0, count("select count(*) from access_credential where revoked_at is null"));
        assertEquals(1, count("select count(*) from participant_session "
                + "where lifecycle_status = 'Ended' and revoke_reason = 'ACCESS_REVOKED'"));
        BusinessException e = assertThrows(BusinessException.class,
                () -> entryService.enter(issued.code(), DEVICE_A));
        assertEquals(ParticipantEntryService.INVALID_CODE, e.code());
    }

    @Test
    void withdrawTwiceIsRejected() {
        UUID participantId = seedParticipantWithoutCredential();
        operatorService.issue(participantId, OPERATOR);
        operatorService.withdraw(participantId, NOW.minusSeconds(60), OPERATOR);

        BusinessException e = assertThrows(BusinessException.class,
                () -> operatorService.withdraw(participantId, NOW.minusSeconds(30), OPERATOR));

        assertEquals(OperatorAccessService.PARTICIPANT_WITHDRAWN, e.code());
    }

    @Test
    void withdrawRequestedInTheFutureOrMissingIsValidationError() {
        UUID participantId = seedParticipantWithoutCredential();
        operatorService.issue(participantId, OPERATOR);

        for (Instant bad : new Instant[] {null, NOW.plusSeconds(1)}) {
            BusinessException e = assertThrows(BusinessException.class,
                    () -> operatorService.withdraw(participantId, bad, OPERATOR));
            assertEquals(ErrorType.VALIDATION, e.type());
            assertEquals(OperatorAccessService.WITHDRAWAL_REQUESTED_AT_INVALID, e.code());
        }
        assertEquals(0, count("select count(*) from participant where withdrawn_at is not null"));
    }

    @Test
    void issueAndReissueAreRejectedAfterWithdrawal() {
        UUID participantId = seedParticipantWithoutCredential();
        operatorService.issue(participantId, OPERATOR);
        operatorService.withdraw(participantId, NOW.minusSeconds(60), OPERATOR);

        assertEquals(OperatorAccessService.PARTICIPANT_WITHDRAWN,
                assertThrows(BusinessException.class, () -> operatorService.issue(participantId, OPERATOR)).code());
        assertEquals(OperatorAccessService.PARTICIPANT_WITHDRAWN,
                assertThrows(BusinessException.class, () -> operatorService.reissue(participantId, OPERATOR)).code());
    }

    // ---------- 감사 로그 ----------

    @Test
    void successfulActionsAreAuditedWithoutSecrets() {
        UUID participantId = seedParticipantWithoutCredential();
        IssuedCredential issued = operatorService.issue(participantId, OPERATOR);
        IssuedCredential reissued = operatorService.reissue(participantId, OPERATOR);
        operatorService.withdraw(participantId, NOW.minusSeconds(60), OPERATOR);

        assertEquals(1, count("select count(*) from operator_audit_log where action_type = 'ISSUE_CREDENTIAL' "
                + "and result = 'SUCCESS' and operator_ref = '" + OPERATOR + "'"));
        assertEquals(1, count("select count(*) from operator_audit_log where action_type = 'REISSUE_CREDENTIAL'"));
        assertEquals(1, count("select count(*) from operator_audit_log where action_type = 'WITHDRAW'"));
        // 코드·해시 원문은 감사 로그 어디에도 들어가지 않습니다.
        for (IssuedCredential c : List.of(issued, reissued)) {
            String normalized = AccessCode.normalize(c.code()).orElseThrow();
            assertEquals(0, count("select count(*) from operator_audit_log where detail::text like '%"
                    + normalized + "%' or detail::text like '%" + hasher.hash(normalized) + "%'"));
        }
    }

    @Test
    void rejectedActionLeavesNoAuditRowAndNoPartialChange() {
        UUID participantId = seedParticipantWithoutCredential();

        assertThrows(BusinessException.class, () -> operatorService.reissue(participantId, OPERATOR));

        assertEquals(0, count("select count(*) from operator_audit_log"));
        assertEquals(0, count("select count(*) from access_credential"));
    }

    @Test
    void issuedCredentialToStringHidesCode() {
        IssuedCredential issued = new IssuedCredential(UUID.randomUUID(), "ABCD-EFGH-JKLM");

        assertFalse(issued.toString().contains("ABCD"));
        assertTrue(issued.toString().contains("***"));
    }

    // ---------- 동시성 ----------

    @Test
    void concurrentReissuesLeaveExactlyOneActiveCredential() throws Exception {
        UUID participantId = seedParticipantWithoutCredential();
        operatorService.issue(participantId, OPERATOR);
        int threads = 6;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<IssuedCredential>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            futures.add(pool.submit(() -> {
                start.await();
                return operatorService.reissue(participantId, OPERATOR);
            }));
        }
        start.countDown();
        int succeeded = 0;
        for (Future<IssuedCredential> future : futures) {
            try {
                future.get();
                succeeded++;
            } catch (ExecutionException e) {
                assertTrue(e.getCause() instanceof BusinessException, String.valueOf(e.getCause()));
            }
        }
        pool.shutdown();

        // 참가자 행 잠금으로 직렬화되므로 6번 모두 성공하고, 활성 코드는 항상 하나입니다.
        assertEquals(threads, succeeded);
        assertEquals(1, count("select count(*) from access_credential where revoked_at is null"));
        assertEquals(threads + 1, count("select count(*) from access_credential"));
        assertEquals(threads, count("select count(*) from access_credential where replaces_credential_id is not null"));
    }

    @Test
    void reissueRacingWithEntryNeverLeavesSessionOnOldCredential() throws Exception {
        UUID participantId = seedParticipantWithoutCredential();
        IssuedCredential first = operatorService.issue(participantId, OPERATOR);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);

        Future<?> entry = pool.submit(() -> {
            start.await();
            try {
                entryService.enter(first.code(), DEVICE_A);
            } catch (BusinessException ignored) {
                // 재발급이 먼저 반영되면 구 코드 입장이 거부되는 것이 정상입니다.
            }
            return null;
        });
        Future<IssuedCredential> reissue = pool.submit(() -> {
            start.await();
            return operatorService.reissue(participantId, OPERATOR);
        });
        start.countDown();
        entry.get();
        reissue.get();
        pool.shutdown();

        // 어떤 순서로 실행되든, 구 코드로 만든 활성 세션이 남아 있으면 안 됩니다.
        assertEquals(0, count("select count(*) from participant_session where lifecycle_status = 'Active'"));
        assertEquals(1, count("select count(*) from access_credential where revoked_at is null"));
    }

    // ---------- 헬퍼 ----------

    /** 연구 기간과 참가자만 만들고 접근 자격은 만들지 않습니다(운영자 발급 흐름 검증용). */
    private UUID seedParticipantWithoutCredential() {
        UUID studyId = jdbc.sql("""
                        insert into study_period (code, access_ends_at)
                        values (:code, :ends) returning id
                        """)
                .param("code", "study-" + UUID.randomUUID())
                .param("ends", OffsetDateTime.ofInstant(NOW.plusSeconds(30L * 24 * 3600), ZoneOffset.UTC))
                .query(UUID.class).single();
        return jdbc.sql("""
                        insert into participant (study_period_id, public_code, lifecycle_status, enrolled_at)
                        values (:study, :code, 'Enrolled', now()) returning id
                        """)
                .param("study", studyId)
                .param("code", "P-" + UUID.randomUUID())
                .query(UUID.class).single();
    }

    private static int count(String sql) {
        return jdbc.sql(sql).query(Integer.class).single();
    }
}