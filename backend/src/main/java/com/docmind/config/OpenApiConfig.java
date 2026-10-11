package com.docmind.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger 接口文档配置。
 * 启动后访问 http://localhost:8080/swagger-ui/index.html 即可在线调试所有接口。
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI docmindOpenAPI() {
        return new OpenAPI().info(new Info()
                .title("DocMind 智档 API")
                .description("轻量私有化个人文档智能归档检索平台 —— 本地文档解析 / 语义检索 / 自动标签。"
                        + "除注册登录外的接口都需要在请求头携带 Authorization: Bearer <token>")
                .version("v1.0"));
    }
}
