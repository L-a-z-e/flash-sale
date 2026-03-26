package com.flashsale.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.RedisScript;

@Configuration
public class RedisScriptConfig {

    @Bean
    public RedisScript<Long> deductStockScript() {
        return RedisScript.of(new ClassPathResource("scripts/deduct-stock.lua"), Long.class);
    }

}
