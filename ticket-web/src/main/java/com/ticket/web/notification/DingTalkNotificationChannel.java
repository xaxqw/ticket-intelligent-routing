package com.ticket.web.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 钉钉群机器人通知渠道：可插拔、可配置。
 *  - 默认关闭；在 application.yml 配置 notification.dingtalk.webhook 并 enabled=true 后自动启用。
 *  - 支持“加签”安全模式（配置 secret 时自动追加 timestamp & sign 参数）。
 *  - 发送失败仅告警、绝不影响工单主流程。
 */
@Service
public class DingTalkNotificationChannel implements NotificationChannel {

    private static final Logger log = LoggerFactory.getLogger(DingTalkNotificationChannel.class);

    @Value("${notification.dingtalk.enabled:false}")
    private boolean enabled;

    @Value("${notification.dingtalk.webhook:}")
    private String webhook;

    @Value("${notification.dingtalk.secret:}")
    private String secret;

    private final RestTemplate rest = new RestTemplate();

    @Override
    public boolean isEnabled() {
        return enabled && webhook != null && !webhook.trim().isEmpty();
    }

    @Override
    public void send(NotificationEvent e) {
        try {
            String url = buildSignedUrl();
            String text = buildMarkdown(e);
            Map<String, Object> md = new LinkedHashMap<>();
            md.put("title", "工单智能分流系统通知");
            md.put("text", text);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("msgtype", "markdown");
            body.put("markdown", md);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            rest.postForObject(url, new HttpEntity<>(body, headers), String.class);
            log.info("钉钉通知已发送: 工单{}《{}》", e.getTicketId(), e.getTitle());
        } catch (Exception ex) {
            log.warn("钉钉通知发送失败(不影响业务): {}", ex.getMessage());
        }
    }

    private String buildSignedUrl() {
        if (secret == null || secret.trim().isEmpty()) {
            return webhook;
        }
        long timestamp = System.currentTimeMillis();
        String stringToSign = timestamp + "\n" + secret;
        String sign = hmacSha256(stringToSign, secret);
        String sep = webhook.contains("?") ? "&" : "?";
        try {
            return webhook + sep + "timestamp=" + timestamp
                    + "&sign=" + URLEncoder.encode(sign, "UTF-8");
        } catch (UnsupportedEncodingException ue) {
            return webhook;
        }
    }

    private String hmacSha256(String data, String key) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] raw = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(raw);
        } catch (Exception e) {
            return "";
        }
    }

    private String buildMarkdown(NotificationEvent e) {
        String head;
        switch (e.getType()) {
            case ROUTED:        head = "✅ 工单已智能分流"; break;
            case REJECTED:      head = "⚠️ 工单被驳回"; break;
            case APPROVED:      head = "✅ 工单审批通过"; break;
            case REVIEW_NEEDED: head = "👀 工单待人工复核"; break;
            default:            head = "工单通知";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("### ").append(head).append("\n\n");
        sb.append("> **工单号：** ").append(e.getTicketId()).append("\n");
        sb.append("> **标题：** ").append(e.getTitle() == null ? "" : e.getTitle()).append("\n");
        sb.append("> **详情：** ").append(e.getDetail() == null ? "" : e.getDetail()).append("\n");
        return sb.toString();
    }
}
