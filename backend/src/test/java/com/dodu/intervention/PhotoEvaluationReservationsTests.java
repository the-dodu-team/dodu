package com.dodu.intervention;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class PhotoEvaluationReservationsTests {
    @Test
    void onlyOneConcurrentRequestAcquiresTheSamePromise() throws Exception {
        PhotoEvaluationReservations reservations = new PhotoEvaluationReservations();
        UUID promiseId = UUID.randomUUID();
        CountDownLatch ready = new CountDownLatch(16);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(16)) {
            List<Future<Boolean>> results = new ArrayList<>();
            for (int i = 0; i < 16; i++) {
                results.add(executor.submit(() -> {
                    ready.countDown();
                    if (!start.await(5, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("test start timed out");
                    }
                    return reservations.tryReserve(promiseId, UUID.randomUUID());
                }));
            }
            try {
                assertTrue(ready.await(5, TimeUnit.SECONDS));
            } finally {
                start.countDown();
            }
            int acquired = 0;
            for (Future<Boolean> result : results) {
                if (result.get(5, TimeUnit.SECONDS)) {
                    acquired++;
                }
            }
            assertEquals(1, acquired);
        }
    }

    @Test
    void staleReleaseCannotClearNewAttemptAndRetryCannotAcquireTwice() {
        PhotoEvaluationReservations reservations = new PhotoEvaluationReservations();
        UUID promiseId = UUID.randomUUID();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        assertTrue(reservations.tryReserve(promiseId, first));
        assertFalse(reservations.tryReserve(promiseId, first));
        assertTrue(reservations.release(promiseId, first));
        assertTrue(reservations.tryReserve(promiseId, second));
        assertFalse(reservations.release(promiseId, first));
        assertFalse(reservations.tryReserve(promiseId, UUID.randomUUID()));
        assertTrue(reservations.tryReserve(UUID.randomUUID(), UUID.randomUUID()));
        assertTrue(reservations.release(promiseId, second));
    }
}
