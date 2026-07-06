package com.example.service;

import org.slf4j.MDC;

import java.util.UUID;
import java.util.regex.Pattern;

public final class TraceContext {
    public static final String TRACE_ID = "traceId";
    public static final String TRACE_ID_STANDARD = "trace_id";
    public static final String REQUEST_ID = "requestId";
    public static final String USER_ID = "userId";
    public static final String ROOM_CODE = "roomCode";
    public static final String EVENT = "event";
    private static final Pattern TRACE_ID_PATTERN = Pattern.compile("^[0-9a-f]{32}$");

    private TraceContext() {}

    public static String newId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    public static Scope open(String requestId) {
        return open(requestId, null);
    }

    public static Scope open(String requestId, String traceparent) {
        String traceId = traceIdFromTraceparent(traceparent);
        String request = clean(requestId);
        if (traceId == null) traceId = validTraceId(request) ? request : newId();
        if (request == null) request = traceId;
        return new Scope(traceId, request);
    }

    public static String currentTraceId() {
        return MDC.get(TRACE_ID);
    }

    public static void put(String key, Object value) {
        if (value != null) MDC.put(key, String.valueOf(value));
    }

    public static String traceIdFromTraceparent(String traceparent) {
        String value = clean(traceparent);
        if (value == null) return null;
        String[] parts = value.split("-");
        if (parts.length < 4) return null;
        String traceId = parts[1];
        return validTraceId(traceId) ? traceId : null;
    }

    private static String clean(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim();
    }

    private static boolean validTraceId(String value) {
        return value != null && TRACE_ID_PATTERN.matcher(value).matches() && !"00000000000000000000000000000000".equals(value);
    }

    public static final class Scope implements AutoCloseable {
        private Scope(String traceId, String requestId) {
            MDC.put(TRACE_ID, traceId);
            MDC.put(TRACE_ID_STANDARD, traceId);
            MDC.put(REQUEST_ID, requestId);
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
