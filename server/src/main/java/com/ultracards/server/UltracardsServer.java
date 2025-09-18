package com.ultracards.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class UltracardsServer {
    public static void main(String[] args) {
        SpringApplication.run(UltracardsServer.class, args);
    }
}
