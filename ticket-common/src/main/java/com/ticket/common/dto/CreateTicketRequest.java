package com.ticket.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

/**
 * 创建工单请求。idempotencyKey 由客户端生成并透传，
 * 用于防御“重复提交/重试建单”（与 Redis 分布式锁配合）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateTicketRequest {

    @NotBlank(message = "标题不能为空")
    @Size(max = 128)
    private String title;

    @Size(max = 2000)
    private String description = "";

    /** 客户端生成的幂等键（UUID），缺省时由服务端补 */
    private String idempotencyKey;

    /** 优先级 1低 2中 3高，缺省 2 */
    private Integer priority = 2;

    /**
     * 可选：人工指定的分类。为空时由 AI 语义分派。
     * 体现“AI 建议 + 人工覆盖”的人机协同兜底。
     */
    private String category;

    /** 期望 SLA 时长（小时），用于仪表盘超时预警，缺省 24 */
    private Integer slaHours = 24;
}
