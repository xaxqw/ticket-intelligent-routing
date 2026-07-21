package com.ticket.workflow.service;

import org.flowable.task.service.delegate.DelegateTask;
import org.flowable.task.service.delegate.TaskListener;

/**
 * 复审(会签)完成监听器。
 * 每个审批人完成自己的会签任务时触发：若其结论为“驳回”，
 * 则将流程变量 {@code rejected} 置为 true，使多实例提前结束（任一驳回即整体驳回）。
 */
public class ReviewCompleteListener implements TaskListener {

    @Override
    public void notify(DelegateTask task) {
        Object approved = task.getVariable("approved");
        if (approved instanceof Boolean && !((Boolean) approved)) {
            task.setVariable("rejected", true);
        }
    }
}
