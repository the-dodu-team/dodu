package com.dodu.history.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WeeklyAuthenticationSummaryTests {
    private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private final WeeklyAuthenticationSummary policy = new WeeklyAuthenticationSummary(
            Clock.fixed(Instant.parse("2026-10-04T15:00:00Z"), ZoneOffset.UTC));

    @Test
    void weekUsesKstMondayBoundaryAndFinalScheduledDate() {
        var result = policy.summarize(OWNER, List.of(
                snapshot(1, OWNER, "2026-10-04T14:59:59Z", WeeklyAuthenticationSummary.Outcome.AUTH_COMPLETED),
                snapshot(2, OWNER, "2026-10-04T15:00:00Z", WeeklyAuthenticationSummary.Outcome.AUTH_COMPLETED),
                snapshot(3, OWNER, "2026-10-11T14:59:59Z", WeeklyAuthenticationSummary.Outcome.AUTH_INCOMPLETE),
                snapshot(4, OWNER, "2026-10-11T15:00:00Z", WeeklyAuthenticationSummary.Outcome.AUTH_COMPLETED)));
        assertEquals(LocalDate.parse("2026-10-05"), result.today());
        assertEquals(LocalDate.parse("2026-10-05"), result.weekStart());
        assertEquals(LocalDate.parse("2026-10-12"), result.weekEndExclusive());
        assertEquals(2, result.eligibleTotal());
        assertEquals(1, result.count(WeeklyAuthenticationSummary.Outcome.AUTH_COMPLETED));
    }

    @Test
    void exceptionalAndNonFinalOutcomesNeverCountAsIncomplete() {
        var snapshots = new ArrayList<WeeklyAuthenticationSummary.PromiseSnapshot>();
        int id = 1;
        for (var outcome : WeeklyAuthenticationSummary.Outcome.values()) {
            snapshots.add(snapshot(id++, OWNER, "2026-10-06T01:00:00Z", outcome));
        }
        var result = policy.summarize(OWNER, snapshots);
        assertEquals(2, result.eligibleTotal());
        assertEquals(1, result.count(WeeklyAuthenticationSummary.Outcome.AUTH_INCOMPLETE));
        for (var outcome : WeeklyAuthenticationSummary.Outcome.values()) {
            assertEquals(1, result.count(outcome));
        }
        assertThrows(UnsupportedOperationException.class,
                () -> result.counts().put(WeeklyAuthenticationSummary.Outcome.AUTH_COMPLETED, 99));
    }

    @Test
    void anotherParticipantsRecordsAreExcludedAndRepeatedRowsCountOnlyOnce() {
        var own = snapshot(1, OWNER, "2026-10-06T01:00:00Z", WeeklyAuthenticationSummary.Outcome.AUTH_COMPLETED);
        var result = policy.summarize(OWNER, List.of(own, own,
                snapshot(2, new UUID(0, 99), "2026-10-06T01:00:00Z",
                        WeeklyAuthenticationSummary.Outcome.AUTH_INCOMPLETE)));
        assertEquals(1, result.eligibleTotal());
        assertEquals(0, result.count(WeeklyAuthenticationSummary.Outcome.AUTH_INCOMPLETE));
    }

    @Test
    void conflictingDuplicateSnapshotsFailRatherThanSelectingAnArbitraryResult() {
        assertThrows(IllegalArgumentException.class, () -> policy.summarize(OWNER, List.of(
                snapshot(1, OWNER, "2026-10-06T01:00:00Z", WeeklyAuthenticationSummary.Outcome.AUTH_COMPLETED),
                snapshot(1, OWNER, "2026-10-06T01:00:00Z", WeeklyAuthenticationSummary.Outcome.AUTH_INCOMPLETE))));
    }

    @Test
    void emptyWeekHasNoInventedFailures() {
        var result = policy.summarize(OWNER, List.of());
        assertEquals(0, result.eligibleTotal());
        assertEquals(0, result.count(WeeklyAuthenticationSummary.Outcome.AUTH_INCOMPLETE));
    }

    private static WeeklyAuthenticationSummary.PromiseSnapshot snapshot(
            int id, UUID participant, String scheduledAt, WeeklyAuthenticationSummary.Outcome outcome) {
        return new WeeklyAuthenticationSummary.PromiseSnapshot(new UUID(0, id), participant,
                Instant.parse(scheduledAt), outcome);
    }
}
