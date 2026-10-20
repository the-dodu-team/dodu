package com.dodu.participant.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dodu.common.exception.BusinessException;
import com.dodu.common.exception.ErrorType;
import com.dodu.participant.domain.AccessCode;
import com.dodu.participant.domain.SecretHasher;
import com.dodu.participant.repository.ParticipantAccessRepository;
import com.dodu.participant.service.ParticipantEntryService.EnterResult;
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
 * 입장·세션 발급 통합 검증(service + repository + 실제 DB). Spring 컨텍스트 없이 실제 PostgreSQL
 * (Testcontainers)에 V1·V2 마이그레이션을 적용하고 service·repository를 직접 조립합니다.
 * 행 잠금, 부분 유니크 인덱스, 일괄 종료 update처럼 SQL 동작이 핵심인 부분을 확인합니다.
 * 업무 규칙 분기는 {@link ParticipantEntryServiceTest}가 repository 없이 확인합니다.
 */
@Testcontainers
class ParticipantEntryIntegrationTest {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    private static final Instant NOW = Instant.parse("2026-10-20T03:00:00Z");
    private static final byte[] PEPPER = "test-only-pepper-0123456789abcdef0123456789abcdef".getBytes();
    private static final String DEVICE_A = "device-key-aaaaaaaaaaaaaaaa";
    private static final String DEVICE_B = "device-key-bbbbbbbbbbbbbbbb";

    private static DriverManagerDataSource dataSource;
    private static JdbcClient jdbc;
    private static ParticipantEntryService service;
    private static SecretHasher hasher;

    @BeforeAll
    static void setUp() {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();
        dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        jdbc = JdbcClient.create(dataSource);
        hasher = new SecretHasher(PEPPER);
        service = new ParticipantEntryService(
                new ParticipantAccessRepository(jdbc),
                hasher,
                new SecureRandom(),
                Clock.fixed(NOW, ZoneId.of("Asia/Seoul")),
                new DataSourceTransactionManager(dataSource));
    }

    @BeforeEach
    void clean() {
        jdbc.sql("truncate notification_subscription, participant_session, participant_device, "
                + "access_credential, participant, study_period cascade").update();
    }

    @Test
    void validCodeCreatesSessionAndStoresOnlyHashes() {
        String code = seedParticipant(accessEnd(30));

        EnterResult result = service.enter(AccessCode.format(code), DEVICE_A);

        assertEquals(43, result.sessionToken().length());
        assertFalse(result.onboardingCompleted());
        assertEquals(NOW.plusSeconds(30L * 24 * 3600), result.expiresAt());
        assertEquals(1, count("select count(*) from participant_session where lifecycle_status = 'Active'"));
        // 원문 토큰·코드·기기 키는 어디에도 저장되지 않습니다.
        assertEquals(0, count("select count(*) from participant_session where session_token_hash = '"
                + result.sessionToken() + "'"));
        assertEquals(1, count("select count(*) from participant_session where session_token_hash = '"
                + hasher.hash(result.sessionToken()) + "'"));
        assertEquals(0, count("select count(*) from participant_device where device_key_hash = '" + DEVICE_A + "'"));
        assertEquals(1, count("select count(*) from participant_device where device_key_hash = '"
                + hasher.hash(DEVICE_A) + "'"));
    }

    @Test
    void onboardingCompletedFlagFollowsParticipant() {
        String code = seedParticipant(accessEnd(30));
        jdbc.sql("update participant set onboarding_completed_at = now()").update();

        assertTrue(service.enter(code, DEVICE_A).onboardingCompleted());
    }

    @Test
    void entryFromAnotherDeviceEndsPreviousSessionSubscriptionAndDevice() {
        String code = seedParticipant(accessEnd(30));
        service.enter(code, DEVICE_A);
        jdbc.sql("""
                insert into notification_subscription
                    (participant_id, participant_session_id, subscription_key_hash, lifecycle_status, activated_at)
                select participant_id, id, 'sub-hash', 'Active', now() from participant_session
                """).update();

        service.enter(code, DEVICE_B);

        assertEquals(1, count("select count(*) from participant_session where lifecycle_status = 'Active'"));
        assertEquals(1, count("select count(*) from participant_session "
                + "where lifecycle_status = 'Ended' and revoke_reason = 'SESSION_REPLACED'"));
        assertEquals(1, count("select count(*) from notification_subscription where lifecycle_status = 'Inactive'"));
        assertEquals(1, count("select count(*) from participant_device where lifecycle_status = 'Active'"));
        assertEquals(2, count("select count(*) from participant_device"));
    }

    @Test
    void reentryFromSameDeviceReusesDeviceRow() {
        String code = seedParticipant(accessEnd(30));

        service.enter(code, DEVICE_A);
        service.enter(code, DEVICE_A);

        assertEquals(1, count("select count(*) from participant_device"));
        assertEquals(1, count("select count(*) from participant_session where lifecycle_status = 'Active'"));
        assertEquals(1, count("select count(*) from participant_session where lifecycle_status = 'Ended'"));
    }

