package com.example.ISDProject;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableCaching
@EnableScheduling // abilita il purge periodico dei refresh token scaduti (RefreshTokenStore)
public class IsdProjectHeliosApplication {

	public static void main(String[] args) {
		SpringApplication.run(IsdProjectHeliosApplication.class, args);
	}

}
