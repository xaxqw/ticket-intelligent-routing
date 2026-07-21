package com.ticket.common.constants;

/**
 * 全局常量。流程节点名、动作名、候选人组等集中定义，
 * 避免散落在代码各处的魔法字符串。
 */
public final class TicketConstants {

    private TicketConstants() {
    }

    /** Flowable 流程定义 key */
    public static final String PROCESS_KEY = "ticketProcess";

    /** 流程节点（与 BPMN 中 userTask name 对应） */
    public static final String NODE_SUBMIT = "提交";
    public static final String NODE_AUDIT1 = "初审";
    public static final String NODE_AUDIT2 = "复审";
    public static final String NODE_REVISE = "补充材料";
    public static final String NODE_PAY = "财务打款";
    public static final String NODE_ARCHIVE = "归档";

    /** 动作 */
    public static final String ACTION_SUBMIT = "提交";
    public static final String ACTION_CLAIM = "接单";
    public static final String ACTION_APPROVE = "通过";
    public static final String ACTION_REJECT = "驳回";
    public static final String ACTION_SUSPEND = "挂起";
    public static final String ACTION_RESUME = "恢复";
    public static final String ACTION_ARCHIVE = "归档";

    /** 流程变量名 */
    public static final String VAR_CATEGORY = "category";
    public static final String VAR_REVIEWERS = "reviewers";
    public static final String VAR_APPROVED = "approved";
    public static final String VAR_REJECT_REASON = "rejectReason";
    public static final String VAR_TICKET_ID = "ticketId";

    /** Redis 锁前缀，防重复处理（超卖式并发） */
    public static final String LOCK_TICKET_CLAIM = "lock:ticket:claim:";
    public static final String LOCK_IDEMPOTENT = "lock:idem:";
}
