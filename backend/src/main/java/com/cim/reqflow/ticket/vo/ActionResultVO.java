package com.cim.reqflow.ticket.vo;

import com.cim.reqflow.ticket.flow.Blocker;

import java.util.List;

/**
 * 流转动作的执行结果。前端据此更新状态标签、倒计时与提示。
 *
 * @author Haijie Xiao
 * @date 2026-11-04
 */
public class ActionResultVO {

    /** 单据 ID */
    private String ticketId;

    /** 动作码 */
    private String action;

    /** 动作前状态 */
    private String fromStatus;

    /** 动作后状态 */
    private String toStatus;

    /** 动作后状态的中文名 */
    private String statusLabel;

    /** 本次动作是否触发了「第 3 次退回转评审」 */
    private Boolean escalated;

    /** 本次是該退回点上的第几次 */
    private Integer returnSeq;

    /** 下一状态的到期时间 */
    private String slaDueAt;

    public String getTicketId() {
        return ticketId;
    }

    public void setTicketId(String ticketId) {
        this.ticketId = ticketId;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getFromStatus() {
        return fromStatus;
    }

    public void setFromStatus(String fromStatus) {
        this.fromStatus = fromStatus;
    }

    public String getToStatus() {
        return toStatus;
    }

    public void setToStatus(String toStatus) {
        this.toStatus = toStatus;
    }

    public String getStatusLabel() {
        return statusLabel;
    }

    public void setStatusLabel(String statusLabel) {
        this.statusLabel = statusLabel;
    }

    public Boolean getEscalated() {
        return escalated;
    }

    public void setEscalated(Boolean escalated) {
        this.escalated = escalated;
    }

    public Integer getReturnSeq() {
        return returnSeq;
    }

    public void setReturnSeq(Integer returnSeq) {
        this.returnSeq = returnSeq;
    }

    public String getSlaDueAt() {
        return slaDueAt;
    }

    public void setSlaDueAt(String slaDueAt) {
        this.slaDueAt = slaDueAt;
    }
}
