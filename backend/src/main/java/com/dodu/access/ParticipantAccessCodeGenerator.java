package com.dodu.access;

import java.security.SecureRandom;

/** Generates the approved 12-character code using an ambiguity-free alphabet. */
public final class ParticipantAccessCodeGenerator {
    private static final char[] ALPHABET = "23456789ABCDEFGHJKMNPQRSTUVWXYZ".toCharArray();
    private static final int CODE_LENGTH = 12;
    private final SecureRandom random;

    public ParticipantAccessCodeGenerator() {
        this(new SecureRandom());
    }

    ParticipantAccessCodeGenerator(SecureRandom random) {
        this.random = random;
    }

    public String generate() {
        char[] code = new char[CODE_LENGTH];
        for (int index = 0; index < code.length; index++) {
            code[index] = ALPHABET[random.nextInt(ALPHABET.length)];
        }
        return new String(code);
    }
}
