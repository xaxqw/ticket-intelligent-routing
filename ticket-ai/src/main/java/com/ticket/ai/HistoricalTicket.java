package com.ticket.ai;

import lombok.Data;

/**
 * 历史工单样本（用于 few-shot 检索的训练/冷启动数据）。
 * 实际生产中由“已闭环工单”离线同步进向量库；MVP 用 resources 下的种子文件。
 */
@Data
public class HistoricalTicket {

    private String id;
    /** 工单标题 + 描述拼接后的文本 */
    private String text;
    /** 分类标签：硬件类 / 财务类 / 权限类 */
    private String category;
}
