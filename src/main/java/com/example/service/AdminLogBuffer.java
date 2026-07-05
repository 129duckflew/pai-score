package com.example.service;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

@Service
public class AdminLogBuffer {
    private static final int MAX_LINES = 1000;
    private final ArrayDeque<String> lines = new ArrayDeque<>();

    public synchronized void append(String level, String logger, String message) {
        String line = Instant.now() + " " + level + " " + logger + " - " + message;
        lines.addLast(line);
        while (lines.size() > MAX_LINES) {
            lines.removeFirst();
        }
    }

    public synchronized List<String> recent(int limit) {
        int size = Math.max(1, Math.min(limit, MAX_LINES));
        List<String> all = new ArrayList<>(lines);
        return all.subList(Math.max(0, all.size() - size), all.size());
    }
}
