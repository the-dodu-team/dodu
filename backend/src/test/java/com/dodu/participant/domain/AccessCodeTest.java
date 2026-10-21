package com.dodu.participant.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.security.SecureRandom;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AccessCodeTest {

    private static final SecureRandom RANDOM = new SecureRandom();

    @Test
    void alphabetHasNoLookAlikeCharacters() {
        assertEquals(31, AccessCode.ALPHABET.length());
        for (char c : "0O1IL".toCharArray()) {
            assertEquals(-1, AccessCode.ALPHABET.indexOf(c), "must not contain " + c);
        }
        assertEquals(AccessCode.ALPHABET.length(), AccessCode.ALPHABET.chars().distinct().count());
    }

    @Test
    void generatesTwelveCharactersFromAlphabet() {
        for (int i = 0; i < 200; i++) {
            String code = AccessCode.generate(RANDOM);
            assertEquals(AccessCode.LENGTH, code.length());
            assertTrue(AccessCode.isNormalized(code), code);
        }
    }

    @Test
    void formatsInGroupsOfFour() {
        assertEquals("ABCD-EFGH-JKMN", AccessCode.format("ABCDEFGHJKMN"));
    }

    @Test
    void formatRejectsNonNormalizedInput() {
        assertThrows(IllegalArgumentException.class, () -> AccessCode.format("ABCD-EFGH-JKMN"));
        assertThrows(IllegalArgumentException.class, () -> AccessCode.format(null));
    }

    @Test
    void normalizeIgnoresCaseSeparatorsAndWhitespace() {
        Optional<String> expected = Optional.of("ABCDEFGHJKMN");
        assertEquals(expected, AccessCode.normalize("ABCD-EFGH-JKMN"));
        assertEquals(expected, AccessCode.normalize("abcd-efgh-jkmn"));
        assertEquals(expected, AccessCode.normalize("  abcd efgh\tjkmn \n"));
        assertEquals(expected, AccessCode.normalize("abcdefghjkmn"));
    }

    @Test
    void normalizeRoundTripsGeneratedCodes() {
        String code = AccessCode.generate(RANDOM);
        assertEquals(Optional.of(code), AccessCode.normalize(AccessCode.format(code)));
    }

    @Test
    void normalizeRejectsWrongLength() {
        assertEquals(Optional.empty(), AccessCode.normalize("ABCD-EFGH-JKM"));
        assertEquals(Optional.empty(), AccessCode.normalize("ABCD-EFGH-JKMN-P"));
        assertEquals(Optional.empty(), AccessCode.normalize(""));
    }

    @Test
    void normalizeRejectsCharactersOutsideAlphabet() {
        assertEquals(Optional.empty(), AccessCode.normalize("ABCD-EFGH-JKM0"));
        assertEquals(Optional.empty(), AccessCode.normalize("ABCD-EFGH-JKMO"));
        assertEquals(Optional.empty(), AccessCode.normalize("ABCD-EFGH-JKM1"));
        assertEquals(Optional.empty(), AccessCode.normalize("ABCD-EFGH-JKMI"));
        assertEquals(Optional.empty(), AccessCode.normalize("ABCD-EFGH-JKM!"));
    }

    @Test
    void normalizeRejectsNull() {
        assertEquals(Optional.empty(), AccessCode.normalize(null));
    }

    @Test
    void isNormalizedRejectsLowercaseAndSeparators() {
        assertFalse(AccessCode.isNormalized("abcdefghjkmn"));
        assertFalse(AccessCode.isNormalized("ABCD-EFGH-JKMN"));
    }
}