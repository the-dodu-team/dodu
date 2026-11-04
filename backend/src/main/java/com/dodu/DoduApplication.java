package com.dodu;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class DoduApplication {

	public static void main(String[] args) {
        try {
            SpringApplication.run(DoduApplication.class, args);
        } catch (RuntimeException failure) {
            if (java.util.Arrays.asList(args).contains("reset-test-data")) {
                System.out.println("RESET_FAILED stage=STARTUP code=" + com.dodu.operator.cli.ResetFailureCode.of(failure));
                System.exit(3);
                return;
            }
            throw failure;
        }
	}

}
