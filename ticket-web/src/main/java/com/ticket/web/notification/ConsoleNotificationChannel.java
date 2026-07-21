package com.ticket.web.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 控制台 / 日志通知渠道：默认启用，零依赖、离线可演示。
 * 任何工单事件都会以结构化日志输出，便于演示与排查。
 */
@Service
public class ConsoleNotificationChannel implements NotificationChannel {

    private static final Logger log = LoggerFactory.getLogger(ConsoleNotificationChannel.class);

    @Value("${notification.console.enabled:true}")
    private boolean enabled;

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void send(NotificationEvent e) {
        String line = String.format("[工单通知|%s] 工单%s《%s》 %s",
                e.getType(), e.getTicketId(), e.getTitle(),
                e.getDetail() == null ? "" : e.getDetail());
        log.info(line);
    }
}
