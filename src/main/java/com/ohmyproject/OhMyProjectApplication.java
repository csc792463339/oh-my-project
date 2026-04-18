package com.ohmyproject;

import com.ohmyproject.config.AppProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(AppProperties.class)
public class OhMyProjectApplication {
    public static void main(String[] args) {
        SpringApplication.run(OhMyProjectApplication.class, args);
    }
}
