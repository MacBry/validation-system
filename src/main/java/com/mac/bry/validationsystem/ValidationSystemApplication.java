package com.mac.bry.validationsystem;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ValidationSystemApplication {

    public static void main(String[] args) {
        SpringApplication.run(ValidationSystemApplication.class, args);
    }
}
