package com.example;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.event.EventListener;

import java.time.Duration;

@SpringBootApplication
public class HelloApplication {

    public static void main(String[] args) {
        SpringApplication.run(HelloApplication.class, args);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void logStartupTime(ApplicationReadyEvent event) {
        Duration timeTaken = event.getTimeTaken();
        if (timeTaken != null) {
            System.out.println("Application ready in " + timeTaken.toMillis() + " ms");
        }
    }
}
