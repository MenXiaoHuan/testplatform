package com.example.platform.ai.config;

import io.micrometer.observation.ObservationRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.ai.deepseek.DeepSeekChatOptions;
import org.springframework.ai.deepseek.api.DeepSeekApi;
import org.springframework.ai.model.tool.DefaultToolCallingManager;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.tool.execution.DefaultToolExecutionExceptionProcessor;
import org.springframework.ai.tool.resolution.SpringBeanToolCallbackResolver;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.retry.support.RetryTemplate;

@Configuration
public class DeepSeekChatModelConfig {

    private static final Logger log = LoggerFactory.getLogger(DeepSeekChatModelConfig.class);

    @Bean
    @ConditionalOnMissingBean
    public DeepSeekApi deepSeekApi(
            @Value("${spring.ai.deepseek.api-key:}") String apiKey,
            @Value("${spring.ai.deepseek.base-url:https://api.deepseek.com}") String baseUrl) {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("spring.ai.deepseek.api-key is empty - DeepSeekChatModel will not function properly");
        } else {
            log.info("Creating DeepSeekApi with baseUrl={}, apiKey length={}", baseUrl, apiKey.length());
        }
        return DeepSeekApi.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .build();
    }

    @Bean
    @ConditionalOnMissingBean
    public DeepSeekChatOptions deepSeekChatOptions(
            @Value("${spring.ai.deepseek.chat.options.model:deepseek-chat}") String model,
            @Value("${spring.ai.deepseek.chat.options.temperature:0.7}") Double temperature) {
        log.info("Creating DeepSeekChatOptions with model={}, temperature={}", model, temperature);
        return DeepSeekChatOptions.builder()
                .model(model)
                .temperature(temperature)
                .build();
    }

    @Bean
    @ConditionalOnMissingBean
    public RetryTemplate retryTemplate() {
        log.info("Creating default RetryTemplate");
        return RetryTemplate.defaultInstance();
    }

    @Bean
    @ConditionalOnMissingBean
    public ObservationRegistry observationRegistry() {
        log.info("Creating NOOP ObservationRegistry");
        return ObservationRegistry.NOOP;
    }

    @Bean
    @ConditionalOnMissingBean
    public ToolCallingManager toolCallingManager(
            ApplicationContext applicationContext,
            ObservationRegistry observationRegistry) {
        log.info("Creating ToolCallingManager with SpringBeanToolCallbackResolver");
        GenericApplicationContext gac = (applicationContext instanceof GenericApplicationContext)
                ? (GenericApplicationContext) applicationContext
                : new GenericApplicationContext(applicationContext);

        var toolCallbackResolver = SpringBeanToolCallbackResolver.builder()
                .applicationContext(gac)
                .build();

        var exceptionProcessor = DefaultToolExecutionExceptionProcessor.builder()
                .alwaysThrow(false)
                .build();

        return DefaultToolCallingManager.builder()
                .observationRegistry(observationRegistry)
                .toolCallbackResolver(toolCallbackResolver)
                .toolExecutionExceptionProcessor(exceptionProcessor)
                .build();
    }

    @Bean
    @ConditionalOnMissingBean
    public ChatModel chatModel(
            DeepSeekApi deepSeekApi,
            DeepSeekChatOptions options,
            ToolCallingManager toolCallingManager,
            RetryTemplate retryTemplate,
            ObservationRegistry observationRegistry) {
        log.info("Creating DeepSeekChatModel bean with all dependencies");
        return DeepSeekChatModel.builder()
                .deepSeekApi(deepSeekApi)
                .defaultOptions(options)
                .toolCallingManager(toolCallingManager)
                .retryTemplate(retryTemplate)
                .observationRegistry(observationRegistry)
                .build();
    }
}