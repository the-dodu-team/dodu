package com.dodu.intervention;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SupabasePhotoObjectStoreTests {
    private HttpServer server;
    private SupabasePhotoObjectStore store;

    @BeforeEach
    void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.start();
        store = new SupabasePhotoObjectStore(
                "http://127.0.0.1:" + server.getAddress().getPort(),
                "dodu-photos", "service-secret", HttpClient.newHttpClient());
    }

    @AfterEach
    void tearDown() { server.stop(0); }

    @Test
    void uploadsReadsAndDeletesThroughPrivateStorageHeaders() {
        AtomicReference<String> methods = new AtomicReference<>("");
        AtomicReference<byte[]> uploaded = new AtomicReference<>();
        server.createContext("/storage/v1/object/dodu-photos/object-1", exchange -> {
            methods.updateAndGet(value -> value + exchange.getRequestMethod());
            if ("POST".equals(exchange.getRequestMethod())) {
                assertEquals("Bearer service-secret", exchange.getRequestHeaders().getFirst("Authorization"));
                uploaded.set(exchange.getRequestBody().readAllBytes());
                respond(exchange, 200, "{}");
            } else if ("GET".equals(exchange.getRequestMethod())) {
                respond(exchange, 200, "stored-photo".getBytes(StandardCharsets.UTF_8));
            } else {
                respond(exchange, 200, "{}");
            }
        });

        assertEquals("object-1", store.write("object-1", "image/jpeg", new byte[] {1, 2, 3}));
        assertArrayEquals(new byte[] {1, 2, 3}, uploaded.get());
        assertArrayEquals("stored-photo".getBytes(StandardCharsets.UTF_8),
                store.read("object-1", "image/jpeg").bytes());
        store.delete("object-1");
        assertEquals("POSTGETDELETE", methods.get());
    }

    @Test
    void retentionDeadlineIsThirtyDaysAfterInterventionEnd() {
        var endsAt = java.time.Instant.parse("2026-11-01T00:00:00Z");
        assertEquals(endsAt.plus(java.time.Duration.ofDays(30)), PhotoRetentionWorker.deleteDueAt(endsAt));
    }

    private static void respond(com.sun.net.httpserver.HttpExchange exchange, int status, String body)
            throws java.io.IOException { respond(exchange, status, body.getBytes(StandardCharsets.UTF_8)); }

    private static void respond(com.sun.net.httpserver.HttpExchange exchange, int status, byte[] body)
            throws java.io.IOException {
        exchange.sendResponseHeaders(status, body.length);
        try (var output = exchange.getResponseBody()) { output.write(body); }
    }
}
