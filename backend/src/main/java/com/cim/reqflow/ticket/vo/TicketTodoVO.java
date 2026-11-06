package com.cim.reqflow.ticket.vo;

import java.util.List;

/**
 * 我的待办一行：单据摘要 + 「轮到我做什么」。
 * <p>
 * 继承列表行而不是另建一套字段：单号、状态、优先级、提出人这些列待办也要显示，
 * 各写一遍的话，同一个状态码在两处翻译成不同中文也没人会发现。
 * <p>
 * 契约 `11` §4.3 只多两个字段（`waitingHours` / `dueActions`），这里另有
 * `blockedActions` —— 见 {@code TicketTodoService} 的口径说明。
 *
 * @author Haijie Xiao
 * @date 2026-11-05
 */
public class TicketTodoVO extends TicketListVO {

    /** 停在当前环节的小时数，自然小时。排序与「等了多久」都用它 */
    private Double waitingHours;

    /** 停留时长的可读形式，如「3 天 2 小时」 */
    private String waitingText;

    /** 现在就能点的动作码 */
    private List<String> dueActions;

    /** 现在就能点的动作中文名 */
    private List<String> dueActionLabels;

    /** 归属是我、但前置条件没满足的动作码（缺附件、缺字段等） */
    private List<String> blockedActions;

    /** 上述动作的中文名 */
    private List<String> blockedActionLabels;

    /** 被挡的原因，一条一句，直接展示给用户 */
    private List<String> blockedReasons;

    /** 一句「待你做什么」，取第一个可执行动作，没有则取第一个被挡动作 */
    private String todoHint;

    /**
     * 待办性质：`DUE` 现在就能办，`BLOCKED` 需要先补材料。
     * <p>
     * 由后端定，不由前端拿 `dueActions` 是否为空去推 —— 因为「能把单往下推的动作」
     * 和「任何能点的动作」不是一回事：一张单上「退回重生成」「判定重复关闭」都可点，
     * 但对用户来说它仍然是「缺材料待补」。口径放前端必然对不上。
     */
    private String todoType;

    /** 当前环节是否已经超过应完成时间 */
    private Boolean slaOverdue;

    public Double getWaitingHours() {
        return waitingHours;
    }

    public void setWaitingHours(Double waitingHours) {
        this.waitingHours = waitingHours;
    }

    public String getWaitingText() {
        return waitingText;
    }

    public void setWaitingText(String waitingText) {
        this.waitingText = waitingText;
    }

    public List<String> getDueActions() {
        return dueActions;
    }

    public void setDueActions(List<String> dueActions) {
        this.dueActions = dueActions;
    }

    public List<String> getDueActionLabels() {
        return dueActionLabels;
    }

    public void setDueActionLabels(List<String> dueActionLabels) {
        this.dueActionLabels = dueActionLabels;
    }

    public List<String> getBlockedActions() {
        return blockedActions;
    }

    public void setBlockedActions(List<String> blockedActions) {
        this.blockedActions = blockedActions;
    }

    public List<String> getBlockedActionLabels() {
        return blockedActionLabels;
    }

    public void setBlockedActionLabels(List<String> blockedActionLabels) {
        this.blockedActionLabels = blockedActionLabels;
    }

    public List<String> getBlockedReasons() {
        return blockedReasons;
    }

    public void setBlockedReasons(List<String> blockedReasons) {
        this.blockedReasons = blockedReasons;
    }

    public String getTodoHint() {
        return todoHint;
    }

    public void setTodoHint(String todoHint) {
        this.todoHint = todoHint;
    }

    public String getTodoType() {
        return todoType;
    }

    public void setTodoType(String todoType) {
        this.todoType = todoType;
    }

    public Boolean getSlaOverdue() {
        return slaOverdue;
    }

    public void setSlaOverdue(Boolean slaOverdue) {
        this.slaOverdue = slaOverdue;
    }
}
