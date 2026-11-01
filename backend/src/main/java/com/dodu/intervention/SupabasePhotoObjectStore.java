package com.dodu.intervention;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Supabase Storage adapter using its S3-compatible object HTTP API. */
@Component
@ConditionalOnProperty(name = "dodu.photo-storage.provider", havingValue = "supabase")
public final class SupabasePhotoObjectStore implements PhotoObjectReader, PhotoObjectWriter {
    private static final Duration TIMEOUT = Duration.ofSeconds(20);
    private final HttpClient client;
    private final String baseUrl;
    private final String bucket;
    private final String serviceRoleKey;

    public SupabasePhotoObjectStore(
            org.springframework.core.env.Environment environment) {
        this(
                environment.getRequiredProperty("dodu.photo-storage.supabase-url"),
                environment.getRequiredProperty("dodu.photo-storage.bucket"),
                environment.getRequiredProperty("dodu.photo-storage.service-role-key"),
                HttpClient.newBuilder().connectTimeout(TIMEOUT).build());
    }

    SupabasePhotoObjectStore(String supabaseUrl, String bucket, String serviceRoleKey, HttpClient client) {
        if (supabaseUrl == null || supabaseUrl.isBlank()
                || bucket == null || bucket.isBlank() || serviceRoleKey == null || serviceRoleKey.isBlank()) {
            throw new IllegalArgumentException("Supabase photo storage configuration is incomplete");
        }
        this.baseUrl = stripTrailingSlash(supabaseUrl);
        this.bucket = bucket;
        this.serviceRoleKey = serviceRoleKey;
        this.client = client;
    }

    @Override
    public String write(String storageKey, String contentType, byte[] bytes) {
        request("POST", storageKey, contentType, bytes, true);
        return storageKey;
    }

    @Override
    public PhotoObjectReader.PhotoObject read(String storageKey, String contentType) {
        HttpResponse<byte[]> response = send("GET", storageKey, contentType, null);
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("Photo storage read failed with HTTP " + response.statusCode());
        }
        return new PhotoObjectReader.PhotoObject(contentType, response.body());
    }

    @Override
    public void delete(String storageKey) {
        HttpResponse<byte[]> response = send("DELETE", storageKey, "application/json", null);
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("Photo storage delete failed with HTTP " + response.statusCode());
        }
    }

    private void request(String method, String storageKey, String contentType, byte[] bytes, boolean upsert) {
        HttpRequest.Builder builder = requestBuilder(method, storageKey)
                .header("Content-Type", contentType)
                .header("x-upsert", Boolean.toString(upsert));
        builder.method(method, bytes == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofByteArray(bytes));
        try {
            HttpResponse<byte[]> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("Photo storage write failed with HTTP " + response.statusCode());
            }
        } catch (IOException e) {
            throw new IllegalStateException("Photo storage request failed", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Photo storage request interrupted", e);
        }
    }

    private HttpResponse<byte[]> send(String method, String storageKey, String contentType, byte[] bytes) {
        HttpRequest.Builder builder = requestBuilder(method, storageKey).header("Content-Type", contentType);
        builder.method(method, bytes == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofByteArray(bytes));
        try {
            return client.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
        } catch (IOException e) {
            throw new IllegalStateException("Photo storage request failed", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Photo storage request interrupted", e);
        }
    }

    private HttpRequest.Builder requestBuilder(String method, String storageKey) {
        return HttpRequest.newBuilder(URI.create(baseUrl + "/storage/v1/object/" + bucket + "/" + storageKey))
                .timeout(TIMEOUT)
                .header("Authorization", "Bearer " + serviceRoleKey)
                .header("apikey", serviceRoleKey)
                .header("Accept", "application/json");
    }

    private static String stripTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
