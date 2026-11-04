package com.dodu.common;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@Profile("!operator")
@EnableScheduling
public class SchedulingConfig { }
