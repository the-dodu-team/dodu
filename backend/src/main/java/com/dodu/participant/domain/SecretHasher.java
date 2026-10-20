package com.dodu.participant.domain;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;
import java.util.Objects;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * 입장 코드, 세션 토큰, 기기 식별 키처럼 DB에 원문을 두지 않는 비밀 값을 HMAC-SHA-256과 서버 pepper로
 * 해시합니다.
 *
 * <p>DB에는 소문자 hex 해시(64자)만 저장합니다(예: {@code access_credential.secret_hash},
 * {@code participant_session.session_token_hash}). 값의 형식 검증은 호출하는 쪽이 먼저 합니다
 * (예: {@link AccessCode#normalize(String)}). pepper는 DB가 아니라 환경 설정에서 가져와야 하며,
 * pepper가 바뀌면 이미 저장된 해시와 일치하지 않게 됩니다.
 */
public final class SecretHasher {

    public static final int MIN_PEPPER_BYTES = 32;
    private static final String ALGORITHM = "HmacSHA256";

    private final SecretKeySpec key;

    public SecretHasher(byte[] pepper) {
        Objects.requireNonNull(pepper, "pepper must not be null");
        if (pepper.length < MIN_PEPPER_BYTES) {
            throw new IllegalArgumentException("pepper must be at least " + MIN_PEPPER_BYTES + " bytes");
        }
        this.key = new SecretKeySpec(pepper.clone(), ALGORITHM);
    }

    public String hash(String value) {
        Objects.requireNonNull(value, "value must not be null");
        try {
            Mac mac = Mac.getInstance(ALGORITHM); // Mac은 스레드 안전하지 않으므로 호출마다 새로 만듭니다
            mac.init(key);
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC-SHA-256 is unavailable", e);
        }
    }
}