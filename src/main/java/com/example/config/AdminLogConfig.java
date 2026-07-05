package com.example.config;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import com.example.service.AdminLogBuffer;
import jakarta.annotation.PostConstruct;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class AdminLogConfig {
    private final AdminLogBuffer adminLogBuffer;

    public AdminLogConfig(AdminLogBuffer adminLogBuffer) {
        this.adminLogBuffer = adminLogBuffer;
    }

    @PostConstruct
    public void attachAppender() {
        AdminLogbackAppender.setBuffer(adminLogBuffer);
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        AdminLogbackAppender appender = new AdminLogbackAppender();
        appender.setContext(context);
        appender.setName("ADMIN_LOG_BUFFER");
        appender.start();
        Logger root = context.getLogger(Logger.ROOT_LOGGER_NAME);
        if (root.getAppender(appender.getName()) == null) {
            root.addAppender(appender);
        }
    }
}
