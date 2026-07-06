package com.example.service;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
public class AdminLogBuffer {
    private static final int MAX_LINES = 5000;
    private final ArrayDeque<LogEntry> entries = new ArrayDeque<>();

    public synchronized void append(String level, String logger, String message, String traceId, String requestId, String userId,
                                    String roomCode, String event, String throwable) {
        entries.addLast(new LogEntry(Instant.now().toString(), level, logger, message, traceId, requestId, userId, roomCode, event, throwable));
        while (entries.size() > MAX_LINES) {
            entries.removeFirst();
        }
    }

    public synchronized List<String> recent(int limit) {
        return recentEntries(limit).stream().map(LogEntry::line).toList();
    }

    public synchronized List<LogEntry> recentEntries(int limit) {
        int size = Math.max(1, Math.min(limit, MAX_LINES));
        List<LogEntry> all = new ArrayList<>(entries);
        return all.subList(Math.max(0, all.size() - size), all.size());
    }

    public synchronized List<LogEntry> byTraceId(String traceId, int limit) {
        if (traceId == null || traceId.isBlank()) return recentEntries(limit);
        int size = Math.max(1, Math.min(limit, MAX_LINES));
        String needle = traceId.trim();
        List<LogEntry> matches = entries.stream()
            .filter(entry -> Objects.equals(needle, entry.traceId()) || Objects.equals(needle, entry.requestId()))
            .toList();
        return matches.subList(Math.max(0, matches.size() - size), matches.size());
    }

    public record LogEntry(String timestamp, String level, String logger, String message, String traceId, String requestId,
                           String userId, String roomCode, String event, String throwable) {
        public String line() {
            StringBuilder line = new StringBuilder(timestamp)
                .append(' ').append(level)
                .append(' ').append(logger);
            if (traceId != null && !traceId.isBlank()) line.append(" traceId=").append(traceId);
            if (userId != null && !userId.isBlank()) line.append(" userId=").append(userId);
            if (roomCode != null && !roomCode.isBlank()) line.append(" roomCode=").append(roomCode);
            if (event != null && !event.isBlank()) line.append(" event=").append(event);
            line.append(" - ").append(message);
            if (throwable != null && !throwable.isBlank()) line.append('\n').append(throwable);
            return line.toString();
        }
    }
}
