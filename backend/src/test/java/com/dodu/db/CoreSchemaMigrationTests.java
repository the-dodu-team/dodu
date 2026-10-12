package com.dodu.db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * V1 마이그레이션 검증. Spring 컨텍스트 없이 Flyway와 JDBC만 사용합니다.
 * 컨테이너는 테스트 실행마다 새로 뜨므로 로컬 DB·연결정보에 의존하지 않습니다.
 *
 * 주의: Testcontainers 패키지·클래스 이름은 사용하는 버전(1.x/2.x)에 따라 다릅니다.
 * 컴파일 오류가 나면 start.spring.io가 생성한 TestcontainersConfiguration의 import를 기준으로 맞춥니다.
 */
@Testcontainers
class CoreSchemaMigrationTests {
    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    private static Flyway flyway;

    @BeforeAll
    static void migrate() {
        flyway = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .load();
        flyway.migrate();
    }

    @Test
    void coreAndAuthTablesAreCreatedOnCleanDatabase() throws SQLException {
        assertEquals(13, count("select count(*) from information_schema.tables where table_schema = 'public' "
                + "and table_name in ('study_period','policy_version','participant','promise',"
                + "'promise_schedule_revision','promise_date_usage','auth_attempt','validation_survey','behavior_event',"
                + "'access_credential','participant_device','participant_session','notification_subscription')"));
    }

    @Test
    void rerunningMigrationAppliesNothingMore() {
        assertEquals(0, flyway.migrate().migrationsExecuted);
        assertEquals(1, flyway.info().applied().length);
    }

    @Test
    void scheduledDateKstMustMatchKstDateOfScheduledAt() throws SQLException {
        seedParticipant("A");
        assertViolates("ck_promise_scheduled_date_kst",
                () -> exec(promiseInsert("A", "2026-10-07T00:30:00+09:00", "2026-10-06")));
        exec(promiseInsert("A", "2026-10-07T00:30:00+09:00", "2026-10-07"));
    }

    @Test
    void onlyOneDateUsageRowIsAllowedPerParticipantAndKstDate() throws SQLException {
        String participant = seedParticipant("B");
        String insert = "insert into promise_date_usage(participant_id, scheduled_date_kst, first_used_at) "
                + "values ('" + participant + "', '2026-10-07', now())";
        exec(insert);
        assertViolates("uq_promise_date_usage", () -> exec(insert));
    }

    @Test
    void recreationAndRecoveryCountsAreEachCappedAtOne() throws SQLException {
        String participant = seedParticipant("C");
        exec("insert into promise_date_usage(participant_id, scheduled_date_kst, first_used_at, "
                + "user_recreation_count, technical_recovery_count) values ('" + participant
                + "', '2026-10-08', now(), 1, 1)");
        assertViolates("ck_user_recreation_count", () -> exec("update promise_date_usage "
                + "set user_recreation_count = 2 where participant_id = '" + participant + "'"));
    }

    @Test
    void onlyOneEvaluatingPhotoIsAllowedPerPromise() throws SQLException {
        String promise = seedPromise("D");
        exec(attemptInsert(promise, "00000000-0000-4000-8000-0000000000d1", "Evaluating"));
        assertViolates("ux_auth_attempt_one_evaluating_per_promise",
                () -> exec(attemptInsert(promise, "00000000-0000-4000-8000-0000000000d2", "Evaluating")));
        exec(attemptInsert(promise, "00000000-0000-4000-8000-0000000000d3", "Rejected"));
    }

    @Test
    void sameRequestKeyKeepsTheSameAttempt() throws SQLException {
        String promise = seedPromise("E");
        String insert = attemptInsert(promise, "00000000-0000-4000-8000-0000000000e1", "Rejected");
        exec(insert);
        assertViolates("uq_auth_attempt_request", () -> exec(insert));
    }

    @Test
    void onlyOneSurveyIsAllowedPerPromise() throws SQLException {
        String promise = seedPromise("F");
        String insert = "insert into validation_survey(promise_id, questionnaire_version, lifecycle_status, "
                + "eligibility_status) values ('" + promise + "', 'q1', 'Open', 'Eligible')";
        exec(insert);
        assertViolates("uq_validation_survey_promise", () -> exec(insert));
    }

    @Test
    void eventWithAttemptRequiresPromise() throws SQLException {
        String promise = seedPromise("G");
        String participant = scalar("select participant_id::text from promise where id = '" + promise + "'");
        exec(attemptInsert(promise, "00000000-0000-4000-8000-0000000000a7", "Rejected"));
        String attempt = scalar("select id::text from auth_attempt where promise_id = '" + promise + "'");
        assertViolates("ck_behavior_event_attempt_requires_promise", () -> exec("insert into behavior_event("
                + "event_id, participant_id, auth_attempt_id, event_type, occurred_at, received_at) values ("
                + "gen_random_uuid(), '" + participant + "', '" + attempt + "', 'x', now(), now())"));
    }

