package com.dodu.promise.domain;

import java.util.Set;

/**
 * 같은 KST 예정 시작일에는 참가자 화면에 보이는 약속을 1개만 둔다.
 * 그 약속이 인증 미완료나 기술 실패로 끝났을 때만 같은 날짜에 다시 만들 수 있다.
 * 인증 완료·취소·철회·진행 중 약속은 그 날짜를 계속 사용한다.
 */
public final class PromiseDailySlotPolicy {
    private static final Set<String> REPLACEABLE_OUTCOMES = Set.of("AuthIncomplete", "TechFailed");

    private PromiseDailySlotPolicy() { }

    public static boolean isReplaceable(String lifecycleStatus, String finalOutcome) {
        String outcome = finalOutcome != null ? finalOutcome : lifecycleStatus;
        return outcome != null && REPLACEABLE_OUTCOMES.contains(outcome);
    }
}
