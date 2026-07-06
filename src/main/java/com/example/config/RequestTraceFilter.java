package com.example.config;

import com.example.service.TraceContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class RequestTraceFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(RequestTraceFilter.class);
    private static final String REQUEST_ID_HEADER = "X-Request-Id";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        long started = System.currentTimeMillis();
        try (TraceContext.Scope scope = TraceContext.open(request.getHeader(REQUEST_ID_HEADER))) {
            response.setHeader(REQUEST_ID_HEADER, scope.id());
            TraceContext.put("method", request.getMethod());
            TraceContext.put("path", request.getRequestURI());
            log.info("HTTP request started method={} path={}", request.getMethod(), request.getRequestURI());
            try {
                filterChain.doFilter(request, response);
            } finally {
                long durationMs = System.currentTimeMillis() - started;
                log.info("HTTP request completed method={} path={} status={} durationMs={}",
                    request.getMethod(), request.getRequestURI(), response.getStatus(), durationMs);
            }
        }
    }
}
