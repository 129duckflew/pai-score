package com.example.service;

import org.slf4j.MDC;

import java.util.UUID;

public final class TraceContext {
    public static final String TRACE_ID = "traceId";
    public static final String REQUEST_ID = "requestId";
    public static final String USER_ID = "userId";
    public static final String ROOM_CODE = "roomCode";
    public static final String EVENT = "event";

    private TraceContext() {}

    public static String newId() {
        return UUID.randomUUID().toString();
    }

    public static Scope open(String requestId) {
        String id = clean(requestId);
        if (id == null) id = newId();
        return new Scope(id);
    }

    public static String currentTraceId() {
        return MDC.get(TRACE_ID);
    }

    public static void put(String key, Object value) {
        if (value != null) MDC.put(key, String.valueOf(value));
    }

    private static String clean(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim();
    }

    public static final class Scope implements AutoCloseable {
        private Scope(String id) {
            MDC.put(TRACE_ID, id);
            MDC.put(REQUEST_ID, id);
        }

        public String id() {
            return MDC.get(TRACE_ID);
        }

        @Override
        public void close() {
            MDC.clear();
        }
    }
}
