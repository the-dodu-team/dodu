package com.dodu.notification.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "dodu.push.fcm.enabled", havingValue = "true")
class FcmConfiguration {
    @Bean
    FirebaseMessaging firebaseMessaging(@Value("${dodu.push.fcm.credentials-path}") String path)
            throws IOException {
        try (InputStream in = new FileInputStream(path)) {
            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(in))
                    .build();
            return FirebaseMessaging.getInstance(FirebaseApp.initializeApp(options, "dodu"));
        }
    }
}