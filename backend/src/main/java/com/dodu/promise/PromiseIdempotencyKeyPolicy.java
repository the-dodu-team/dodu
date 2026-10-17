package com.dodu.promise;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class PromiseIdempotencyKeyPolicy {
    private PromiseIdempotencyKeyPolicy() { }

    public static String validate(String key) {
        if (key == null || key.isBlank() || key.length() > 128 || key.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("INVALID_IDEMPOTENCY_KEY");
        }
        return key;
    }

    public static String fingerprint(PromiseController.PromiseRequest request) {
        String canonical = String.join("\u001f",
                request.taskTitle() == null ? "" : request.taskTitle().trim(),
                request.toolType() == null ? "" : request.toolType().name(),
                request.scheduledAt() == null ? "" : request.scheduledAt().trim());
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }
}
