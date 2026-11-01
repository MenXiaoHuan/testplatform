package com.example.platform.evaluation.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** 评测模块自动配置入口。 */
@Configuration
@EnableConfigurationProperties({EvaluationProperties.class, LangfuseProperties.class})
public class EvaluationAutoConfiguration {
}
