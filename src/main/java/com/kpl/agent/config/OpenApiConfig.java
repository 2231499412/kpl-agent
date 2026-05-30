package com.kpl.agent.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI 配置：启动后通过 /swagger-ui.html 查看和调试接口。
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI kplOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("KPL Data Agent API")
                        .description("KPL 电竞数据同步、查询和自然语言 Agent 接口")
                        .version("1.0.0")
                        .license(new License().name("Internal MVP")));
    }
}
