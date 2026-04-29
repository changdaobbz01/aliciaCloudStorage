package com.alicia.cloudstorage.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class CloudStorageApiApplication {

    /**
     * 启动云盘后端 Spring Boot 应用。
     */
    public static void main(String[] args) {
        SpringApplication.run(CloudStorageApiApplication.class, args);
    }
}
