package com.example.application;

import com.example.application.datalog.*;
import com.example.application.s7.*;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.util.List;

@SpringBootApplication
public class Application {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }

    @Bean
    public LocalFileLogger localFileLogger() {
        LocalFileLogger logger = new LocalFileLogger();
        logger.start();
        return logger;
    }

    @Bean
    public JdbcLogger jdbcLogger() {
        JdbcLogger logger = new JdbcLogger();
        try {
            logger.initialize();
        } catch (Exception e) {
            System.out.println("JDBC Logger not initialized: " + e.getMessage());
        }
        return logger;
    }

    @Bean
    public SupabaseLogger supabaseLogger() {
        SupabaseLogger logger = new SupabaseLogger();
        logger.initialize();
        return logger;
    }
}
