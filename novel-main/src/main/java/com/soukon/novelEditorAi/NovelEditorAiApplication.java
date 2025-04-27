package com.soukon.novelEditorAi;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.soukon.novelEditorAi.mapper")
public class NovelEditorAiApplication {

    public static void main(String[] args) {
        SpringApplication.run(NovelEditorAiApplication.class, args);
    }

}
