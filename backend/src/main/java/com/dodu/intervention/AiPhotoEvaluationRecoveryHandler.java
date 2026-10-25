package com.dodu.intervention;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Calls the configured AI endpoint and normalizes laptop/book/other results. */
@Component
@ConditionalOnProperty(name = "dodu.ai.enabled", havingValue = "true")
public final class AiPhotoEvaluationRecoveryHandler implements PhotoEvaluationRecoveryHandler {
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(20);
    private final HttpClient client;
    private final AiPhotoEvaluationResponseMapper responseMapper;
    private final PhotoObjectReader photoObjectReader;
    private final URI endpoint;
    private final String apiKey;

    public AiPhotoEvaluationRecoveryHandler(ObjectMapper objectMapper,
                                            ObjectProvider<PhotoObjectReader> photoObjectReaders) {
        this.client = HttpClient.newBuilder().connectTimeout(REQUEST_TIMEOUT).build();
        this.responseMapper = new AiPhotoEvaluationResponseMapper(objectMapper);
        this.photoObjectReader = photoObjectReaders.getIfAvailable();
        String configuredEndpoint = System.getenv().getOrDefault("DODU_AI_ENDPOINT", "");
        this.endpoint = configuredEndpoint.isBlank() ? null : URI.create(configuredEndpoint);
        this.apiKey = System.getenv().getOrDefault("DODU_AI_API_KEY", "");
    }

    @Override
    public RecoveryResult recover(RecoveryJob job) {
        if (endpoint == null || apiKey.isBlank()) {
            return RecoveryResult.retryable("AI_WORKER_UNCONFIGURED");
        }
        if (photoObjectReader == null || job.storageKey() == null || job.contentType() == null) {
            return RecoveryResult.retryable("PHOTO_STORAGE_UNCONFIGURED");
        }
        PhotoObjectReader.PhotoObject photo;
        try {
            photo = photoObjectReader.read(job.storageKey(), job.contentType());
        } catch (RuntimeException ignored) {
            return RecoveryResult.retryable("PHOTO_STORAGE_UNAVAILABLE");
        }

        String boundary = "DoduBoundary" + UUID.randomUUID();
        HttpRequest request = HttpRequest.newBuilder(endpoint)
                .timeout(REQUEST_TIMEOUT)
                .header("x-api-key", apiKey)
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(multipartBody(boundary, photo))
                .build();
        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            return responseMapper.map(response.statusCode(), response.body());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return RecoveryResult.retryable("AI_REQUEST_INTERRUPTED");
        } catch (IOException | RuntimeException ignored) {
            return RecoveryResult.retryable("AI_REQUEST_FAILED");
        }
    }


    private static HttpRequest.BodyPublisher multipartBody(String boundary, PhotoObjectReader.PhotoObject photo) {
        String prefix = "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"files\"; filename=\"photo\"\r\n"
                + "Content-Type: " + photo.contentType() + "\r\n\r\n";
        String suffix = "\r\n--" + boundary + "--\r\n";
        return HttpRequest.BodyPublishers.concat(
                HttpRequest.BodyPublishers.ofString(prefix),
                HttpRequest.BodyPublishers.ofByteArray(photo.bytes()),
                HttpRequest.BodyPublishers.ofString(suffix));
    }
}