    @Test
    void onlyOneActiveSessionPerParticipantAndNewSessionAllowedAfterRevoke() throws SQLException {
        String participant = seedParticipant("H");
        String device = seedDevice(participant, "dev-h");
        exec(sessionInsert(participant, device, "token-h1", "Active"));
        assertViolates("ux_participant_session_one_active_per_participant",
                () -> exec(sessionInsert(participant, device, "token-h2", "Active")));
        exec("update participant_session set lifecycle_status = 'Revoked', revoked_at = now() "
                + "where session_token_hash = 'token-h1'");
        exec(sessionInsert(participant, device, "token-h2", "Active"));
    }

    @Test
    void deviceKeyHashIsUniqueOnlyWithinParticipant() throws SQLException {
        String first = seedParticipant("I1");
        String second = seedParticipant("I2");
        seedDevice(first, "same-device-key");
        seedDevice(second, "same-device-key");
        assertViolates("uq_participant_device_key", () -> seedDevice(first, "same-device-key"));
    }

    @Test
    void credentialCanBeReplacedOnlyOnceAndSecretHashIsUnique() throws SQLException {
        String participant = seedParticipant("J");
        exec("insert into access_credential(participant_id, secret_hash, issued_at) "
                + "values ('" + participant + "', 'secret-j1', now())");
        String original = scalar("select id::text from access_credential where secret_hash = 'secret-j1'");
        exec("insert into access_credential(participant_id, secret_hash, issued_at, replaces_credential_id) "
                + "values ('" + participant + "', 'secret-j2', now(), '" + original + "')");
        assertViolates("uq_access_credential_replaces", () -> exec("insert into access_credential("
                + "participant_id, secret_hash, issued_at, replaces_credential_id) values ('" + participant
                + "', 'secret-j3', now(), '" + original + "')"));
        assertViolates("uq_access_credential_secret_hash", () -> exec("insert into access_credential("
                + "participant_id, secret_hash, issued_at) values ('" + participant + "', 'secret-j1', now())"));
    }

    private static void assertViolates(String constraintName, Executable action) {
        SQLException error = assertThrows(SQLException.class, action);
        assertTrue(error.getMessage().contains(constraintName), error.getMessage());
    }

    private static String seedParticipant(String code) throws SQLException {
        String study = scalarOrNull("select id::text from study_period where code = 'S'");
        if (study == null) {
            exec("insert into study_period(code) values ('S')");
            study = scalar("select id::text from study_period where code = 'S'");
        }
        if (scalarOrNull("select id::text from policy_version where version = 'v1'") == null) {
            exec("insert into policy_version(version, effective_from) values ('v1', now())");
        }
        exec("insert into participant(study_period_id, public_code, lifecycle_status, enrolled_at) "
                + "values ('" + study + "', '" + code + "', 'Active', now())");
        return scalar("select id::text from participant where public_code = '" + code + "'");
    }

    private static String seedPromise(String code) throws SQLException {
        String participant = seedParticipant(code);
        exec(promiseInsert(code, "2026-10-07T20:00:00+09:00", "2026-10-07"));
        return scalar("select id::text from promise where participant_id = '" + participant + "'");
    }

    private static String seedDevice(String participantId, String deviceKeyHash) throws SQLException {
        exec("insert into participant_device(participant_id, device_key_hash, lifecycle_status, first_seen_at, "
                + "last_seen_at) values ('" + participantId + "', '" + deviceKeyHash + "', 'Active', now(), now())");
        return scalar("select id::text from participant_device where participant_id = '" + participantId
                + "' and device_key_hash = '" + deviceKeyHash + "'");
    }

    private static String promiseInsert(String participantCode, String scheduledAt, String dateKst) {
        return "insert into promise(participant_id, policy_version_id, task_title, scheduled_at, "
                + "scheduled_date_kst, tool_type, creation_type, lifecycle_status) values ("
                + "(select id from participant where public_code = '" + participantCode + "'), "
                + "(select id from policy_version where version = 'v1'), 't', '" + scheduledAt + "', '"
                + dateKst + "', 'laptop', 'normal', 'Scheduled')";
    }

    private static String attemptInsert(String promiseId, String requestId, String status) {
        return "insert into auth_attempt(promise_id, client_request_id, request_fingerprint, tool_type, "
                + "processing_status, request_received_at) values ('" + promiseId + "', '" + requestId
                + "', 'f', 'laptop', '" + status + "', now())";
    }

    private static String sessionInsert(String participantId, String deviceId, String tokenHash, String status) {
        return "insert into participant_session(participant_id, participant_device_id, session_token_hash, "
                + "lifecycle_status, issued_at, last_seen_at, expires_at) values ('" + participantId + "', '"
                + deviceId + "', '" + tokenHash + "', '" + status + "', now(), now(), now() + interval '1 day')";
    }

    private static Connection connect() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private static void exec(String sql) throws SQLException {
        try (Connection connection = connect(); Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private static String scalarOrNull(String sql) throws SQLException {
        try (Connection connection = connect();
             Statement statement = connection.createStatement();
             var resultSet = statement.executeQuery(sql)) {
            return resultSet.next() ? resultSet.getString(1) : null;
        }
    }

    private static String scalar(String sql) throws SQLException {
        return scalarOrNull(sql);
    }

    private static long count(String sql) throws SQLException {
        return Long.parseLong(scalar(sql));
    }
}