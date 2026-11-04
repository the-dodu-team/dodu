package com.dodu.notification;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.MessagingErrorCode;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.slf4j.LoggerFactory;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.SyncTaskExecutor;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class DeviceChangeNotificationListenerTests {
    @Test
    void startupReportsOnlyEnabledAndDryRunFlags() {
        Logger logger = (Logger) LoggerFactory.getLogger(com.dodu.notification.config.FcmStartupDiagnostics.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>(); appender.start(); logger.addAppender(appender);
        try {
            new com.dodu.notification.config.FcmStartupDiagnostics(false, true).report();
            assertEquals("DODU_FCM_CONFIG enabled=false dryRun=true", logs(appender));
        } finally { logger.detachAppender(appender); appender.stop(); }
    }

    @Test
    void distinguishesAcceptanceDryRunMissingSubscriptionAndFcmErrorWithoutSecrets() throws Exception {
        Logger logger = (Logger) LoggerFactory.getLogger(DeviceChangeNotificationListener.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start(); logger.addAppender(appender);
        try {
            FirebaseMessaging messaging = mock(FirebaseMessaging.class);
            when(messaging.send(any(Message.class), anyBoolean())).thenReturn("sensitive-provider-message-id");
            var notice = new DeviceChangeNotice(List.of("sensitive-browser-token"));
            var live = new DeviceChangeNotificationListener(messaging, new SyncTaskExecutor(), false);
            live.afterCommit(notice);
            String accepted = logs(appender);
            assertTrue(accepted.contains("stage=FCM_ACCEPTED"));
            assertTrue(accepted.contains("trace=" + notice.traceId()));
            appender.list.clear();
            new DeviceChangeNotificationListener(messaging, new SyncTaskExecutor(), true).afterCommit(notice);
            assertTrue(logs(appender).contains("stage=VALIDATED"));
            assertFalse(logs(appender).contains("stage=FCM_ACCEPTED"));
            appender.list.clear();
            live.afterCommit(new DeviceChangeNotice(List.of()));
            assertTrue(logs(appender).contains("code=NO_ACTIVE_SUBSCRIPTION"));
            assertFalse(logs(appender).contains("stage=ATTEMPT"));
            appender.list.clear();
            FirebaseMessagingException failure = mock(FirebaseMessagingException.class);
            when(failure.getMessagingErrorCode()).thenReturn(MessagingErrorCode.SENDER_ID_MISMATCH);
            when(failure.getMessage()).thenReturn("sensitive-provider-body");
            when(messaging.send(any(Message.class), anyBoolean())).thenThrow(failure);
            live.afterCommit(notice);
            String rejected = logs(appender);
            assertTrue(rejected.contains("stage=FCM_REJECTED"));
            assertTrue(rejected.contains("code=SENDER_ID_MISMATCH"));
            assertFalse((accepted + rejected).contains("sensitive-"));
            assertTrue(appender.list.stream().allMatch(event -> event.getThrowableProxy() == null));
        } finally { logger.detachAppender(appender); appender.stop(); }
    }

    private static String logs(ListAppender<ILoggingEvent> appender) {
        return appender.list.stream().map(ILoggingEvent::getFormattedMessage).collect(java.util.stream.Collectors.joining("\n"));
    }
    @Test
    void committedDeviceChangeNotifiesOldDeviceWithoutAskingItToApprove() throws Exception {
        FirebaseMessaging messaging = mock(FirebaseMessaging.class);
        DeviceChangeNotice notice = new DeviceChangeNotice(List.of("synthetic-old-device-token"));
        new DeviceChangeNotificationListener(messaging, new SyncTaskExecutor(), true).afterCommit(notice);
        verify(messaging).send(any(Message.class), eq(true));
        assertFalse(notice.toString().contains("synthetic-old-device-token"));
    }
    @Test
    void deliveryAndExecutorFailureNeverFailAlreadyCommittedLogin() throws Exception {
        FirebaseMessaging messaging = mock(FirebaseMessaging.class);
        when(messaging.send(any(Message.class), anyBoolean())).thenThrow(new RuntimeException("synthetic-provider-error"));
        var notice = new DeviceChangeNotice(List.of("synthetic-token"));
        assertDoesNotThrow(() -> new DeviceChangeNotificationListener(messaging, new SyncTaskExecutor(), false).afterCommit(notice));
        assertDoesNotThrow(() -> new DeviceChangeNotificationListener(messaging, command -> { throw new RuntimeException(); }, false).afterCommit(notice));
    }
}
