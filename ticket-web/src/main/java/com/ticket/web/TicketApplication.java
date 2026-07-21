package com.ticket.web;

import com.ticket.common.util.SnowflakeId;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * 应用入口。scanBasePackages 覆盖所有子模块包（com.ticket），
 * 确保 ticket-ai / ticket-workflow 中带 Spring 注解的 Bean 被扫描到。
 * 实体分布在 ticket-common（com.ticket.common.domain），故需显式 @EntityScan / @EnableJpaRepositories。
 */
@SpringBootApplication(scanBasePackages = "com.ticket", exclude = RedisRepositoriesAutoConfiguration.class)
@EntityScan("com.ticket")
@EnableJpaRepositories("com.ticket")
public class TicketApplication {

    public static void main(String[] args) {
        SpringApplication.run(TicketApplication.class, args);
    }

    @Bean
    public SnowflakeId snowflakeId() {
        return SnowflakeId.auto();
    }
}
