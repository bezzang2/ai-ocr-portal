package com.agilesoda;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class AiOcrApplication{

	public static void main(String[] args) {
		SpringApplication.run(AiOcrApplication.class, args);
	}

}
