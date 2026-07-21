package com.ticket.web.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI 文档元信息（标题 / 版本 / 描述）。
 * 用注解方式设置，确保 Swagger UI 与 /v3/api-docs 稳定展示项目信息，
 * 不依赖 springdoc.info.* 属性绑定。
 */
@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "企业级工单智能分流与协同处理系统 API",
                version = "1.0.0",
                description = "工单 AI 智能分类与路由、Flowable 工作流协同处理、Redisson 分布式锁幂等、SLA 监控、置信度人工复核闭环。"
        )
)
public class OpenApiConfig {
}
