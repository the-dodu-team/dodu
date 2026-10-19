package com.dodu.participant.config;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dodu.common.exception.BusinessException;
import com.dodu.common.exception.ErrorType;
import com.dodu.common.exception.GlobalExceptionHandler;
import com.dodu.participant.controller.ParticipantController;
import com.dodu.participant.controller.ParticipantSessionController;
import com.dodu.participant.domain.Participant;
import com.dodu.participant.domain.ParticipantSession;
import com.dodu.participant.domain.SessionContext;
import com.dodu.participant.service.ParticipantAuthService;
import com.dodu.participant.service.ParticipantEntryService;
import com.dodu.participant.service.ParticipantEntryService.EnterResult;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

/**
 * 인터셉터·인자 주입·오류 응답이 HTTP 수준에서 맞물리는지 확인합니다. 실제 {@link ParticipantWebConfig}를
 * 올린 웹 설정에서 인증 service와 입장 service만 mock으로 바꿉니다. DB와 Spring Boot 전체 컨텍스트는 쓰지 않습니다.
 */
class ParticipantAuthWebTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-20T03:00:00Z"), ZoneId.of("Asia/Seoul"));
    private static final String TOKEN = "A".repeat(43);
    private static final UUID PARTICIPANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID SESSION_ID = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final Instant SESSION_EXPIRES_AT = Instant.parse("2026-11-19T03:00:00Z");

    /** 인자 주입을 확인하기 위한 테스트 전용 컨트롤러. 운영 코드에는 없습니다. */
    @RestController
    static class ProbeController {
        @GetMapping("/api/participant/probe")
        String protectedProbe(Participant participant, ParticipantSession session) {
            return participant.id() + ":" + session.id();
        }

        /** 인터셉터가 적용되지 않는 경로. 인자를 요구하면 통과시키지 않고 막아야 합니다. */
        @GetMapping("/api/unprotected/probe")
        String unprotectedProbe(Participant participant) {
            return participant.id().toString();
        }
    }

    @Configuration
    @EnableWebMvc
    @Import(ParticipantWebConfig.class)
    static class WebTestConfig {
        @Bean
        Clock clock() {
            return CLOCK;
        }

        @Bean
        ParticipantAuthService participantAuthService() {
            return mock(ParticipantAuthService.class);
        }

        @Bean
        ParticipantEntryService participantEntryService() {
            return mock(ParticipantEntryService.class);
        }

        @Bean
        ParticipantController participantController(Clock clock) {
            return new ParticipantController(clock);
        }

        @Bean
        ParticipantSessionController participantSessionController(ParticipantEntryService entryService, Clock clock) {
            return new ParticipantSessionController(entryService, clock);
        }

        @Bean
        ProbeController probeController() {
            return new ProbeController();
        }

        @Bean
        GlobalExceptionHandler globalExceptionHandler(Clock clock) {
            return new GlobalExceptionHandler(clock);
        }
    }

    private ParticipantAuthService authService;
    private ParticipantEntryService entryService;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        AnnotationConfigWebApplicationContext context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.register(WebTestConfig.class);
        context.refresh();
        authService = context.getBean(ParticipantAuthService.class);
        entryService = context.getBean(ParticipantEntryService.class);
        reset(authService, entryService);
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    void meReturnsOnlyStateFieldsOfTheCurrentParticipant() throws Exception {
        when(authService.authenticate("Bearer " + TOKEN)).thenReturn(
                context(Instant.parse("2026-10-20T02:00:00Z"), SESSION_EXPIRES_AT, SESSION_EXPIRES_AT));

        mvc.perform(get("/api/participant/me").header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.onboardingCompleted").value(true))
                .andExpect(jsonPath("$.data.expiresAt").value("2026-11-19T12:00:00+09:00"))
                .andExpect(jsonPath("$.data.participantId").doesNotExist())
                .andExpect(jsonPath("$.data.sessionId").doesNotExist());
    }

    @Test
    void meExpiresAtFollowsTheEarlierAccessEnd() throws Exception {
        Instant accessEndsAt = Instant.parse("2026-11-10T03:00:00Z");
        when(authService.authenticate("Bearer " + TOKEN)).thenReturn(
                context(null, SESSION_EXPIRES_AT, accessEndsAt));

        mvc.perform(get("/api/participant/me").header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.onboardingCompleted").value(false))
                .andExpect(jsonPath("$.data.expiresAt").value("2026-11-10T12:00:00+09:00"));
    }

    @Test
    void controllerReceivesParticipantAndSessionAsArguments() throws Exception {
        when(authService.authenticate("Bearer " + TOKEN)).thenReturn(context(null, SESSION_EXPIRES_AT, SESSION_EXPIRES_AT));

        mvc.perform(get("/api/participant/probe").header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isOk())
                .andExpect(content().string(PARTICIPANT_ID + ":" + SESSION_ID));
    }

    @Test
    void argumentIsRefusedOnAPathTheInterceptorDoesNotCover() throws Exception {
        mvc.perform(get("/api/unprotected/probe").header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("ACCESS_REQUIRED"));

        verifyNoInteractions(authService);
    }

    @Test
    void missingTokenIsAccessRequiredInCommonErrorFormat() throws Exception {
        when(authService.authenticate(any())).thenThrow(ParticipantAuthService.accessRequired());

        mvc.perform(get("/api/participant/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.error.type").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.error.code").value("ACCESS_REQUIRED"))
                .andExpect(jsonPath("$.serverTime").exists());
    }

    @Test
    void sessionReplacedAndAccessRevokedKeepTheirOwnCodes() throws Exception {
        when(authService.authenticate("Bearer " + TOKEN))
                .thenThrow(new BusinessException(ErrorType.UNAUTHENTICATED,
                        ParticipantAuthService.SESSION_REPLACED, "다른 기기에서 이용 중이에요."));
        mvc.perform(get("/api/participant/me").header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("SESSION_REPLACED"));

        doThrow(new BusinessException(ErrorType.UNAUTHENTICATED,
                ParticipantAuthService.ACCESS_REVOKED, "더 이상 이용할 수 없어요."))
                .when(authService).authenticate("Bearer " + TOKEN);
        mvc.perform(get("/api/participant/me").header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("ACCESS_REVOKED"));
    }

    @Test
    void entryPathDoesNotRequireASession() throws Exception {
        when(entryService.enter(any(), any())).thenReturn(
                new EnterResult("t".repeat(43), Instant.parse("2026-11-19T03:00:00Z"), false));

        mvc.perform(post("/api/participant/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"ABCD-EFGH-JKMN\",\"deviceKey\":\"device-key-aaaaaaaaaaaaaaaa\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.onboardingCompleted").value(false));

        verifyNoInteractions(authService);
    }

    @Test
    void preflightRequestSkipsAuthentication() throws Exception {
        mvc.perform(options("/api/participant/me")).andExpect(status().isOk());

        verifyNoInteractions(authService);
    }

    /** 인증을 통과한 요청의 값. {@code onboardingCompletedAt}이 null이면 온보딩 전입니다. */
    private static SessionContext context(Instant onboardingCompletedAt, Instant sessionExpiresAt, Instant accessEndsAt) {
        Participant participant = new Participant(PARTICIPANT_ID, null, null, onboardingCompletedAt);
        ParticipantSession session = new ParticipantSession(
                SESSION_ID, PARTICIPANT_ID, true, null, Instant.parse("2026-10-20T03:00:00Z"), sessionExpiresAt);
        return new SessionContext(participant, session, accessEndsAt);
    }
}