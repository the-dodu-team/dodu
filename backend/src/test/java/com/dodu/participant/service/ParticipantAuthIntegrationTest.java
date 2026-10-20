package com.dodu.participant.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dodu.common.exception.BusinessException;
import com.dodu.common.exception.ErrorType;
import com.dodu.participant.domain.SecretHasher;
import com.dodu.participant.domain.SessionContext;
import com.dodu.participant.domain.SessionToken;
import com.dodu.participant.repository.ParticipantAccessRepository;
import com.dodu.participant.service.ParticipantEntryService.EnterResult;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;
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
 * 세션 인증 통합 검증(service + repository + 실제 DB). 입장 service로 세션을 만든 뒤 그 토큰으로 인증하며,
 * 조회 SQL(조인, 상태 컬럼)과 마지막 접속 시각의 조건부 갱신을 확인합니다.
 * 접근 판정 규칙의 경계값은 {@code SessionContextTest}가 확인합니다.
 */
@Testcontainers
class ParticipantAuthIntegrationTest {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    private static final Instant NOW = Instant.parse("2026-10-20T03:00:00Z");
    private static final byte[] PEPPER = "test-only-pepper-0123456789abcdef0123456789abcdef".getBytes();
    private static final String DEVICE_A = "device-key-aaaaaaaaaaaaaaaa";
    private static final String DEVICE_B = "device-key-bbbbbbbbbbbbbbbb";

