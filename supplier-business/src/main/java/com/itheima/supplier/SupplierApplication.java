package com.itheima.supplier;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestTemplate;

/**
 * Created by 传智播客*黑马程序员.
 */
@SpringBootApplication(scanBasePackages = {"com.itheima.supplier","com.itheima.cache"})
@MapperScan("com.itheima.recharge")
public class SupplierApplication {

    public static void main(String[] args) {
        SpringApplication.run(SupplierApplication.class,args);
    }
    
    @Bean
    public RestTemplate restTemplate(){
        return new RestTemplate();
    }
}
