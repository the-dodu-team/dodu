package com.dodu.participant.domain;

import java.security.SecureRandom;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * Participant entry code: generation, display format and input normalization.
 *
 * <p>See docs/security/PARTICIPANT_ACCESS_CONTRACT.md. A code is {@value #LENGTH} characters from a
 * 31-character alphabet without look-alike characters (0/O, 1/I/L), shown as {@code XXXX-XXXX-XXXX}.
 * Input is case-insensitive and ignores hyphens and whitespace.
 */
public final class AccessCode {

    /** Letters A-Z without I, L, O plus digits 2-9. */
    public static final String ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
    public static final int LENGTH = 12;
    public static final int GROUP_SIZE = 4;

    private AccessCode() {
    }

    /** Returns a new code in normalized form (no separators). */
    public static String generate(SecureRandom random) {
        Objects.requireNonNull(random, "random must not be null");
        StringBuilder code = new StringBuilder(LENGTH);
        for (int i = 0; i < LENGTH; i++) {
            // nextInt(bound) is uniform, so the alphabet size needs no power-of-two padding.
            code.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return code.toString();
    }

    /** Formats a normalized code for display: {@code XXXX-XXXX-XXXX}. */
    public static String format(String normalizedCode) {
        if (!isNormalized(normalizedCode)) {
            throw new IllegalArgumentException("not a normalized access code");
        }
        StringBuilder formatted = new StringBuilder(LENGTH + LENGTH / GROUP_SIZE - 1);
        for (int i = 0; i < LENGTH; i += GROUP_SIZE) {
            if (i > 0) {
                formatted.append('-');
            }
            formatted.append(normalizedCode, i, i + GROUP_SIZE);
        }
        return formatted.toString();
    }

    /**
     * Normalizes user input: upper-cases, removes hyphens and whitespace.
     *
     * @return the normalized code, or empty if the input is null, has the wrong length, or contains
     *         characters outside the alphabet
     */
    public static Optional<String> normalize(String input) {
        if (input == null) {
            return Optional.empty();
        }
        StringBuilder stripped = new StringBuilder(LENGTH);
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (c == '-' || Character.isWhitespace(c)) {
                continue;
            }
            stripped.append(c);
        }
        String normalized = stripped.toString().toUpperCase(Locale.ROOT);
        return isNormalized(normalized) ? Optional.of(normalized) : Optional.empty();
    }

    /** True if the value is exactly {@value #LENGTH} alphabet characters. */
    public static boolean isNormalized(String value) {
        if (value == null || value.length() != LENGTH) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            if (ALPHABET.indexOf(value.charAt(i)) < 0) {
                return false;
            }
        }
        return true;
    }
}