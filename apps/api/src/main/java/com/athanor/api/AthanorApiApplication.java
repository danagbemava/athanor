package com.athanor.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class AthanorApiApplication {

	public static void main(String[] args) {
		SpringApplication.run(AthanorApiApplication.class, args);
	}

}
