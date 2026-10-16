package com.dodu.access;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AccessCredentialHasherTests {
    private final AccessCredentialHasher hasher = new AccessCredentialHasher("test-only-pepper");

    @Test
    void hashesAndMatchesWithoutStoringRawSecret() {
        String hash = hasher.hash("PT-0231");
        assertNotEquals("PT-0231", hash);
        assertTrue(hasher.matches("PT-0231", hash));
        assertFalse(hasher.matches("PT-0232", hash));
    }

    @Test
    void rejectsBlankPepperAndSecret() {
        assertThrows(IllegalArgumentException.class, () -> new AccessCredentialHasher(" "));
        assertThrows(IllegalArgumentException.class, () -> hasher.hash(""));
        assertFalse(hasher.matches(null, "hash"));
    }
}
