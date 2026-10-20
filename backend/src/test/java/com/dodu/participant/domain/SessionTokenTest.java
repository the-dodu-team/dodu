package com.dodu.participant.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.security.SecureRandom;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SessionTokenTest {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String TOKEN = "A".repeat(43);

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

    @Test
    void generatedTokenIsWellFormed() {
        assertTrue(SessionToken.isWellFormed(SessionToken.generate(RANDOM)));
    }

    @Test
    void wellFormedRequiresExactLengthAndUrlSafeCharacters() {
        assertFalse(SessionToken.isWellFormed(null));
        assertFalse(SessionToken.isWellFormed(""));
        assertFalse(SessionToken.isWellFormed("A".repeat(42)));
        assertFalse(SessionToken.isWellFormed("A".repeat(44)));
        assertFalse(SessionToken.isWellFormed("A".repeat(42) + "+"));
        assertFalse(SessionToken.isWellFormed("A".repeat(42) + "="));
        assertFalse(SessionToken.isWellFormed("A".repeat(42) + " "));
    }

    @Test
    void bearerHeaderYieldsToken() {
        assertEquals(Optional.of(TOKEN), SessionToken.fromAuthorizationHeader("Bearer " + TOKEN));
    }

    @Test
    void bearerSchemeIsCaseInsensitive() {
        assertEquals(Optional.of(TOKEN), SessionToken.fromAuthorizationHeader("bearer " + TOKEN));
        assertEquals(Optional.of(TOKEN), SessionToken.fromAuthorizationHeader("BEARER " + TOKEN));
    }

    @Test
    void malformedAuthorizationHeadersYieldNothing() {
        assertEquals(Optional.empty(), SessionToken.fromAuthorizationHeader(null));
        assertEquals(Optional.empty(), SessionToken.fromAuthorizationHeader(""));
        assertEquals(Optional.empty(), SessionToken.fromAuthorizationHeader("Bearer "));
        assertEquals(Optional.empty(), SessionToken.fromAuthorizationHeader(TOKEN));
        assertEquals(Optional.empty(), SessionToken.fromAuthorizationHeader("Basic " + TOKEN));
        assertEquals(Optional.empty(), SessionToken.fromAuthorizationHeader("Bearer  " + TOKEN));
        assertEquals(Optional.empty(), SessionToken.fromAuthorizationHeader("Bearer " + TOKEN + " "));
        assertEquals(Optional.empty(), SessionToken.fromAuthorizationHeader("Bearer " + "A".repeat(42)));
        assertEquals(Optional.empty(), SessionToken.fromAuthorizationHeader("Bearer " + "A".repeat(44)));
        assertEquals(Optional.empty(), SessionToken.fromAuthorizationHeader("Bearer " + "A".repeat(42) + "+"));
    }
}