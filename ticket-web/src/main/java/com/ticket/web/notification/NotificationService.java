package com.ticket.web.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 通知分发中心：自动收集所有 NotificationChannel 实现（Spring 注入 List），
 * 将工单生命周期事件 fan-out 给每个已启用的渠道。fire-and-forget，单渠道异常不影响其他渠道与主业务。
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    @Autowired(required = false)
    private List<NotificationChannel> channels = new ArrayList<>();

    /** 分发一条事件给所有启用的渠道 */
    public void publish(NotificationEvent e) {
        for (NotificationChannel ch : channels) {
            try {
                if (ch.isEnabled()) {
                    ch.send(e);
                }
            } catch (Exception ex) {
                log.warn("通知渠道 {} 发送异常: {}", ch.getClass().getSimpleName(), ex.getMessage());
            }
        }
    }

    public void notifyRouted(String ticketId, String title, String group) {
        publish(new NotificationEvent(NotificationEvent.Type.ROUTED, ticketId, title,
                "已自动分派至「" + (group == null ? "" : group) + "」"));
    }

    public void notifyReviewNeeded(String ticketId, String title) {
        publish(new NotificationEvent(NotificationEvent.Type.REVIEW_NEEDED, ticketId, title,
                "AI 置信度不足，转人工复核"));
    }

    public void notifyRejected(String ticketId, String title, String reason, String node) {
        String detail = (node == null ? "" : node + "：")
                + (reason == null || reason.trim().isEmpty() ? "（无理由）" : reason);
        publish(new NotificationEvent(NotificationEvent.Type.REJECTED, ticketId, title, detail));
    }

    public void notifyApproved(String ticketId, String title, String node) {
        String detail = (node == null ? "" : "节点「" + node + "」") + "审批通过";
        publish(new NotificationEvent(NotificationEvent.Type.APPROVED, ticketId, title, detail));
    }
}
