package com.dodu.participant.dto;

import java.time.Instant;

public record WithdrawalResponse(
        String lifecycleStatus,
        int endedSessions,

        int deactivatedSubscriptions,
        Instant requestedAt) { }
