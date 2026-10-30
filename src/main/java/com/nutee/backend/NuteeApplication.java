package com.nutee.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;

/** Điểm khởi động ứng dụng Spring Boot NUTEE. */
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class NuteeApplication {

    public static void main(String[] args) {
        SpringApplication.run(NuteeApplication.class, args);
    }
}
