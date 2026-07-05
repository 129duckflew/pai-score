package com.example.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AdminAuthService {
    private static final long SESSION_TTL_SECONDS = 12 * 60 * 60;

    private final String adminPassword;
    private final Map<String, Instant> sessions = new ConcurrentHashMap<>();

    public AdminAuthService(@Value("${admin.password:${ADMIN_PASSWORD:}}") String adminPassword) {
        this.adminPassword = adminPassword;
    }

    public String login(String password) {
        if (adminPassword == null || adminPassword.isBlank()) {
            throw new IllegalStateException("管理员密码未配置");
        }
        if (!adminPassword.equals(password)) {
            throw new IllegalArgumentException("管理员密码错误");
        }
        String token = UUID.randomUUID().toString();
        sessions.put(token, Instant.now().plusSeconds(SESSION_TTL_SECONDS));
        return token;
    }

    public boolean isValid(String token) {
        if (token == null || token.isBlank()) return false;
        Instant expiresAt = sessions.get(token);
        if (expiresAt == null) return false;
        if (expiresAt.isBefore(Instant.now())) {
            sessions.remove(token);
            return false;
        }
        return true;
    }
}
