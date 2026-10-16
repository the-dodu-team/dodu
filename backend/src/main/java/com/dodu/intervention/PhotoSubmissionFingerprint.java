package com.dodu.intervention;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/** Creates a stable request fingerprint without logging or retaining photo bytes. */
public final class PhotoSubmissionFingerprint {
    private PhotoSubmissionFingerprint() {
    }

    public static String sha256(byte[] content, String contentType, String toolType) {
        Objects.requireNonNull(content, "content must not be null");
        Objects.requireNonNull(contentType, "contentType must not be null");
        Objects.requireNonNull(toolType, "toolType must not be null");
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            digest.update(content);
            digest.update((byte) 0);
            digest.update(contentType.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            digest.update((byte) 0);
            digest.update(toolType.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required", e);
        }
    }
}
