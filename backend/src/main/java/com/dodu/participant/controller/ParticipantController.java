package com.dodu.participant.controller;

import com.dodu.common.api.ApiResponse;
import com.dodu.common.api.ApiTime;
import com.dodu.participant.domain.SessionContext;
import com.dodu.participant.dto.MeResponse;
import java.time.Clock;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 인증된 참가자 자신에 대한 API. 세션 인증은 인터셉터가 먼저 처리하므로 여기서는 인증된 요청의
 * 값을 응답 형식으로 바꾸기만 합니다.
 */
@RestController
@Tag(name = "참가자")
@RequestMapping("/api/participant")
public class ParticipantController {

    private final Clock clock;

    public ParticipantController(Clock clock) {
        this.clock = clock;
    }

    @Operation(summary = "내 상태 조회")
    @GetMapping("/me")
    public ApiResponse<MeResponse> me(SessionContext context) {
        // 만료 시각은 세션 값과 연구 기간의 접근 종료 시각 중 이른 쪽(실제로 접근이 끝나는 시각)입니다.
        MeResponse body = new MeResponse(
                context.participant().onboardingCompleted(), ApiTime.format(context.expiresAt(), clock));
        return ApiResponse.success(body, ApiTime.now(clock));
    }
}