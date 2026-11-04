package com.dodu.notification;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.SyncTaskExecutor;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class DeviceChangeNotificationListenerTests {
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
