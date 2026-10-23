package com.dodu.intervention;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;

class PhotoSubmissionFingerprintTests {
    @Test
    void samePhotoAndMetadataProduceTheSameFingerprint() {
        assertEquals(
                PhotoSubmissionFingerprint.sha256("photo".getBytes(), "image/jpeg", "laptop"),
                PhotoSubmissionFingerprint.sha256("photo".getBytes(), "image/jpeg", "laptop"));
    }

    @Test
    void metadataChangesProduceADifferentFingerprint() {
        assertNotEquals(
                PhotoSubmissionFingerprint.sha256("photo".getBytes(), "image/jpeg", "laptop"),
                PhotoSubmissionFingerprint.sha256("photo".getBytes(), "image/png", "laptop"));
    }
}
