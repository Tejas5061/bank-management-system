package com.bankms;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/** JWT-only authentication, so Spring's default in-memory user (and its generated password) is excluded. */
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@ConfigurationPropertiesScan
public class BankManagementApplication {

    public static void main(String[] args) {
        SpringApplication.run(BankManagementApplication.class, args);
    }
}
