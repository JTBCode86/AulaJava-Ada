package com.melicore.engine;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class CheckoutEngineApplication {

    public static void main(String[] args) {
        SpringApplication.run(CheckoutEngineApplication.class, args);
    }

}