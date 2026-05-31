package com.kpl.agent;

import com.kpl.agent.config.DotenvConfig;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@MapperScan("com.kpl.agent.mapper")
@EnableScheduling
public class KplAgentApplication {

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(KplAgentApplication.class);
        app.addInitializers(new DotenvConfig());
        app.run(args);
    }
}
