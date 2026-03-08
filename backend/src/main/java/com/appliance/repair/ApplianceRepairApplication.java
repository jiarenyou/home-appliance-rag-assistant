package com.appliance.repair;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class ApplianceRepairApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApplianceRepairApplication.class, args);
    }
}
