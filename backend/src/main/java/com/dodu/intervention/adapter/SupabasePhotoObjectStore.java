package com.dodu.intervention.adapter;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import com.dodu.intervention.PhotoObjectReader;
import com.dodu.intervention.PhotoObjectWriter;
import com.dodu.intervention.PhotoStorageRequestException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Supabase Storage REST adapter. */
@Component
@ConditionalOnProperty(name = "dodu.photo-storage.provider", havingValue = "supabase")
public final class SupabasePhotoObjectStore implements PhotoObjectReader, PhotoObjectWriter {
    private static final Duration TIMEOUT = Duration.ofSeconds(20);
    private final HttpClient client;
    private final String baseUrl;
    private final String bucket;
    private final String serviceRoleKey;

    @Autowired
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
        byte[] body = new tools.jackson.databind.json.JsonMapper().writeValueAsBytes(
                java.util.Map.of("prefixes", java.util.List.of(storageKey)));
        // Match the official SDK's remove API: exact object names in a JSON body.
        // An absent object returns an empty success list; bucket/auth failures must not be ignored.
        HttpResponse<byte[]> response = send("DELETE", null, "application/json", body);
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new PhotoStorageRequestException(response.statusCode());
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
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl + "/storage/v1/object/" + bucket + (storageKey == null ? "" : "/" + storageKey)))
                .timeout(TIMEOUT)
                .header("apikey", serviceRoleKey)
                .header("Accept", "application/json");
        // New secret credentials are not JWTs; the gateway accepts them on apikey only.
        if (!serviceRoleKey.startsWith("sb_secret_")) builder.header("Authorization", "Bearer " + serviceRoleKey);
        return builder;
    }

    private static String stripTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