    @Test
    void invalidRevokedExpiredAndWithdrawnCodesGetTheSameError() {
        String revoked = seedParticipant(accessEnd(30));
        jdbc.sql("update access_credential set revoked_at = now() where secret_hash = :h")
                .param("h", hasher.hash(revoked)).update();
        String expired = seedParticipant(accessEnd(30));
        jdbc.sql("update access_credential set expires_at = :t where secret_hash = :h")
                .param("t", OffsetDateTime.ofInstant(NOW.minusSeconds(1), ZoneOffset.UTC))
                .param("h", hasher.hash(expired)).update();
        String withdrawn = seedParticipant(accessEnd(30));
        jdbc.sql("update participant set withdrawn_at = now() where id = "
                        + "(select participant_id from access_credential where secret_hash = :h)")
                .param("h", hasher.hash(withdrawn)).update();
        String disabled = seedParticipant(accessEnd(30));
        jdbc.sql("update participant set access_disabled_at = now() where id = "
                        + "(select participant_id from access_credential where secret_hash = :h)")
                .param("h", hasher.hash(disabled)).update();

        List<String> inputs = List.of(
                AccessCode.generate(new SecureRandom()), // 존재하지 않는 코드
                "not-a-code", "", revoked, expired, withdrawn, disabled);
        BusinessException first = null;
        for (String input : inputs) {
            BusinessException e = assertThrows(BusinessException.class, () -> service.enter(input, DEVICE_A));
            assertEquals(ErrorType.UNAUTHENTICATED, e.type());
            assertEquals(ParticipantEntryService.INVALID_CODE, e.code());
            if (first == null) {
                first = e;
            }
            assertEquals(first.getMessage(), e.getMessage());
        }
        assertEquals(0, count("select count(*) from participant_session"));
    }

    @Test
    void entryNullCodeIsInvalidCode() {
        BusinessException e = assertThrows(BusinessException.class, () -> service.enter(null, DEVICE_A));
        assertEquals(ParticipantEntryService.INVALID_CODE, e.code());
    }

    @Test
    void entryAfterAccessEndIsRejectedAsStudyPeriodEnded() {
        String code = seedParticipant(NOW.minusSeconds(1));

        BusinessException e = assertThrows(BusinessException.class, () -> service.enter(code, DEVICE_A));

        assertEquals(ErrorType.CONFLICT, e.type());
        assertEquals(ParticipantEntryService.STUDY_PERIOD_ENDED, e.code());
        assertEquals(0, count("select count(*) from participant_session"));
    }

    @Test
    void entryExactlyAtAccessEndIsRejected() {
        String code = seedParticipant(NOW);

        BusinessException e = assertThrows(BusinessException.class, () -> service.enter(code, DEVICE_A));

        assertEquals(ParticipantEntryService.STUDY_PERIOD_ENDED, e.code());
    }

    @Test
    void badDeviceKeyIsValidationError() {
        String code = seedParticipant(accessEnd(30));
        for (String bad : new String[] {null, "", "short", "x".repeat(129)}) {
            BusinessException e = assertThrows(BusinessException.class, () -> service.enter(code, bad));
            assertEquals(ErrorType.VALIDATION, e.type());
            assertEquals(ParticipantEntryService.DEVICE_KEY_INVALID, e.code());
            assertEquals("deviceKey", e.field());
        }
    }

    @Test
    void concurrentEntriesLeaveExactlyOneActiveSession() throws Exception {
        String code = seedParticipant(accessEnd(30));
        int threads = 6;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<EnterResult>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            String device = "device-key-concurrent-" + i + "-xxxxxxxx";
            futures.add(pool.submit(() -> {
                start.await();
                return service.enter(code, device);
            }));
        }
        start.countDown();
        int succeeded = 0;
        for (Future<EnterResult> future : futures) {
            try {
                future.get();
                succeeded++;
            } catch (java.util.concurrent.ExecutionException e) {
                // 인덱스 위반 재시도까지 실패한 경우에도 업무 오류(ENTRY_CONFLICT)여야 합니다.
                assertTrue(e.getCause() instanceof BusinessException, String.valueOf(e.getCause()));
            }
        }
        pool.shutdown();

        assertTrue(succeeded >= 1);
        assertEquals(1, count("select count(*) from participant_session where lifecycle_status = 'Active'"));
        assertEquals(1, count("select count(*) from participant_device where lifecycle_status = 'Active'"));
        assertNotEquals(0, count("select count(*) from participant_session"));
    }

    /** 연구 기간·참가자·활성 접근 자격을 만들고 정규화된 입장 코드를 돌려줍니다. */
    private String seedParticipant(Instant accessEndsAt) {
        UUID studyId = jdbc.sql("""
                        insert into study_period (code, access_ends_at)
                        values (:code, :ends) returning id
                        """)
                .param("code", "study-" + UUID.randomUUID())
                .param("ends", OffsetDateTime.ofInstant(accessEndsAt, ZoneOffset.UTC))
                .query(UUID.class).single();
        UUID participantId = jdbc.sql("""
                        insert into participant (study_period_id, public_code, lifecycle_status, enrolled_at)
                        values (:study, :code, 'Enrolled', now()) returning id
                        """)
                .param("study", studyId)
                .param("code", "P-" + UUID.randomUUID())
                .query(UUID.class).single();
        String code = AccessCode.generate(new SecureRandom());
        jdbc.sql("""
                        insert into access_credential (participant_id, secret_hash, issued_at)
                        values (:p, :h, now())
                        """)
                .param("p", participantId)
                .param("h", hasher.hash(code))
                .update();
        return code;
    }

    private static Instant accessEnd(int daysFromNow) {
        return NOW.plusSeconds(daysFromNow * 24L * 3600);
    }

    private static int count(String sql) {
        return jdbc.sql(sql).query(Integer.class).single();
    }
}