package com.itheima.supplier.config;

import org.springframework.amqp.core.Queue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Created by 传智播客*黑马程序员.
 */
@Configuration
public class QueueConfig {
    
    @Bean
    public Queue payQueue(){
        return new Queue("pay");
    }
}
