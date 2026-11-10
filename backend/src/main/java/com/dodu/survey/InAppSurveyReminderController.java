package com.dodu.survey;

import com.dodu.common.api.ApiResponse;
import com.dodu.common.api.ApiTime;
import com.dodu.participant.service.ParticipantAuthService;
import java.time.Clock;
import java.util.UUID;

import com.dodu.survey.dto.ReminderResponse;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class InAppSurveyReminderController {
    private final InAppSurveyReminderService service;
    private final ParticipantAuthService auth;
    private final Clock clock;

    public InAppSurveyReminderController(InAppSurveyReminderService service, ParticipantAuthService auth, Clock clock) {
        this.service = service; this.auth = auth; this.clock = clock;
    }

    @PostMapping("/api/promises/{promiseId}/validation-survey/inapp-reminder")
    public ApiResponse<ReminderResponse> claim(@PathVariable UUID promiseId,
                                               @RequestHeader(value = "Authorization", required = false) String authorization) {
        boolean show = service.claim(promiseId, auth.authenticate(authorization));
        return ApiResponse.success(new ReminderResponse(show), ApiTime.now(clock));
    }

}
