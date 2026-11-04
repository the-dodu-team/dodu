package com.dodu.participant.controller;

import com.dodu.common.api.ApiResponse;
import com.dodu.common.api.ApiTime;
import com.dodu.participant.domain.SessionContext;
import com.dodu.participant.dto.MeResponse;
import com.dodu.participant.service.ParticipantOnboardingService;
import java.time.Clock;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ParticipantOnboardingController {
    private final ParticipantOnboardingService service;
    private final Clock clock;

    public ParticipantOnboardingController(ParticipantOnboardingService service, Clock clock) {
        this.service = service;
        this.clock = clock;
    }

    @PutMapping("/api/participant/onboarding")
    public ApiResponse<MeResponse> complete(SessionContext context) {
        service.complete(context);
        return ApiResponse.success(new MeResponse(true, ApiTime.format(context.expiresAt(), clock)),
                ApiTime.now(clock));
    }
}
