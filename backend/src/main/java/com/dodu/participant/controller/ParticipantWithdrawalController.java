package com.dodu.participant.controller;

import com.dodu.common.api.ApiResponse;
import com.dodu.common.api.ApiTime;
import com.dodu.operator.service.OperatorAccessService;
import com.dodu.participant.domain.SessionContext;
import com.dodu.participant.dto.WithdrawalResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Clock;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Authenticated participant withdrawal. The raw access credential is never accepted here. */
@RestController
@Tag(name = "Participant account")
@RequestMapping("/api/participant/account")
public class ParticipantWithdrawalController {
    private final OperatorAccessService withdrawalService;
    private final Clock clock;

    public ParticipantWithdrawalController(OperatorAccessService withdrawalService, Clock clock) {
        this.withdrawalService = withdrawalService;
        this.clock = clock;
    }

    @Operation(summary = "Request participant withdrawal")
    @DeleteMapping
    @ResponseStatus(HttpStatus.OK)
    public ApiResponse<WithdrawalResponse> withdraw(SessionContext context) {
        Instant requestedAt = clock.instant();
        OperatorAccessService.WithdrawResult result = withdrawalService.withdrawSelf(
                context.participant().id(), requestedAt);
        return ApiResponse.success(new WithdrawalResponse("Withdrawn", result.endedSessions(),
                result.deactivatedSubscriptions(), requestedAt), ApiTime.now(clock));
    }

}
