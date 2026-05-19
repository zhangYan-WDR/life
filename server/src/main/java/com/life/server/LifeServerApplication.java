package com.life.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class LifeServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(LifeServerApplication.class, args);
    }
}