    private static JdbcClient jdbc;
    private static SecretHasher hasher;
    private static ParticipantEntryService entryService;
    private static ParticipantAuthService authService;

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
        ParticipantAccessRepository repository = new ParticipantAccessRepository(jdbc);
        entryService = new ParticipantEntryService(
                repository, hasher, new SecureRandom(), clock, new DataSourceTransactionManager(dataSource));
        authService = new ParticipantAuthService(repository, hasher, clock);
    }

    @BeforeEach
    void clean() {
        jdbc.sql("truncate notification_subscription, participant_session, participant_device, "
                + "access_credential, participant, study_period cascade").update();
    }

    @Test
    void tokenIssuedAtEntryAuthenticatesTheSameParticipant() {
        String code = seedParticipant(accessEnd(30));
        EnterResult entered = entryService.enter(code, DEVICE_A);

        SessionContext result = authService.authenticate("Bearer " + entered.sessionToken());

        UUID participantId = jdbc.sql("select id from participant").query(UUID.class).single();
        assertEquals(participantId, result.participant().id());
        assertEquals(participantId, result.session().participantId());
        assertEquals(jdbc.sql("select id from participant_session").query(UUID.class).single(), result.session().id());
        assertFalse(result.participant().onboardingCompleted());
        assertEquals(entered.expiresAt(), result.expiresAt());
    }

    @Test
    void shortenedAccessEndMakesExpiresAtFollowTheStudyPeriod() {
        String code = seedParticipant(accessEnd(30));
        EnterResult entered = entryService.enter(code, DEVICE_A);
        // 운영자가 연구 기간 종료일을 앞당겨도 아직 지나지 않았으면 접근은 허용되고, 만료 시각은 새 종료일을 따릅니다.
        jdbc.sql("update study_period set access_ends_at = :t")
                .param("t", OffsetDateTime.ofInstant(accessEnd(5), ZoneOffset.UTC)).update();

        SessionContext result = authService.authenticate("Bearer " + entered.sessionToken());

        assertEquals(accessEnd(5), result.expiresAt());
    }

    @Test
    void extendedAccessEndDoesNotExtendAnExistingSession() {
        String code = seedParticipant(accessEnd(30));
        EnterResult entered = entryService.enter(code, DEVICE_A);
        jdbc.sql("update study_period set access_ends_at = :t")
                .param("t", OffsetDateTime.ofInstant(accessEnd(60), ZoneOffset.UTC)).update();

        SessionContext result = authService.authenticate("Bearer " + entered.sessionToken());

        // 세션 자체의 만료는 발급 때 값 그대로라, 연장해도 이미 발급된 세션의 만료 시각을 넘지 않습니다.
        assertEquals(entered.expiresAt(), result.expiresAt());
    }

    @Test
    void onboardingCompletionIsReflected() {
        String code = seedParticipant(accessEnd(30));
        EnterResult entered = entryService.enter(code, DEVICE_A);
        jdbc.sql("update participant set onboarding_completed_at = now()").update();

        assertTrue(authService.authenticate("Bearer " + entered.sessionToken()).participant().onboardingCompleted());
    }

    @Test
    void unknownTokenIsAccessRequired() {
        seedParticipant(accessEnd(30));

        BusinessException e = assertThrows(BusinessException.class,
                () -> authService.authenticate("Bearer " + SessionToken.generate(new SecureRandom())));

        assertEquals(ErrorType.UNAUTHENTICATED, e.type());
        assertEquals(ParticipantAuthService.ACCESS_REQUIRED, e.code());
    }

    @Test
    void entryFromAnotherDeviceReplacesTheOldTokenOnly() {
        String code = seedParticipant(accessEnd(30));
        EnterResult first = entryService.enter(code, DEVICE_A);
        EnterResult second = entryService.enter(code, DEVICE_B);

        BusinessException e = assertThrows(BusinessException.class,
                () -> authService.authenticate("Bearer " + first.sessionToken()));

        assertEquals(ParticipantAuthService.SESSION_REPLACED, e.code());
        assertEquals(ErrorType.UNAUTHENTICATED, e.type());
        authService.authenticate("Bearer " + second.sessionToken());
    }

    @Test
    void withdrawnParticipantIsRevokedEvenIfSessionRowIsStillActive() {
        String code = seedParticipant(accessEnd(30));
        EnterResult entered = entryService.enter(code, DEVICE_A);
        jdbc.sql("update participant set withdrawn_at = now()").update();

        assertRevoked(entered.sessionToken());
    }

    @Test
    void accessDisabledParticipantIsRevoked() {
        String code = seedParticipant(accessEnd(30));
        EnterResult entered = entryService.enter(code, DEVICE_A);
        jdbc.sql("update participant set access_disabled_at = now()").update();

        assertRevoked(entered.sessionToken());
    }

    @Test
    void sessionEndedForNonReplacementReasonIsRevoked() {
        String code = seedParticipant(accessEnd(30));
        EnterResult entered = entryService.enter(code, DEVICE_A);
        // 코드 폐기·재발급 때 운영 스크립트가 세션을 이 상태로 종료할 예정입니다.
        jdbc.sql("update participant_session set lifecycle_status = 'Ended', revoked_at = now(), "
                + "revoke_reason = 'ACCESS_REVOKED'").update();

        assertRevoked(entered.sessionToken());
    }

    @Test
    void accessEndBeforeNowRevokesAnExistingSession() {
        String code = seedParticipant(accessEnd(30));
        EnterResult entered = entryService.enter(code, DEVICE_A);
        jdbc.sql("update study_period set access_ends_at = :t")
                .param("t", OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC)).update();

        assertRevoked(entered.sessionToken());
    }

    @Test
    void sessionPastItsOwnExpiryIsAccessRequired() {
        String code = seedParticipant(accessEnd(30));
        EnterResult entered = entryService.enter(code, DEVICE_A);
        jdbc.sql("update participant_session set expires_at = :t")
                .param("t", OffsetDateTime.ofInstant(NOW.minusSeconds(1), ZoneOffset.UTC)).update();

        BusinessException e = assertThrows(BusinessException.class,
                () -> authService.authenticate("Bearer " + entered.sessionToken()));

        assertEquals(ParticipantAuthService.ACCESS_REQUIRED, e.code());
    }

    @Test
    void lastSeenIsUpdatedOnlyWhenStale() {
        String code = seedParticipant(accessEnd(30));
        EnterResult entered = entryService.enter(code, DEVICE_A);

        Instant tenSecondsAgo = NOW.minusSeconds(10);
        setLastSeen(tenSecondsAgo);
        authService.authenticate("Bearer " + entered.sessionToken());
        assertEquals(tenSecondsAgo, lastSeen());

        setLastSeen(NOW.minusSeconds(120));
        authService.authenticate("Bearer " + entered.sessionToken());
        assertEquals(NOW, lastSeen());
    }

    @Test
    void failedAuthenticationDoesNotUpdateLastSeen() {
        String code = seedParticipant(accessEnd(30));
        EnterResult entered = entryService.enter(code, DEVICE_A);
        Instant old = NOW.minusSeconds(600);
        setLastSeen(old);
        jdbc.sql("update participant set withdrawn_at = now()").update();

        assertRevoked(entered.sessionToken());

        assertEquals(old, lastSeen());
    }

    private void assertRevoked(String sessionToken) {
        BusinessException e = assertThrows(BusinessException.class,
                () -> authService.authenticate("Bearer " + sessionToken));
        assertEquals(ErrorType.UNAUTHENTICATED, e.type());
        assertEquals(ParticipantAuthService.ACCESS_REVOKED, e.code());
    }

    private static void setLastSeen(Instant value) {
        jdbc.sql("update participant_session set last_seen_at = :t")
                .param("t", OffsetDateTime.ofInstant(value, ZoneOffset.UTC)).update();
    }

    private static Instant lastSeen() {
        return jdbc.sql("select last_seen_at from participant_session")
                .query(OffsetDateTime.class).single().toInstant();
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
        String code = com.dodu.participant.domain.AccessCode.generate(new SecureRandom());
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
}