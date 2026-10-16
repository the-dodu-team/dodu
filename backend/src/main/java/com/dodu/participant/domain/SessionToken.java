package com.dodu.participant.domain;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Objects;

/**
 * 세션 토큰 생성. 토큰은 256비트 무작위 값이라 토큰 자체로는 아무 정보도 알 수 없습니다(불투명 토큰).
 * 서버가 해시({@link SecretHasher})로 DB에서 조회해야 누구의 세션인지 알 수 있어, 세션 교체·재발급·철회 때
 * DB에서 세션을 종료하는 것만으로 즉시 무효화할 수 있습니다(계약 §3.5).
 */
public final class SessionToken {

    /** 토큰 길이(바이트). Base64url로 43자가 됩니다. */
    public static final int TOKEN_BYTES = 32;

    private SessionToken() {
    }

    public static String generate(SecureRandom random) {
        Objects.requireNonNull(random, "random must not be null");
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}