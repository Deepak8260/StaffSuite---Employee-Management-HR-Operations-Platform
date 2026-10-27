package com.staffsuite.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "staffsuite")
public record AppProperties(Cors cors, Jwt jwt, Storage storage, Bootstrap bootstrap, PasswordReset passwordReset) {

    public record Cors(List<String> allowedOrigins) {
    }

    public record Jwt(String secret, long expiryMs) {
    }

    public record Storage(String path) {
    }

    public record Bootstrap(String adminEmail, String adminPassword) {
    }

    public record PasswordReset(long expiryMinutes) {
    }
}
