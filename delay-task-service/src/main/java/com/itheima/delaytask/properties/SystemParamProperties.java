package com.itheima.delaytask.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "delaytask")
public class SystemParamProperties {
    private int preLoad;
}
