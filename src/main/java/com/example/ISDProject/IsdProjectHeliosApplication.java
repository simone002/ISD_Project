package com.example.ISDProject;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableCaching
public class IsdProjectHeliosApplication {

	public static void main(String[] args) {
		SpringApplication.run(IsdProjectHeliosApplication.class, args);
	}

}
