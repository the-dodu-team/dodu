package com.dodu.intervention;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class InterventionLifecycleGateTests {
    private static final Instant END = Instant.parse("2026-10-16T01:00:00Z");

    @Test
    void allowsNewWorkOnlyBeforeOfficialEnd() {
        var gate = new InterventionLifecycleGate();
        assertEquals(InterventionLifecycleGate.Decision.NEW_WORK_ALLOWED,
                gate.decide(END.minusSeconds(1), END, false));
        assertEquals(InterventionLifecycleGate.Decision.ACCEPTED_WORK_ONLY,
                gate.decide(END, END, true));
        assertEquals(InterventionLifecycleGate.Decision.CLOSED,
                gate.decide(END, END, false));
    }
}
