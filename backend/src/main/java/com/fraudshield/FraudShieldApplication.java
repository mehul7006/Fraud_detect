package com.fraudshield;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@org.springframework.data.jpa.repository.config.EnableJpaRepositories(considerNestedRepositories = true)
public class FraudShieldApplication {
    public static void main(String[] args) {
        SpringApplication.run(FraudShieldApplication.class, args);
    }
}
