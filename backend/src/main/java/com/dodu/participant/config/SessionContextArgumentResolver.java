package com.dodu.participant.config;

import com.dodu.participant.domain.Participant;
import com.dodu.participant.domain.ParticipantSession;
import com.dodu.participant.domain.SessionContext;
import com.dodu.participant.service.ParticipantAuthService;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * 컨트롤러 메서드의 {@link Participant}, {@link ParticipantSession}, {@link SessionContext} 인자에 인터셉터가
 * 담아 둔 값을 넣습니다. 보통은 {@code Participant}만 받으면 되고, 세션이나 실제 만료 시각이 필요할 때만
 * 나머지를 받습니다.
 *
 * <p>인터셉터를 거치지 않은 경로에서 이 인자를 요구하면 통과시키지 않고 401({@code ACCESS_REQUIRED})로 막습니다.
 */
public class SessionContextArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        Class<?> type = parameter.getParameterType();
        return Participant.class.equals(type)
                || ParticipantSession.class.equals(type)
                || SessionContext.class.equals(type);
    }

    @Override
    public Object resolveArgument(
            MethodParameter parameter,
            ModelAndViewContainer mavContainer,
            NativeWebRequest webRequest,
            WebDataBinderFactory binderFactory) {
        Object value = webRequest.getAttribute(ParticipantAuthInterceptor.ATTRIBUTE, RequestAttributes.SCOPE_REQUEST);
        if (!(value instanceof SessionContext context)) {
            throw ParticipantAuthService.accessRequired();
        }
        Class<?> type = parameter.getParameterType();
        if (Participant.class.equals(type)) {
            return context.participant();
        }
        if (ParticipantSession.class.equals(type)) {
            return context.session();
        }
        return context;
    }
}