package com.dodu.participant.config;

import com.dodu.participant.service.ParticipantAuthService;
import java.util.List;
import java.util.Arrays;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.servlet.config.annotation.CorsRegistry;

/**
 * 참가자 API에 세션 인증을 적용합니다. {@code /api/participant/**}는 모두 인증이 필요하고, 입장 API만 예외입니다
 * (입장 전에는 세션이 없음). 입장 경로에 다른 HTTP 메서드(예: 로그아웃)를 추가할 때는 이 제외 규칙을 다시
 * 확인해야 합니다.
 */
@Configuration
public class ParticipantWebConfig implements WebMvcConfigurer {

    static final String[] AUTHENTICATED_PATHS = {"/api/participant/**"};
    static final String[] PUBLIC_PATHS = {"/api/participant/sessions"};

    private final ParticipantAuthService authService;
    private final String[] allowedOrigins;

    public ParticipantWebConfig(
            ParticipantAuthService authService,
            @Value("${dodu.web.allowed-origins}") String allowedOrigins) {
        this.authService = authService;
        this.allowedOrigins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toArray(String[]::new);
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(allowedOrigins)
                .allowedMethods("GET", "POST", "PATCH", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("Accept", "Authorization", "Content-Type", "Idempotency-Key")
                .maxAge(3600);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new ParticipantAuthInterceptor(authService))
                .addPathPatterns(AUTHENTICATED_PATHS)
                .excludePathPatterns(PUBLIC_PATHS);
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new SessionContextArgumentResolver());
    }
}
