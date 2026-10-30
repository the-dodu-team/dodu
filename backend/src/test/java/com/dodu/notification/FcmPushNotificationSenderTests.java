package com.dodu.notification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class FcmPushNotificationSenderTests {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final FirebaseMessaging messaging = mock(FirebaseMessaging.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-30T00:00:00Z"), ZoneOffset.UTC);
    private final FcmPushNotificationSender sender =
            new FcmPushNotificationSender(jdbc, clock, messaging, false);
    private final UUID participantId = UUID.randomUUID();
    private final UUID promiseId = UUID.randomUUID();

    @Test
    void unregisteredTokenIsUndeliverableAndDeactivatesSubscription() throws Exception {
        givenActiveSubscription();
        givenSendFails(MessagingErrorCode.UNREGISTERED);

        var result = sender.send(participantId, promiseId, NotificationSchedulePolicy.NotificationType.START);

        assertEquals(PushNotificationSender.Status.UNDELIVERABLE, result.status());
        assertEquals("UNREGISTERED", result.failureCode());
        verify(jdbc).update(contains("push_token = null"), any(Timestamp.class), any(UUID.class));
    }

    @Test
    void invalidArgumentIsUndeliverableWithoutDeactivating() throws Exception {
        givenActiveSubscription();
        givenSendFails(MessagingErrorCode.INVALID_ARGUMENT);

        var result = sender.send(participantId, promiseId, NotificationSchedulePolicy.NotificationType.START);

        assertEquals(PushNotificationSender.Status.UNDELIVERABLE, result.status());
        assertEquals("INVALID_ARGUMENT", result.failureCode());
        verify(jdbc, never()).update(anyString(), any(Timestamp.class), any(UUID.class));
    }

    @Test
    void temporaryErrorIsFailedSoRetryPolicyApplies() throws Exception {
        givenActiveSubscription();
        givenSendFails(MessagingErrorCode.UNAVAILABLE);

        var result = sender.send(participantId, promiseId, NotificationSchedulePolicy.NotificationType.REMINDER_10);

        assertEquals(PushNotificationSender.Status.FAILED, result.status());
        assertEquals("UNAVAILABLE", result.failureCode());
    }

    @Test
    void missingSubscriptionIsUndeliverable() {
        when(jdbc.query(anyString(), any(RowMapper.class), eq(participantId))).thenReturn(List.of());

        var result = sender.send(participantId, promiseId, NotificationSchedulePolicy.NotificationType.START);

        assertEquals(PushNotificationSender.Status.UNDELIVERABLE, result.status());
        assertEquals("NO_ACTIVE_SUBSCRIPTION", result.failureCode());
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void givenActiveSubscription() {
        when(jdbc.query(anyString(), any(RowMapper.class), eq(participantId))).thenAnswer(invocation -> {
            RowMapper mapper = invocation.getArgument(1);
            ResultSet rs = mock(ResultSet.class);
            when(rs.getObject("id", UUID.class)).thenReturn(UUID.randomUUID());
            when(rs.getString("push_token")).thenReturn("fake-token");
            return List.of(mapper.mapRow(rs, 0));
        });
    }

    private void givenSendFails(MessagingErrorCode code) throws Exception {
        FirebaseMessagingException failure = mock(FirebaseMessagingException.class);
        when(failure.getMessagingErrorCode()).thenReturn(code);
        when(messaging.send(any(Message.class), eq(false))).thenThrow(failure);
    }
}