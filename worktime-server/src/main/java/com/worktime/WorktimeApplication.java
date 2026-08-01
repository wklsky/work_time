package com.worktime;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@MapperScan("com.worktime.mapper")
@EnableAsync
public class WorktimeApplication {

    public static void main(String[] args) {
        SpringApplication.run(WorktimeApplication.class, args);
    }
}
