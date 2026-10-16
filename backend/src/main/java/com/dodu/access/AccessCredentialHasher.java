package com.dodu.access;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Creates a one-way, peppered digest; raw participant codes never reach storage. */
public final class AccessCredentialHasher {
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private final byte[] pepper;

    public AccessCredentialHasher(String pepper) {
        if (pepper == null || pepper.isBlank()) {
            throw new IllegalArgumentException("credential pepper must not be blank");
        }
        this.pepper = pepper.getBytes(StandardCharsets.UTF_8).clone();
    }

    public String hash(String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalArgumentException("secret must not be blank");
        }
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(pepper, HMAC_ALGORITHM));
            return HexFormat.of().formatHex(mac.doFinal(secret.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("credential hashing is unavailable", exception);
        }
    }

    public boolean matches(String secret, String expectedHash) {
        if (secret == null || expectedHash == null || expectedHash.isBlank()) {
            return false;
        }
        return MessageDigest.isEqual(hash(secret).getBytes(StandardCharsets.US_ASCII),
                expectedHash.getBytes(StandardCharsets.US_ASCII));
    }
}
