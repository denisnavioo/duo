package com.pareja.duo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class DuoApplication {

	public static void main(String[] args) {
		SpringApplication.run(DuoApplication.class, args);
	}

}
