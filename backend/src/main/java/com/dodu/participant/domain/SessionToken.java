package com.dodu.participant.domain;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * 세션 토큰 생성. 토큰은 256비트 무작위 값이라 토큰 자체로는 아무 정보도 알 수 없습니다(불투명 토큰).
 * 서버가 해시({@link SecretHasher})로 DB에서 조회해야 누구의 세션인지 알 수 있어, 세션 교체·재발급·철회 때
 * DB에서 세션을 종료하는 것만으로 즉시 무효화할 수 있습니다(계약 §3.5).
 */
public final class SessionToken {

    /** 토큰 길이(바이트). Base64url로 43자가 됩니다. */
    public static final int TOKEN_BYTES = 32;

    /** 토큰 길이(문자). 32바이트를 패딩 없이 Base64url로 인코딩한 길이입니다. */
    public static final int TOKEN_LENGTH = 43;

    private static final String BEARER_PREFIX = "Bearer ";
    private static final Pattern FORMAT = Pattern.compile("[A-Za-z0-9_-]{" + TOKEN_LENGTH + "}");

    private SessionToken() {
    }

    public static String generate(SecureRandom random) {
        Objects.requireNonNull(random, "random must not be null");
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** 토큰 형식(43자 Base64url)인지. 형식이 틀리면 DB를 조회하지 않고 거부하기 위한 검사입니다. */
    public static boolean isWellFormed(String token) {
        return token != null && FORMAT.matcher(token).matches();
    }

    /**
     * {@code Authorization: Bearer <토큰>} 헤더에서 토큰을 꺼냅니다. 방식 이름의 대소문자는 구분하지 않고,
     * 헤더가 없거나 형식이 맞지 않으면 빈 값입니다.
     */
    public static Optional<String> fromAuthorizationHeader(String header) {
        if (header == null
                || header.length() != BEARER_PREFIX.length() + TOKEN_LENGTH
                || !header.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            return Optional.empty();
        }
        String token = header.substring(BEARER_PREFIX.length());
        return isWellFormed(token) ? Optional.of(token) : Optional.empty();
    }
}