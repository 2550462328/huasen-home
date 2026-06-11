package com.huasen;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class HuasenApplication {

    public static void main(String[] args) {
        SpringApplication.run(HuasenApplication.class, args);
    }
}
