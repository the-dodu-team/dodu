package com.dodu;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class DoduApplication {

	public static void main(String[] args) {
		SpringApplication.run(DoduApplication.class, args);
	}

}
