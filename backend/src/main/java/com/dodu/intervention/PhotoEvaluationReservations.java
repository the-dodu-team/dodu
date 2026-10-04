package com.dodu.intervention;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Atomic evaluation reservations within one server process.
 * A database transaction must provide the same guarantee across server instances.
 * Callers must validate participant ownership before reserving or releasing a slot.
 */
public final class PhotoEvaluationReservations {
    private final ConcurrentMap<UUID, UUID> reservations = new ConcurrentHashMap<>();

    /** Returns true only to the request that acquires the promise's evaluation slot. */
    public boolean tryReserve(UUID promiseId, UUID attemptId) {
        Objects.requireNonNull(promiseId, "promiseId must not be null");
        Objects.requireNonNull(attemptId, "attemptId must not be null");
        return reservations.putIfAbsent(promiseId, attemptId) == null;
    }

    /** A stale result cannot release a slot owned by a newer attempt. */
    public boolean release(UUID promiseId, UUID attemptId) {
        Objects.requireNonNull(promiseId, "promiseId must not be null");
        Objects.requireNonNull(attemptId, "attemptId must not be null");
        return reservations.remove(promiseId, attemptId);
    }
}
