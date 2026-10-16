package com.dodu.access;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.Set;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class ParticipantAccessCodeGeneratorTests {
    @Test
    void generatesTwelveCharactersFromTheApprovedAlphabet() {
        String code = new ParticipantAccessCodeGenerator().generate();
        assertEquals(12, code.length());
        assertFalse(code.matches(".*[01ILO].*"));
        assertEquals(12, code.chars().filter(Character::isLetterOrDigit).count());
    }

    @Test
    void generatesIndependentCodesAcrossRepeatedIssuance() {
        Set<String> codes = IntStream.range(0, 100)
                .mapToObj(ignored -> new ParticipantAccessCodeGenerator().generate())
                .collect(java.util.stream.Collectors.toSet());
        assertEquals(100, codes.size());
    }
}
