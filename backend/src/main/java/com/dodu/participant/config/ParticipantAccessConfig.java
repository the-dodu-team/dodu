package com.dodu.participant.config;

import com.dodu.participant.domain.SecretHasher;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 참가자 접근 관련 빈.
 *
 * <p>pepper는 환경변수({@code DODU_ACCESS_CODE_PEPPER})로만 주입합니다. 값이 없거나 32바이트보다
 * 짧으면 서버가 시작되지 않습니다. 저장소·DB·로그에 남기지 않습니다.
 */
@Configuration
public class ParticipantAccessConfig {

    @Bean
    SecretHasher secretHasher(@Value("${dodu.access.code-pepper:}") String pepper) {
        return new SecretHasher(pepper.getBytes(StandardCharsets.UTF_8));
    }

    @Bean
    SecureRandom secureRandom() {
        return new SecureRandom();
    }
}