package com.ticket.web.notification;

/**
 * 通知事件：承载一次工单生命周期事件（分流 / 驳回 / 通过 / 待复核）的可读信息。
 * 由 NotificationService 分发给所有已启用的 NotificationChannel。
 */
public class NotificationEvent {

    public enum Type { ROUTED, REJECTED, APPROVED, REVIEW_NEEDED }

    private final Type type;
    private final String ticketId;
    private final String title;
    private final String detail;

    public NotificationEvent(Type type, String ticketId, String title, String detail) {
        this.type = type;
        this.ticketId = ticketId;
        this.title = title;
        this.detail = detail;
    }

    public Type getType() { return type; }
    public String getTicketId() { return ticketId; }
    public String getTitle() { return title; }
    public String getDetail() { return detail; }
}
