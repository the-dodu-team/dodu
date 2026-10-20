package com.dodu.common;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 시각에 의존하는 로직이 주입받아 쓰는 시간 공급원. 테스트에서는 고정 Clock으로 바꿉니다. */
@Configuration
public class ClockConfig {

    @Bean
    Clock clock() {
        return Clock.system(ZoneId.of("Asia/Seoul"));
    }
}