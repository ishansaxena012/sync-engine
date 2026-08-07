package com.ishan.syncCanvas;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SyncEngineApplication {
    public static void main(String[] args) {
        SpringApplication.run(SyncEngineApplication.class, args);
    }

    @Bean
    CommandLineRunner dropCheckConstraint(JdbcTemplate jdbcTemplate) {
        return args -> {
            try {
                jdbcTemplate.execute("ALTER TABLE canvas_objects DROP CONSTRAINT IF EXISTS canvas_objects_type_check;");
                System.out.println("Successfully dropped canvas_objects_type_check constraint.");
            } catch (Exception e) {
                System.out.println("Note: canvas_objects_type_check constraint could not be dropped or does not exist: " + e.getMessage());
            }
        };
    }
}