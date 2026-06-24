package com.burinake.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ai")
public record AiProperties(
        String yoloBaseUrl,
        String vlmBaseUrl
) {
}
