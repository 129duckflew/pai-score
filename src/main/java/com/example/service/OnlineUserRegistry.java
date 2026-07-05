package com.example.service;

import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class OnlineUserRegistry {
    private final ConcurrentHashMap<Long, AtomicInteger> connectionCounts = new ConcurrentHashMap<>();

    public void markOnline(Long userId) {
        if (userId == null) return;
        connectionCounts.computeIfAbsent(userId, ignored -> new AtomicInteger()).incrementAndGet();
    }

    public void markOffline(Long userId) {
        if (userId == null) return;
        connectionCounts.computeIfPresent(userId, (ignored, count) -> count.decrementAndGet() <= 0 ? null : count);
    }

    public boolean isOnline(Long userId) {
        AtomicInteger count = connectionCounts.get(userId);
        return count != null && count.get() > 0;
    }

    public Set<Long> onlineUserIds() {
        return new HashSet<>(connectionCounts.keySet());
    }
}
