package com.dodu.intervention.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.Map;

import com.dodu.intervention.PhotoObjectReader;
import com.dodu.intervention.PhotoObjectWriter;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;

class SupabasePhotoObjectStoreContextTests {
    @Test
    void supabaseProviderStartsWithEnvironmentInjectedConstructor() {
        try (var context = context("supabase")) {
            context.refresh();
            assertSame(context.getBean(SupabasePhotoObjectStore.class),
                    context.getBean(PhotoObjectReader.class));
            assertSame(context.getBean(SupabasePhotoObjectStore.class),
                    context.getBean(PhotoObjectWriter.class));
        }
    }

    @Test
    void disabledProviderDoesNotRequireCredentials() {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource(
                    "test", Map.of("dodu.photo-storage.provider", "none")));
            context.register(SupabasePhotoObjectStore.class);
            context.refresh();
            assertEquals(0, context.getBeansOfType(PhotoObjectWriter.class).size());
        }
    }

    private AnnotationConfigApplicationContext context(String provider) {
        var context = new AnnotationConfigApplicationContext();
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test", Map.of(
                "dodu.photo-storage.provider", provider,
                "dodu.photo-storage.supabase-url", "https://example.invalid",
                "dodu.photo-storage.bucket", "dodu-photos",
                "dodu.photo-storage.service-role-key", "synthetic-test-credential")));
        context.register(SupabasePhotoObjectStore.class);
        return context;
    }
}
