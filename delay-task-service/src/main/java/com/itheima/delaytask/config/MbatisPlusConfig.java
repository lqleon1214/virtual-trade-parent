package com.itheima.delaytask.config;

import com.baomidou.mybatisplus.extension.plugins.OptimisticLockerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Created by 传智播客*黑马程序员.
 */
@Configuration
public class MbatisPlusConfig {

    /**
     * mybatis-plus乐观锁支持
     * @return
     */
    @Bean
    public OptimisticLockerInterceptor optimisticLockerInterceptor(){
        return new OptimisticLockerInterceptor();
    }
}
