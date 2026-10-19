package com.dodu.participant.config;

import com.dodu.participant.domain.SessionContext;
import com.dodu.participant.service.ParticipantAuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 참가자 API 요청의 세션을 확인하고 인증된 참가자를 요청에 담습니다.
 *
 * <p>서블릿 필터가 아니라 인터셉터로 만든 이유: 필터에서 던진 예외는 {@code GlobalExceptionHandler}를 거치지
 * 않아 오류 응답 형식을 따로 만들어야 합니다. 인터셉터는 컨트롤러와 같은 오류 처리를 그대로 씁니다.
 * 인증 실패는 {@link com.dodu.common.exception.BusinessException}(401)으로 던집니다.
 */
public class ParticipantAuthInterceptor implements HandlerInterceptor {

    /** 인증된 요청의 {@link SessionContext}를 담는 요청 속성 이름. */
    static final String ATTRIBUTE = ParticipantAuthInterceptor.class.getName() + ".sessionContext";

    private final ParticipantAuthService authService;

    public ParticipantAuthInterceptor(ParticipantAuthService authService) {
        this.authService = authService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // CORS 사전 요청(OPTIONS)은 Authorization 헤더 없이 오므로 통과시킵니다.
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        SessionContext context = authService.authenticate(request.getHeader("Authorization"));
        request.setAttribute(ATTRIBUTE, context);
        return true;
    }



}
