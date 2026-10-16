package com.dodu.participant.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

class SecretHasherTest {

    private static final byte[] PEPPER = pepper(1);
    private static final String CODE = "ABCDEFGHJKMN";

    @Test
    void matchesKnownHmacSha256Vector() {
        // 별도로 계산한 값: HMAC-SHA-256(키 = 0x01 32바이트, 메시지 = "ABCDEFGHJKMN")
        assertEquals("201ac11566b85ec134e436c2e9d358992dba2335f28ccd65c267ea19a15c4d82", new SecretHasher(PEPPER).hash(CODE));
    }

    @Test
    void producesSixtyFourLowercaseHexCharacters() {
        String hash = new SecretHasher(PEPPER).hash(CODE);
        assertEquals(64, hash.length());
        assertTrue(hash.matches("[0-9a-f]{64}"), hash);
    }

    @Test
    void sameInputGivesSameHash() {
        SecretHasher hasher = new SecretHasher(PEPPER);
        assertEquals(hasher.hash(CODE), hasher.hash(CODE));
    }

    @Test
    void differentCodesGiveDifferentHashes() {
        SecretHasher hasher = new SecretHasher(PEPPER);
        assertNotEquals(hasher.hash(CODE), hasher.hash("ABCDEFGHJKMP"));
    }

    @Test
    void differentPepperGivesDifferentHash() {
        assertNotEquals(
                new SecretHasher(pepper(1)).hash(CODE),
                new SecretHasher(pepper(2)).hash(CODE));
    }

    @Test
    void rejectsShortPepper() {
        assertThrows(IllegalArgumentException.class, () -> new SecretHasher(new byte[31]));
        assertThrows(NullPointerException.class, () -> new SecretHasher(null));
    }

    @Test
    void doesNotKeepReferenceToCallerPepper() {
        byte[] pepper = pepper(1);
        SecretHasher hasher = new SecretHasher(pepper);
        String before = hasher.hash(CODE);
        Arrays.fill(pepper, (byte) 9);
        assertEquals(before, hasher.hash(CODE));
    }

    @Test
    void hashesAnyStringValue() {
        SecretHasher hasher = new SecretHasher(PEPPER);
        assertEquals(64, hasher.hash("device-key-aaaaaaaaaaaaaaaa").length());
        assertEquals(64, hasher.hash(SessionToken.generate(new java.security.SecureRandom())).length());
    }

    @Test
    void rejectsNullValue() {
        SecretHasher hasher = new SecretHasher(PEPPER);
        assertThrows(NullPointerException.class, () -> hasher.hash(null));
    }

    private static byte[] pepper(int fill) {
        byte[] bytes = new byte[32];
        Arrays.fill(bytes, (byte) fill);
        return bytes;
    }
}