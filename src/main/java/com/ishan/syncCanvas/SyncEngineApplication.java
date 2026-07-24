package com.ishan.syncCanvas;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SyncEngineApplication {

    public static void main(String[] args) {
        SpringApplication.run(SyncEngineApplication.class, args);
    }

}
