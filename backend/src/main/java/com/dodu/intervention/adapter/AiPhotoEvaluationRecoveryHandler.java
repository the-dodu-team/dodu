package com.dodu.intervention.adapter;

import com.dodu.intervention.PhotoEvaluationRecoveryHandler;
import com.dodu.intervention.PhotoObjectReader;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
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

    @Autowired
    // Previously required a Jackson 2 bean directly. Boot's web JSON mapper may be Jackson 3.
    public AiPhotoEvaluationRecoveryHandler(ObjectProvider<ObjectMapper> objectMappers,
                                            ObjectProvider<PhotoObjectReader> photoObjectReaders) {
        String configuredEndpoint = System.getenv().getOrDefault("DODU_AI_ENDPOINT", "");
        this.client = HttpClient.newBuilder().connectTimeout(REQUEST_TIMEOUT).build();
        // Keep the existing AI response parser isolated from the web mapper configuration.
        this.responseMapper = new AiPhotoEvaluationResponseMapper(objectMappers.getIfAvailable(ObjectMapper::new));
        this.photoObjectReader = photoObjectReaders.getIfAvailable();
        this.endpoint = configuredEndpoint.isBlank() ? null : URI.create(configuredEndpoint);
        this.apiKey = System.getenv().getOrDefault("DODU_AI_API_KEY", "");
    }

    AiPhotoEvaluationRecoveryHandler(ObjectMapper objectMapper, PhotoObjectReader photoObjectReader,
                                     URI endpoint, String apiKey, HttpClient client) {
        this.client = client;
        this.responseMapper = new AiPhotoEvaluationResponseMapper(objectMapper);
        this.photoObjectReader = photoObjectReader;
        this.endpoint = endpoint;
        this.apiKey = apiKey;
    }

    @Override
    public RecoveryResult recover(RecoveryJob job) {
        return recover(job, null);
    }

    /**
     * {@code preloaded}가 있으면 저장소에서 다시 내려받지 않는다(사진 제출 직후 경로: 같은 요청에서 방금 올린 사진).
     * 없으면 이전처럼 저장소에서 읽는다(30초 워커·재시도·서버 재시작 후).
     */
    @Override
    public RecoveryResult recover(RecoveryJob job, PhotoObjectReader.PhotoObject preloaded) {
        if (endpoint == null || apiKey.isBlank()) {
            return RecoveryResult.retryable("AI_WORKER_UNCONFIGURED");
        }
        PhotoObjectReader.PhotoObject photo = preloaded;
        if (photo == null) {
            if (photoObjectReader == null || job.storageKey() == null || job.contentType() == null) {
                return RecoveryResult.retryable("PHOTO_STORAGE_UNCONFIGURED");
            }
            try {
                photo = photoObjectReader.read(job.storageKey(), job.contentType());
            } catch (RuntimeException ignored) {
                return RecoveryResult.retryable("PHOTO_STORAGE_UNAVAILABLE");
            }
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
