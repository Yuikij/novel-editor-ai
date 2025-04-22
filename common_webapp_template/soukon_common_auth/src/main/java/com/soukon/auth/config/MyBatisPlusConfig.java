package com.soukon.auth.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@MapperScan("com.soukon.auth.mapper")
public class MyBatisPlusConfig {
}
