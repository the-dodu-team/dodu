package com.dodu.participant.controller;

import com.dodu.common.api.ApiResponse;
import com.dodu.common.api.ApiTime;
import com.dodu.participant.domain.SessionContext;
import com.dodu.participant.dto.MeResponse;
import java.time.Clock;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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
    private final boolean dailyPromiseLimit;

    public ParticipantController(Clock clock) {
        this(clock, true);
    }

    @Autowired
    public ParticipantController(Clock clock,
                                 @Value("${dodu.promise.daily-limit.enabled:true}") boolean dailyPromiseLimit) {
        this.clock = clock;
        this.dailyPromiseLimit = dailyPromiseLimit;
    }

    @Operation(summary = "내 상태 조회")
    @GetMapping("/me")
    public ApiResponse<MeResponse> me(SessionContext context) {
        // 만료 시각은 세션 값과 연구 기간의 접근 종료 시각 중 이른 쪽(실제로 접근이 끝나는 시각)입니다.
        // 하루 1약속 제한 여부도 함께 보내 화면이 서버 설정과 같은 기준으로 날짜를 막게 한다.
        MeResponse body = new MeResponse(
                context.participant().onboardingCompleted(), ApiTime.format(context.expiresAt(), clock), dailyPromiseLimit);
        return ApiResponse.success(body, ApiTime.now(clock));
    }
}