package com.fairvalue.engine;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class FairvalueEngineApplication {

	public static void main(String[] args) {
		SpringApplication.run(FairvalueEngineApplication.class, args);
	}

}
