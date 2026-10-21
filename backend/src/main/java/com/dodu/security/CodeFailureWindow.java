package com.dodu.security;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Stateless transition policy; storage must serialize transitions per trusted IP key. */
public final class CodeFailureWindow {
    public static final Duration WINDOW = Duration.ofMinutes(10);
    public static final Duration BLOCK = Duration.ofMinutes(15);
    public static final int FAILURE_LIMIT = 10;

    public record State(List<Instant> failures, Instant blockedUntil) {
        public State {
            failures = List.copyOf(failures);
            if (failures.size() > FAILURE_LIMIT) {
                throw new IllegalArgumentException("Too many failure timestamps");
            }
        }

        public static State empty() {
            return new State(List.of(), null);
        }
    }

    public record Decision(State state, long retryAfterSeconds) {
        public boolean blocked() {
            return retryAfterSeconds > 0;
        }
    }

    public Decision inspect(State state, Instant serverNow) {
        Objects.requireNonNull(state);
        Objects.requireNonNull(serverNow);
        if (state.blockedUntil() != null) {
            if (serverNow.isBefore(state.blockedUntil())) {
                Duration remaining = Duration.between(serverNow, state.blockedUntil());
                long seconds = remaining.getSeconds() + (remaining.getNano() > 0 ? 1 : 0);
                return new Decision(state, seconds);
            }
            return new Decision(State.empty(), 0);
        }
        Instant cutoff = serverNow.minus(WINDOW);
        List<Instant> recent = state.failures().stream()
                .filter(time -> time.isAfter(cutoff))
                .toList();
        return new Decision(new State(recent, null), 0);
    }

    /** Invoke only after an actual invalid-code result, not network/CAPTCHA/AI errors. */
    public Decision failedCode(State state, Instant serverNow) {
        Decision current = inspect(state, serverNow);
        if (current.blocked()) return current;
        List<Instant> failures = new ArrayList<>(current.state().failures());
        failures.add(serverNow);
        if (failures.size() >= FAILURE_LIMIT) {
            return new Decision(new State(List.of(), serverNow.plus(BLOCK)), BLOCK.toSeconds());
        }
        return new Decision(new State(failures, null), 0);
    }
}
