package com.theotech;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class TheoTechApplication {

    public static void main(String[] args) {
        SpringApplication.run(TheoTechApplication.class, args);
    }
}
