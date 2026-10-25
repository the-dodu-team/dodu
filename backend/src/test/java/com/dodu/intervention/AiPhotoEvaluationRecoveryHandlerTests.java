package com.dodu.intervention;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AiPhotoEvaluationRecoveryHandlerTests {
    private HttpServer server;

    @BeforeEach
    void startServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void sendsOnePhotoWithConfiguredHeaderAndMapsLaptopToCompleted() {
        AtomicReference<String> requestBody = new AtomicReference<>();
        AtomicReference<String> receivedKey = new AtomicReference<>();
        server.createContext("/predict/batch", exchange -> {
            receivedKey.set(exchange.getRequestHeaders().getFirst("x-api-key"));
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            respond(exchange, 200, "{\"candidates\":[{\"label\":\"laptop\"}]}");
        });

        var handler = new AiPhotoEvaluationRecoveryHandler(new ObjectMapper(),
                (key, contentType) -> new PhotoObjectReader.PhotoObject(contentType, new byte[] {1, 2, 3}),
                endpoint(), "test-secret", HttpClient.newHttpClient());
        var result = handler.recover(job());

        assertEquals(PhotoEvaluationRecoveryHandler.Outcome.COMPLETED, result.outcome());
        assertEquals("test-secret", receivedKey.get());
        assertTrue(requestBody.get().contains("name=\"files\""));
        assertTrue(requestBody.get().contains("filename=\"photo\""));
    }

    @Test
    void mapsOtherToRejectedWithoutExposingPhotoDataInTheResult() {
        server.createContext("/predict/batch", exchange ->
                respond(exchange, 200, "{\"label\":\"other\"}"));

        var handler = new AiPhotoEvaluationRecoveryHandler(new ObjectMapper(),
                (key, contentType) -> new PhotoObjectReader.PhotoObject(contentType, new byte[] {9, 8, 7}),
                endpoint(), "test-secret", HttpClient.newHttpClient());
        var result = handler.recover(job());

        assertEquals(PhotoEvaluationRecoveryHandler.Outcome.REJECTED, result.outcome());
        assertEquals("AI_OTHER", result.errorCode());
        assertTrue(result.toString().indexOf("9") < 0);
    }

    private URI endpoint() {
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/predict/batch");
    }

    private static PhotoEvaluationRecoveryHandler.RecoveryJob job() {
        return new PhotoEvaluationRecoveryHandler.RecoveryJob(UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), 1, "private/object-key", "image/jpeg");
    }

    private static void respond(HttpExchange exchange, int status, String body) throws java.io.IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        try (var output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }
}
