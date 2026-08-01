package com.marketpulse.refdata;

import com.marketpulse.refdata.config.NseProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(NseProperties.class)
public class RefDataApplication {

    public static void main(String[] args) {
        SpringApplication.run(RefDataApplication.class, args);
    }
}
