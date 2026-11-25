package com.dodu.intervention;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.lang.reflect.Field;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

/** 대기열에 들어간 즉시 판정 작업이 무엇을 붙잡고 있는지 확인한다(DB 없이 작업을 실행하지 않고 넣기만 한다). */
class PhotoEvaluationImmediateDispatcherTests {

    @Test
    void queuedTaskDoesNotHoldTheEventOrItsOversizedPhoto() {
        AtomicReference<Runnable> queued = new AtomicReference<>();
        PhotoEvaluationImmediateDispatcher dispatcher = new PhotoEvaluationImmediateDispatcher(null, queued::set, 3);

        dispatcher.afterCommit(new PhotoEvaluationRequested(UUID.randomUUID(),
                new PhotoObjectReader.PhotoObject("image/jpeg", new byte[] {1, 2, 3, 4})));

        // 람다가 이벤트를 잡으면 상한(3바이트)을 넘은 사진도 대기열에 남는다(PR #59 리뷰).
        assertNotNull(queued.get());
        for (Field captured : queued.get().getClass().getDeclaredFields()) {
            assertFalse(PhotoEvaluationRequested.class.isAssignableFrom(captured.getType()),
                    "queued task must not capture the event: " + captured);
        }
    }
}
