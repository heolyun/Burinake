package com.burinake.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "azure.openai")
public record AzureOpenAiProperties(
        String endpoint,
        String apiKey,
        String deploymentName,
        String apiVersion,
        Integer maxCompletionTokens
) {
}
