package com.ultracards.ui.webui;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@ComponentScan(basePackages = {"com.ultracards.ui.webui", "com.ultracards.gateway"})
public class WebUiApplication {
    public static void main(String[] args) {
		SpringApplication.run(WebUiApplication.class, args);
	}
}
