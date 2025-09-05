package com.ultracards.webui;

import com.ultracards.cardtopng.CardToPngConverter;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Main application class for the ULTRACARDS web UI.
 * This class is the entry point for running the Spring Boot application.
 */
@SpringBootApplication
@EnableScheduling
@ComponentScan(basePackages = {"com.ultracards.webui", "com.ultracards.gateway"})
public class UltracardsWebApplication {

    /**
     * Main method to run the application.
     * Spring Boot will start an embedded server and deploy the application.
     *
     * @param args Command line arguments
     */
    public static void main(String[] args) {
        SpringApplication.run(UltracardsWebApplication.class, args);
        System.out.println("=================================================");
        System.out.println("  ULTRACARDS Web UI Application");
        System.out.println("=================================================");
        System.out.println("Application started successfully!");
        System.out.println("The following endpoints are available:");
        System.out.println("  - / : Welcome page");
        System.out.println("  - /auth : Login/Sign Up page");
        System.out.println("  - /games : Game Selection page");
        System.out.println("  - /games/create : Create Game page");
        System.out.println("  - /games/join : Join Game page");
        System.out.println("  - /games/play : Game page");
        System.out.println("=================================================");
        System.out.println("Access the application at: http://localhost:5341");
        System.out.println("=================================================");
    }
}