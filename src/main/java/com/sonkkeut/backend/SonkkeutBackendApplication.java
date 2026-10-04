package com.sonkkeut.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class SonkkeutBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(SonkkeutBackendApplication.class, args);
	}

}
