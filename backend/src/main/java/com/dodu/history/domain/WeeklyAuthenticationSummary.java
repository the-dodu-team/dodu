package com.dodu.history.domain;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Server-record aggregation only; not an HTTP endpoint or evidence of actual work start. */
public final class WeeklyAuthenticationSummary {
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private final Clock clock;

    public WeeklyAuthenticationSummary(Clock clock) {
        this.clock = Objects.requireNonNull(clock);
    }

    public Summary summarize(UUID authenticatedParticipantId, Collection<PromiseSnapshot> snapshots) {
        Objects.requireNonNull(authenticatedParticipantId);
        Objects.requireNonNull(snapshots);
        LocalDate today = clock.instant().atZone(KST).toLocalDate();
        LocalDate start = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate endExclusive = start.plusDays(7);
        Map<UUID, PromiseSnapshot> seen = new HashMap<>();
        Map<Outcome, Integer> counts = new EnumMap<>(Outcome.class);
        for (PromiseSnapshot snapshot : snapshots) {
            Objects.requireNonNull(snapshot);
            if (!authenticatedParticipantId.equals(snapshot.participantId())) {
                continue;
            }
            LocalDate scheduledDate = snapshot.scheduledAt().atZone(KST).toLocalDate();
            if (scheduledDate.isBefore(start) || !scheduledDate.isBefore(endExclusive)) {
                continue;
            }
            PromiseSnapshot previous = seen.putIfAbsent(snapshot.promiseId(), snapshot);
            if (previous != null) {
                if (!previous.equals(snapshot)) {
                    throw new IllegalArgumentException("Conflicting promise snapshots");
                }
                continue;
            }
            counts.merge(snapshot.outcome(), 1, Integer::sum);
        }
        return new Summary(today, start, endExclusive, counts);
    }

    // Adapter classification of server final outcomes, not a replacement for the promise state machine.
    public enum Outcome {
        AUTH_COMPLETED, AUTH_INCOMPLETE, CANCELLED, TECH_FAILED, PERMISSION_BLOCKED, WITHDRAWN, NOT_FINAL
    }

    public record PromiseSnapshot(UUID promiseId, UUID participantId, Instant scheduledAt, Outcome outcome) {
        public PromiseSnapshot {
            Objects.requireNonNull(promiseId);
            Objects.requireNonNull(participantId);
            Objects.requireNonNull(scheduledAt);
            Objects.requireNonNull(outcome);
        }
    }

    public record Summary(LocalDate today, LocalDate weekStart, LocalDate weekEndExclusive,
                          Map<Outcome, Integer> counts) {
        public Summary {
            counts = Map.copyOf(counts);
        }

        public int count(Outcome outcome) {
            return counts.getOrDefault(outcome, 0);
        }

        public int eligibleTotal() {
            return count(Outcome.AUTH_COMPLETED) + count(Outcome.AUTH_INCOMPLETE);
        }
    }
}
