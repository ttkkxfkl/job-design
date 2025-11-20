package com.example.scheduled;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 定时任务系统启动类
 */
@SpringBootApplication
@EnableScheduling
@MapperScan("com.example.scheduled.repository")
public class ScheduledTaskApplication {

    public static void main(String[] args) {
        SpringApplication.run(ScheduledTaskApplication.class, args);
    }
}
