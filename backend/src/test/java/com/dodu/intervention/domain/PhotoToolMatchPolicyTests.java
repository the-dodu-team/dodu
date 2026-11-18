package com.dodu.intervention.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class PhotoToolMatchPolicyTests {
    @Test
    void labelMustMatchPromiseTool() {
        assertTrue(PhotoToolMatchPolicy.matches("BOOK", "book"));
        assertTrue(PhotoToolMatchPolicy.matches("COMPUTER", "laptop"));
        assertFalse(PhotoToolMatchPolicy.matches("BOOK", "laptop"));
        assertFalse(PhotoToolMatchPolicy.matches("COMPUTER", "book"));
        assertFalse(PhotoToolMatchPolicy.matches("BOOK", "other"));
        assertFalse(PhotoToolMatchPolicy.matches("BOOK", null));
    }

    @Test
    void legacyStoredToolValuesAreNormalized() {
        assertEquals(Optional.of("laptop"), PhotoToolMatchPolicy.expectedLabel("laptop"));
        assertEquals(Optional.of("laptop"), PhotoToolMatchPolicy.expectedLabel("computer"));
        assertEquals(Optional.of("book"), PhotoToolMatchPolicy.expectedLabel("book"));
        assertTrue(PhotoToolMatchPolicy.matches("laptop", "LAPTOP"));
        assertEquals(Optional.empty(), PhotoToolMatchPolicy.expectedLabel("tablet"));
        assertEquals(Optional.empty(), PhotoToolMatchPolicy.expectedLabel(null));
    }
}
