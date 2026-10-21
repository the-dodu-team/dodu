package com.dodu.participant.controller;

import com.dodu.common.api.ApiResponse;
import com.dodu.common.api.ApiTime;
import com.dodu.participant.dto.EnterRequest;
import com.dodu.participant.dto.EnterResponse;
import com.dodu.participant.service.ParticipantEntryService;
import com.dodu.participant.service.ParticipantEntryService.EnterResult;
import java.time.Clock;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 참가자 입장·세션 API. HTTP 입력과 응답 형식만 다루고 업무 규칙은 service에 둡니다. */
@RestController
@RequestMapping("/api/participant")
public class ParticipantSessionController {

    private final ParticipantEntryService entryService;
    private final Clock clock;

    public ParticipantSessionController(ParticipantEntryService entryService, Clock clock) {
        this.entryService = entryService;
        this.clock = clock;
    }

    @PostMapping("/sessions")
    public ApiResponse<EnterResponse> enter(@RequestBody EnterRequest request) {
        EnterResult result = entryService.enter(request.code(), request.deviceKey());
        EnterResponse body = new EnterResponse(
                result.sessionToken(),
                ApiTime.format(result.expiresAt(), clock),
                result.onboardingCompleted());
        return ApiResponse.success(body, ApiTime.now(clock));
    }
}