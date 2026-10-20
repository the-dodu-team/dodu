package com.dodu.common.api;

import java.time.Clock;
import java.time.Instant;
import java.time.format.DateTimeFormatter;

/** 응답에 넣는 시각 표현. 서버 시간대의 오프셋(+09:00)을 포함하고 초 단위를 항상 표시합니다. */
public final class ApiTime {

    private ApiTime() {
    }

    public static String format(Instant instant, Clock clock) {
        return DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(instant.atZone(clock.getZone()));
    }

    public static String now(Clock clock) {
        return format(Instant.now(clock), clock);
    }
}