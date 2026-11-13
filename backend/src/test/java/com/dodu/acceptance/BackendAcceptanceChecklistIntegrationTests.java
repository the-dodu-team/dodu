package com.dodu.acceptance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import com.dodu.notification.NotificationDeliveryService;
import com.dodu.participant.domain.SecretHasher;
import com.dodu.participant.domain.SessionToken;
import com.dodu.promise.PromiseLifecycleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * 검증 체크리스트(BE) R-14·15·26·27·28·29·36·37·45·46을 고정 서버 시각으로 확인한다.
 * 서버 시각은 교체 가능한 Clock 빈으로 고정하고, 백그라운드 스케줄러는 테스트 중 돌지 않게 한다.
 */
@SpringBootTest(properties = {
        "dodu.notifications.scheduler-delay-ms=86400000",
        "dodu.photo-recovery.worker-delay-ms=86400000",
        "dodu.photo-retention.worker-delay-ms=86400000"})
@Import(BackendAcceptanceChecklistIntegrationTests.TestConfig.class)
class BackendAcceptanceChecklistIntegrationTests {
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final Instant BASE = kst("2026-11-16T10:00:00");

    @Autowired private WebApplicationContext context;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private SecretHasher secretHasher;
    @Autowired private MutableClock clock;
    @Autowired private NotificationDeliveryService notifications;
    @Autowired private PromiseLifecycleService lifecycle;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
        clock.set(BASE);
    }

    // R-14: 서버 현재 시각 +5분은 허용, 그보다 이른 시각은 거절
    @Test
    void r14_fiveMinuteLowerBoundUsesServerTime() throws Exception {
        Participant p = participant();
        create(p, BASE.plus(Duration.ofMinutes(5)).minusSeconds(1))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("PROMISE_SCHEDULE_TOO_SOON"));
        assertEquals(0, count("select count(*) from promise where participant_id = ?", p.id()));

        create(p, BASE.plus(Duration.ofMinutes(5))).andExpect(status().isOk());
        assertEquals(BASE.plus(Duration.ofMinutes(5)), jdbc.queryForObject(
                "select scheduled_at from promise where participant_id = ?", Timestamp.class, p.id()).toInstant());
    }

    // R-15: 서버 현재 시각 +72시간은 허용, 그 이후는 거절
    @Test
    void r15_seventyTwoHourUpperBoundUsesServerTime() throws Exception {
        Participant exact = participant();
        create(exact, BASE.plus(Duration.ofHours(72))).andExpect(status().isOk());
        assertEquals(1, count("select count(*) from promise where participant_id = ?", exact.id()));

        Participant over = participant();
        create(over, BASE.plus(Duration.ofHours(72)).plusSeconds(1))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("PROMISE_SCHEDULE_TOO_FAR"));
        assertEquals(0, count("select count(*) from promise where participant_id = ?", over.id()));
    }

    // R-26: T에 시작 알림이 한 번만 생성·기록되고 해당 약속과 연결
    @Test
    void r26_startNotificationIsCreatedOnceAtT() {
        Participant p = participant();
        subscribe(p);
        Instant t = BASE.plus(Duration.ofHours(1));
        UUID promise = promise(p, t, "Scheduled");

        notifications.dispatchDueNotifications(t.minusSeconds(1));
        assertEquals(0, deliveries(promise, "START", "IN_APP"));

        notifications.dispatchDueNotifications(t);
        notifications.dispatchDueNotifications(t.plusSeconds(30));
        notifications.dispatchDueNotifications(t.plusSeconds(90));
        assertEquals(1, deliveries(promise, "START", "IN_APP"));
        assertEquals(1, deliveries(promise, "START", "PUSH"));
        assertEquals(p.id(), jdbc.queryForObject(
                "select participant_id from notification_delivery where promise_id = ? and notification_type = 'START' and channel = 'IN_APP'",
                UUID.class, promise));
    }

    // R-27(1): 미제출 약속은 T+10, T+20 리마인더가 각 시점에 나간다
    @Test
    void r27_remindersAreSentAtTenAndTwentyWhenNotSubmitted() {
        Participant p = participant();
        Instant t = BASE.plus(Duration.ofHours(1));
        UUID promise = promise(p, t, "Scheduled");

        notifications.dispatchDueNotifications(t.plus(Duration.ofMinutes(10)).minusSeconds(1));
        assertEquals(0, deliveries(promise, "REMINDER_10", "IN_APP"));
        notifications.dispatchDueNotifications(t.plus(Duration.ofMinutes(10)));
        assertEquals(1, deliveries(promise, "REMINDER_10", "IN_APP"));
        assertEquals(0, deliveries(promise, "REMINDER_20", "IN_APP"));
        notifications.dispatchDueNotifications(t.plus(Duration.ofMinutes(20)));
        assertEquals(1, deliveries(promise, "REMINDER_20", "IN_APP"));
    }

    // R-27(2): Evaluating 중 생략한 리마인더는 판정이 끝난 뒤에도 늦게 보내지 않는다
    @Test
    void r27_reminderSkippedWhileEvaluatingIsNotSentLater() {
        Participant p = participant();
        Instant t = BASE.plus(Duration.ofHours(1));
        UUID promise = promise(p, t, "Scheduled");
        subscribe(p);
        UUID attempt = attempt(promise, t.plus(Duration.ofMinutes(5)), "Evaluating");

        notifications.dispatchDueNotifications(t.plus(Duration.ofMinutes(10)));
        assertEquals(0, deliveries(promise, "REMINDER_10", "IN_APP"));

        // 판정이 거절로 끝나 다시 인증 가능한 상태가 된 뒤 스케줄러가 돈다.
        jdbc.update("update auth_attempt set processing_status = 'Rejected', decided_at = ? where id = ?",
                Timestamp.from(t.plus(Duration.ofMinutes(12))), attempt);
        notifications.dispatchDueNotifications(t.plus(Duration.ofMinutes(15)));
        assertEquals(0, deliveries(promise, "REMINDER_10", "IN_APP"),
                "Evaluating 중 생략한 T+10 리마인더가 T+15에 늦게 생성되면 안 된다");
        assertEquals(0, deliveries(promise, "REMINDER_10", "PUSH"));
        assertEquals(1, count("select count(*) from notification_delivery where promise_id = ? and notification_type = 'REMINDER_10' and lifecycle_status = 'SUPPRESSED' and failure_code = 'EVALUATING'", promise));
        // 생략 이후 판정이 끝났다면 T+20 리마인더는 정상 발송된다.
        notifications.dispatchDueNotifications(t.plus(Duration.ofMinutes(20)));
        assertEquals(1, deliveries(promise, "REMINDER_20", "IN_APP"));
    }

    // R-28: T부터 T+30분 직전까지 접수, T+30분부터 거절.
    // 사진 제출 API는 주입 Clock 대신 Clock.systemUTC()를 쓰므로 실제 시각 기준으로 경계 근처를 확인한다.
    // 밀리초 단위 정확 경계는 InterventionWindowPolicyTests(acceptsTAndRejectsExactlyTPlusThirty...)가 고정 Clock으로 확인한다.
    @Test
    void r28_photoWindowIsTInclusiveAndTPlusThirtyExclusive() throws Exception {
        Instant now = Instant.now();

        Participant justAfterT = participant();
        UUID first = promise(justAfterT, now.minusSeconds(1), "Scheduled");
        submitPhoto(justAfterT, first).andExpect(status().isOk());

        Participant justBeforeDeadline = participant();
        UUID second = promise(justBeforeDeadline, now.minus(Duration.ofMinutes(30)).plusSeconds(5), "Scheduled");
        submitPhoto(justBeforeDeadline, second).andExpect(status().isOk());

        Participant atDeadline = participant();
        UUID third = promise(atDeadline, Instant.now().minus(Duration.ofMinutes(30)), "Scheduled");
        submitPhoto(atDeadline, third)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("PHOTO_WINDOW_CLOSED"));
        assertEquals(0, count("select count(*) from auth_attempt where promise_id = ?", third));

        Participant beforeT = participant();
        UUID fourth = promise(beforeT, Instant.now().plusSeconds(60), "Scheduled");
        submitPhoto(beforeT, fourth).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("PHOTO_WINDOW_CLOSED"));
    }

    // R-29: T+30분 전에 접수된 사진은 판정 결과가 마감 뒤에 와도 반영(결과 API도 실제 시각 사용)
    @Test
    void r29_lateAiResultForAcceptedPhotoIsApplied() throws Exception {
        Participant p = participant();
        Instant t = Instant.now().minus(Duration.ofMinutes(30)).plusSeconds(3);
        UUID promise = promise(p, t, "Scheduled");
        String attemptId = attemptIdOf(submitPhoto(p, promise).andExpect(status().isOk()));
        Instant receivedAt = jdbc.queryForObject("select valid_received_at from auth_attempt where id = ?",
                Timestamp.class, UUID.fromString(attemptId)).toInstant();
        assertTrue(receivedAt.isBefore(t.plus(Duration.ofMinutes(30))));

        Thread.sleep(Duration.between(Instant.now(), t.plus(Duration.ofMinutes(30))).toMillis() + 500);
        assertTrue(Instant.now().isAfter(t.plus(Duration.ofMinutes(30))));
        mockMvc.perform(post("/api/promises/" + promise + "/photo-attempts/" + attemptId + "/result")
                        .header("Authorization", p.auth()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"result\":\"COMPLETED\",\"modelVersion\":\"acceptance-model\"}"))
                .andExpect(status().isOk());

        assertEquals("AuthCompleted", jdbc.queryForObject(
                "select lifecycle_status from promise where id = ?", String.class, promise));
        assertEquals(receivedAt, jdbc.queryForObject(
                "select valid_received_at from auth_attempt where id = ?", Timestamp.class, UUID.fromString(attemptId)).toInstant());
    }

    // R-36: T=11:59 약속은 당일 18시, T=12:00 약속은 다음 날 11시에 한 번씩 안내
    @Test
    void r36_surveyGuidanceUsesKstNoonBoundaryOnce() {
        assertSurveyGuidance(kst("2026-11-16T11:59:00"), kst("2026-11-16T18:00:00"));
        assertSurveyGuidance(kst("2026-11-16T12:00:00"), kst("2026-11-17T11:00:00"));
    }

    // R-36 보충: 인증 완료 약속도 자기보고 설문 대상이어야 한다(인증 완료 ≠ 실제 시작).
    @Test
    void r36_completedPromiseAlsoReceivesSelfReportSurvey() throws Exception {
        Instant t = Instant.now().minusSeconds(60);
        Participant p = participant();
        UUID promise = promise(p, t, "Scheduled");
        String attemptId = attemptIdOf(submitPhoto(p, promise).andExpect(status().isOk()));
        mockMvc.perform(post("/api/promises/" + promise + "/photo-attempts/" + attemptId + "/result")
                        .header("Authorization", p.auth()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"result\":\"COMPLETED\"}"))
                .andExpect(status().isOk());
        clock.set(t.plus(Duration.ofHours(2)));
        lifecycle.reconcile(p.id());

        assertEquals(1, count("select count(*) from validation_survey where promise_id = ? and eligibility_status = 'AuthCompleted'", promise),
                "인증 완료 약속에도 자기보고 설문이 생성되어야 한다");
        Instant availableAt = jdbc.queryForObject("select available_at from validation_survey where promise_id = ?",
                Timestamp.class, promise).toInstant();
        assertEquals(new com.dodu.notification.NotificationSchedulePolicy().surveyReminderAt(t), availableAt);
        assertEquals(availableAt.plus(Duration.ofHours(24)), jdbc.queryForObject(
                "select expires_at from validation_survey where promise_id = ?", Timestamp.class, promise).toInstant());
    }

    // R-37: 안내 후 24시간 직전까지 응답 저장, 정확히 24시간부터 Expired
    @Test
    void r37_surveyResponseWindowEndsExactlyTwentyFourHoursAfterGuidance() throws Exception {
        Instant t = kst("2026-11-16T11:00:00");

        Participant before = participant();
        UUID first = expiredUnansweredPromise(before, t);
        Instant availableAt = jdbc.queryForObject("select available_at from validation_survey where promise_id = ?",
                Timestamp.class, first).toInstant();
        Instant expiresAt = jdbc.queryForObject("select expires_at from validation_survey where promise_id = ?",
                Timestamp.class, first).toInstant();
        assertEquals(Duration.ofHours(24), Duration.between(availableAt, expiresAt));

        clock.set(expiresAt.minusMillis(1));
        submitSurvey(before, first).andExpect(status().isOk())
                .andExpect(jsonPath("$.lifecycleStatus").value("Submitted"));
        assertEquals(expiresAt.minusMillis(1), jdbc.queryForObject(
                "select submitted_at from validation_survey where promise_id = ?", Timestamp.class, first).toInstant());

        clock.set(t);
        Participant atDeadline = participant();
        UUID second = expiredUnansweredPromise(atDeadline, t);
        clock.set(expiresAt);
        submitSurvey(atDeadline, second).andExpect(status().isConflict());
        mockMvc.perform(get("/api/promises/" + second + "/validation-survey").header("Authorization", atDeadline.auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lifecycleStatus").value("Expired"));
        assertEquals(null, jdbc.queryForObject(
                "select submitted_at from validation_survey where promise_id = ?", Timestamp.class, second));
    }

    // R-45: 원래 약속·인증 시도·재생성 관계가 끊기거나 중복되지 않는다
    @Test
    void r45_recreationKeepsOriginalAttemptsAndLinksWithoutDuplicates() throws Exception {
        Participant p = participant();
        Instant t = BASE.plus(Duration.ofHours(1));
        UUID original = promise(p, t, "Scheduled");
        UUID attempt = attempt(original, t.plus(Duration.ofMinutes(3)), "Rejected");
        clock.set(t.plus(Duration.ofMinutes(31)));
        lifecycle.reconcile(p.id());
        assertEquals("AuthIncomplete", jdbc.queryForObject(
                "select final_outcome from promise where id = ?", String.class, original));

        Instant again = t.plus(Duration.ofHours(2));
        String key = "acceptance-recreate-" + UUID.randomUUID();
        String firstId = promiseIdOf(create(p, again, key).andExpect(status().isOk()));
        String replayId = promiseIdOf(create(p, again, key).andExpect(status().isOk()));
        assertEquals(firstId, replayId);
        UUID recreated = UUID.fromString(firstId);

        assertEquals("RECREATED", jdbc.queryForObject("select creation_type from promise where id = ?", String.class, recreated));
        assertEquals(original, jdbc.queryForObject("select source_promise_id from promise where id = ?", UUID.class, recreated));
        assertEquals(recreated, jdbc.queryForObject("select superseded_by_promise_id from promise where id = ?", UUID.class, original));
        assertEquals(original, jdbc.queryForObject("select promise_id from auth_attempt where id = ?", UUID.class, attempt));
        assertEquals(2, count("select count(*) from promise where participant_id = ?", p.id()));
        assertEquals(1, count("select count(*) from promise where participant_id = ? and superseded_by_promise_id is null", p.id()));
    }

    // R-46: 핵심 흐름을 한 번 실행하면 행동 로그(behavior_event)가 남는다.
    // 현재 behavior_event를 기록하는 코드가 없다. 이벤트 종류·필드 명세(D05/D08)가 확정된 뒤 구현하고 활성화한다.
    @org.junit.jupiter.api.Disabled("R-46 미구현: 행동 로그 명세(D05/D08) 확정 대기")
    @Test
    void r46_coreFlowWritesBehaviorEvents() throws Exception {
        Participant p = participant();
        Instant t = BASE.plus(Duration.ofHours(1));
        String promiseId = promiseIdOf(create(p, t).andExpect(status().isOk()));
        // 사진 API는 실제 시각을 쓰므로 생성된 약속의 시작을 현재 직전으로 옮긴다.
        Instant realT = Instant.now().minusSeconds(60);
        jdbc.update("update promise set scheduled_at = ?, scheduled_date_kst = ?, auth_window_ends_at = ? where id = ?",
                ts(realT), realT.atZone(KST).toLocalDate(), ts(realT.plus(Duration.ofMinutes(30))), UUID.fromString(promiseId));
        String attemptId = attemptIdOf(submitPhoto(p, UUID.fromString(promiseId)).andExpect(status().isOk()));
        mockMvc.perform(post("/api/promises/" + promiseId + "/photo-attempts/" + attemptId + "/result")
                        .header("Authorization", p.auth()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"result\":\"COMPLETED\",\"modelVersion\":\"acceptance-model\"}"))
                .andExpect(status().isOk());

        assertTrue(count("select count(*) from behavior_event where participant_id = ?", p.id()) > 0,
                "약속 생성·사진 제출·판정 흐름에서 behavior_event가 기록되어야 한다");
    }

    private void assertSurveyGuidance(Instant scheduledAt, Instant expectedGuidance) {
        clock.set(scheduledAt.minus(Duration.ofHours(1)));
        Participant p = participant();
        subscribe(p);
        UUID promise = expiredUnansweredPromise(p, scheduledAt);
        assertEquals(expectedGuidance, jdbc.queryForObject(
                "select available_at from validation_survey where promise_id = ?", Timestamp.class, promise).toInstant());

        notifications.dispatchDueNotifications(expectedGuidance.minusSeconds(1));
        assertEquals(0, deliveries(promise, "SURVEY_REMINDER", "IN_APP"));
        notifications.dispatchDueNotifications(expectedGuidance);
        notifications.dispatchDueNotifications(expectedGuidance.plusSeconds(60));
        assertEquals(1, deliveries(promise, "SURVEY_REMINDER", "IN_APP"));
        assertEquals(1, deliveries(promise, "SURVEY_REMINDER", "PUSH"));
    }

    /** 사진 없이 인증 창이 끝난 약속을 서버 reconcile로 AuthIncomplete 처리해 설문을 만든다. */
    private UUID expiredUnansweredPromise(Participant p, Instant scheduledAt) {
        UUID promise = promise(p, scheduledAt, "Scheduled");
        clock.set(scheduledAt.plus(Duration.ofMinutes(30)));
        lifecycle.reconcile(p.id());
        assertEquals(1, count("select count(*) from validation_survey where promise_id = ?", promise));
        return promise;
    }

    private org.springframework.test.web.servlet.ResultActions create(Participant p, Instant scheduledAt) throws Exception {
        return create(p, scheduledAt, "acceptance-" + UUID.randomUUID());
    }

    private org.springframework.test.web.servlet.ResultActions create(Participant p, Instant scheduledAt, String key) throws Exception {
        String body = "{\"taskTitle\":\"acceptance task\",\"toolType\":\"BOOK\",\"scheduledAt\":\""
                + scheduledAt.atZone(KST).toOffsetDateTime() + "\"}";
        return mockMvc.perform(post("/api/promises").header("Authorization", p.auth())
                .header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private org.springframework.test.web.servlet.ResultActions submitPhoto(Participant p, UUID promise) throws Exception {
        byte[] bytes = UUID.randomUUID().toString().getBytes();
        return mockMvc.perform(multipart("/api/promises/" + promise + "/photo-attempts")
                .file(new MockMultipartFile("photo", "sample.jpg", "image/jpeg", bytes))
                .param("clientRequestId", UUID.randomUUID().toString()).param("toolType", "laptop")
                .header("Authorization", p.auth()));
    }

    private org.springframework.test.web.servlet.ResultActions submitSurvey(Participant p, UUID promise) throws Exception {
        return mockMvc.perform(post("/api/promises/" + promise + "/validation-survey")
                .header("Authorization", p.auth()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"actualStarted\":true,\"startOrder\":\"after\",\"startDelayBucket\":\"within5\"}"));
    }

    private Participant participant() {
        String suffix = UUID.randomUUID().toString();
        Instant now = clock.instant();
        UUID studyId = UUID.randomUUID();
        UUID participantId = UUID.randomUUID();
        UUID deviceId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID policyId = UUID.randomUUID();
        String token = SessionToken.generate(new SecureRandom());
        jdbc.update("insert into study_period(id, code, intervention_ends_at) values (?, ?, ?)",
                studyId, "acceptance-" + suffix, ts(BASE.plus(Duration.ofDays(10))));
        jdbc.update("insert into policy_version(id, version, effective_from) values (?, ?, ?)",
                policyId, "acceptance-" + suffix, ts(BASE.minus(Duration.ofDays(1))));
        jdbc.update("insert into participant(id, study_period_id, public_code, lifecycle_status, enrolled_at) values (?, ?, ?, 'Active', ?)",
                participantId, studyId, "acceptance-" + suffix, ts(BASE.minus(Duration.ofDays(1))));
        jdbc.update("insert into participant_device(id, participant_id, device_key_hash, lifecycle_status, first_seen_at, last_seen_at) values (?, ?, ?, 'Active', ?, ?)",
                deviceId, participantId, "acceptance-device-" + suffix, ts(now), ts(now));
        jdbc.update("insert into participant_session(id, participant_id, participant_device_id, session_token_hash, lifecycle_status, issued_at, last_seen_at, expires_at) values (?, ?, ?, ?, 'Active', ?, ?, ?)",
                sessionId, participantId, deviceId, secretHasher.hash(token), ts(BASE.minus(Duration.ofDays(1))), ts(now),
                ts(BASE.plus(Duration.ofDays(30))));
        return new Participant(participantId, policyId, sessionId, "Bearer " + token);
    }

    private void subscribe(Participant p) {
        jdbc.update("insert into notification_subscription(participant_id, participant_session_id, subscription_key_hash, lifecycle_status, activated_at) values (?, ?, ?, 'Active', ?)",
                p.id(), p.sessionId(), "acceptance-sub-" + UUID.randomUUID(), ts(clock.instant()));
    }

    private UUID promise(Participant p, Instant scheduledAt, String status) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into promise(id, participant_id, policy_version_id, task_title, scheduled_at, scheduled_date_kst, tool_type, creation_type, lifecycle_status, auth_window_ends_at) values (?, ?, ?, 'acceptance task', ?, ?, 'laptop', 'NORMAL', ?, ?)",
                id, p.id(), p.policyId(), ts(scheduledAt), scheduledAt.atZone(KST).toLocalDate(), status,
                ts(scheduledAt.plus(Duration.ofMinutes(30))));
        return id;
    }

    private UUID attempt(UUID promise, Instant receivedAt, String status) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into auth_attempt(id, promise_id, client_request_id, request_fingerprint, tool_type, processing_status, request_received_at, valid_received_at) values (?, ?, ?, ?, 'laptop', ?, ?, ?)",
                id, promise, UUID.randomUUID(), "acceptance-" + id, status, ts(receivedAt), ts(receivedAt));
        return id;
    }

    private int deliveries(UUID promise, String type, String channel) {
        return count("select count(*) from notification_delivery where promise_id = ? and notification_type = '"
                + type + "' and channel = '" + channel + "' and lifecycle_status <> 'SUPPRESSED'", promise);
    }

    private int count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Integer.class, args);
    }

    private static String promiseIdOf(org.springframework.test.web.servlet.ResultActions result) throws Exception {
        return result.andReturn().getResponse().getContentAsString()
                .replaceFirst(".*\\\"promiseId\\\":\\\"([^\\\"]+)\\\".*", "$1");
    }

    private static String attemptIdOf(org.springframework.test.web.servlet.ResultActions result) throws Exception {
        return result.andReturn().getResponse().getContentAsString()
                .replaceFirst(".*\\\"attemptId\\\":\\\"([^\\\"]+)\\\".*", "$1");
    }

    private static Instant kst(String localDateTime) {
        return LocalDateTime.parse(localDateTime).atZone(KST).toInstant();
    }

    private static Timestamp ts(Instant instant) {
        return Timestamp.from(instant);
    }

    private record Participant(UUID id, UUID policyId, UUID sessionId, String auth) { }

    static final class MutableClock extends Clock {
        private final AtomicReference<Instant> now = new AtomicReference<>(BASE);

        void set(Instant instant) { now.set(instant); }

        @Override public ZoneId getZone() { return KST; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now.get(); }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestConfig {
        @Bean @Primary
        MutableClock acceptanceClock() { return new MutableClock(); }

        @Bean @ServiceConnection
        PostgreSQLContainer postgresContainer() { return new PostgreSQLContainer("postgres:16-alpine"); }
    }
}
