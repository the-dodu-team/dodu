package com.dodu.participant.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.security.SecureRandom;
import org.junit.jupiter.api.Test;

class SessionTokenTest {

    private static final SecureRandom RANDOM = new SecureRandom();

    @Test
    void tokenIsFortyThreeUrlSafeCharacters() {
        String token = SessionToken.generate(RANDOM);
        assertEquals(43, token.length());
        assertTrue(token.matches("[A-Za-z0-9_-]{43}"), token);
    }

    @Test
    void tokensDiffer() {
        assertNotEquals(SessionToken.generate(RANDOM), SessionToken.generate(RANDOM));
    }
}