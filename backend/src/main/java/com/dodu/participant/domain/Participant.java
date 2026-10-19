package com.dodu.participant.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * 참가자의 상태. 조회한 시점의 값을 담은 불변 객체이고, 값을 바꾸는 일은 repository의 SQL이 합니다.
 *
 * <p>참가자용 API는 요청 경로·본문의 참가자 ID를 믿지 않고 항상 세션이 가리키는 이 객체의 {@code id}만 사용합니다
 * (계약 §4). 자원을 조회할 때는 "이 참가자의 소유인가"를 조건에 포함해야 합니다.
 *
 * @param id 참가자 ID
 * @param withdrawnAt 철회 적용 시각. 철회하지 않았으면 null
 * @param accessDisabledAt 접근 중단 시각. 중단되지 않았으면 null
 * @param onboardingCompletedAt 온보딩 완료 시각. 완료 전이면 null
 */
public record Participant(
        UUID id,
        Instant withdrawnAt,
        Instant accessDisabledAt,
        Instant onboardingCompletedAt) {

    public Participant {
        Objects.requireNonNull(id, "id must not be null");
    }

    public boolean isWithdrawn() {
        return withdrawnAt != null;
    }

    public boolean isAccessDisabled() {
        return accessDisabledAt != null;
    }

    /** 철회했거나 접근이 중단돼 더 이상 이용할 수 없는 상태인지. */
    public boolean isAccessBlocked() {
        return isWithdrawn() || isAccessDisabled();
    }

    public boolean onboardingCompleted() {
        return onboardingCompletedAt != null;
    }
}