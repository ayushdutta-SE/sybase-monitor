package com.sybmon;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SybmonApplication {
    public static void main(String[] args) {
        SpringApplication.run(SybmonApplication.class, args);
    }
}
