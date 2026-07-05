package com.example.config;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import com.example.service.AdminLogBuffer;

public class AdminLogbackAppender extends AppenderBase<ILoggingEvent> {
    private static volatile AdminLogBuffer buffer;

    public static void setBuffer(AdminLogBuffer adminLogBuffer) {
        buffer = adminLogBuffer;
    }

    @Override
    protected void append(ILoggingEvent event) {
        AdminLogBuffer current = buffer;
        if (current == null) return;
        current.append(event.getLevel().toString(), event.getLoggerName(), event.getFormattedMessage());
    }
}
