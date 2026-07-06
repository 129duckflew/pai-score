package com.example.config;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.StackTraceElementProxy;
import ch.qos.logback.core.AppenderBase;
import com.example.service.AdminLogBuffer;
import com.example.service.TraceContext;
import org.slf4j.MDC;

public class AdminLogbackAppender extends AppenderBase<ILoggingEvent> {
    private static volatile AdminLogBuffer buffer;

    public static void setBuffer(AdminLogBuffer adminLogBuffer) {
        buffer = adminLogBuffer;
    }

    @Override
    protected void append(ILoggingEvent event) {
        AdminLogBuffer current = buffer;
        if (current == null) return;
        current.append(event.getLevel().toString(), event.getLoggerName(), event.getFormattedMessage(),
            MDC.get(TraceContext.TRACE_ID), MDC.get(TraceContext.REQUEST_ID), MDC.get(TraceContext.USER_ID),
            MDC.get(TraceContext.ROOM_CODE), MDC.get(TraceContext.EVENT), throwable(event.getThrowableProxy()));
    }

    private String throwable(IThrowableProxy throwable) {
        if (throwable == null) return null;
        StringBuilder text = new StringBuilder(throwable.getClassName()).append(": ").append(throwable.getMessage());
        for (StackTraceElementProxy element : throwable.getStackTraceElementProxyArray()) {
            text.append('\n').append("  at ").append(element.getSTEAsString());
        }
        return text.toString();
    }
}
