package dev.vedaaxis.api.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("vedaaxis.security")
public record SecurityProperties(
        String jwtSecret,
        long accessTokenMinutes,
        long refreshTokenDays,
        long deviceCodeMinutes) {
}
