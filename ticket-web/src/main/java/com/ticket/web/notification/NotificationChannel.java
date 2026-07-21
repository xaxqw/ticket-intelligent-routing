package com.ticket.web.notification;

/**
 * 通知渠道接口：可插拔。新增渠道（邮件 / 飞书 / 企业微信）只需实现本接口并注册为 Spring Bean，
 * NotificationService 会自动发现并分发，业务代码零改动。
 */
public interface NotificationChannel {

    /** 是否启用（如钉钉未配置 webhook 则返回 false，自动跳过） */
    boolean isEnabled();

    /** 发送一条通知事件（实现内部必须吞掉异常，绝不影响主业务流程） */
    void send(NotificationEvent event);
}
