package com.itheima.delaytask;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Created by 传智播客*黑马程序员.
 */
@SpringBootApplication(scanBasePackages = {"com.itheima.delaytask","com.itheima.cache"})
@EnableScheduling
public class DelayTaskApplication {

    public static void main(String[] args) {
        SpringApplication.run(DelayTaskApplication.class,args);
    }
}
