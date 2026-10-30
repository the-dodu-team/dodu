package com.dodu.common;

import com.dodu.participant.domain.Participant;
import com.dodu.participant.domain.ParticipantSession;
import com.dodu.participant.domain.SessionContext;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.utils.SpringDocUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** API 문서(OpenAPI) 공통 설정. 켜고 끄는 것은 {@code springdoc.*.enabled}가 정합니다. */
@Configuration
public class OpenApiConfig {

    static {
        // 인터셉터가 채워 주는 인자는 요청 값이 아니므로, 문서에 쿼리 파라미터로 나오지 않게 뺍니다.
        SpringDocUtils.getConfig().addRequestWrapperToIgnore(
                SessionContext.class, ParticipantSession.class, Participant.class);
    }

    static final String SESSION_SCHEME = "participantSession";

    @Bean
    OpenAPI doduOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Dodu API")
                        .version("v1")
                        .description("개발 환경용 API 문서입니다. 참가자 API는 입장으로 받은 세션 토큰이 필요합니다."))
                .components(new Components().addSecuritySchemes(SESSION_SCHEME,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .description("입장 API로 받은 세션 토큰. 'Bearer ' 없이 토큰 값만 입력합니다.")))
                .addSecurityItem(new SecurityRequirement().addList(SESSION_SCHEME));
    }
}