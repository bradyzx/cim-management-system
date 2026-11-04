package com.cim.reqflow.ticket.vo;

/**
 * 流转时间线的一条记录。
 * <p>
 * 状态码与动作码都翻译成中文再返回，前端不再查字典：
 * 时间线是审计视图，任何一处显示成英文码都算事故。
 *
 * @author Haijie Xiao
 * @date 2026-11-04
 */
public class TimelineItemVO {

    /** 记录 ID */
    private String id;

    /** 动作码 */
    private String action;

    /** 动作中文名 */
    private String actionLabel;

    /** 动作前状态码 */
    private String fromStatus;

    /** 动作前状态中文名 */
    private String fromStatusLabel;

    /** 动作后状态码 */
    private String toStatus;

    /** 动作后状态中文名 */
    private String toStatusLabel;

    /** 操作人姓名；系统动作为「系统」 */
    private String operatorName;

    /** 退回原因码 */
    private String reasonCode;

    /** 退回原因中文名 */
    private String reasonLabel;

    /** 附加说明 */
    private String remark;

    /** 该退回点上的第几次 */
    private Integer returnSeq;

    /** 发生时间 */
    private String createdAt;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getActionLabel() {
        return actionLabel;
    }

    public void setActionLabel(String actionLabel) {
        this.actionLabel = actionLabel;
    }

    public String getFromStatus() {
        return fromStatus;
    }

    public void setFromStatus(String fromStatus) {
        this.fromStatus = fromStatus;
    }

    public String getFromStatusLabel() {
        return fromStatusLabel;
    }

    public void setFromStatusLabel(String fromStatusLabel) {
        this.fromStatusLabel = fromStatusLabel;
    }

    public String getToStatus() {
        return toStatus;
    }

    public void setToStatus(String toStatus) {
        this.toStatus = toStatus;
    }

    public String getToStatusLabel() {
        return toStatusLabel;
    }

    public void setToStatusLabel(String toStatusLabel) {
        this.toStatusLabel = toStatusLabel;
    }

    public String getOperatorName() {
        return operatorName;
    }

    public void setOperatorName(String operatorName) {
        this.operatorName = operatorName;
    }

    public String getReasonCode() {
        return reasonCode;
    }

    public void setReasonCode(String reasonCode) {
        this.reasonCode = reasonCode;
    }

    public String getReasonLabel() {
        return reasonLabel;
    }

    public void setReasonLabel(String reasonLabel) {
        this.reasonLabel = reasonLabel;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public Integer getReturnSeq() {
        return returnSeq;
    }

    public void setReturnSeq(Integer returnSeq) {
        this.returnSeq = returnSeq;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    /**
     * 只输出关键字段。
     *
     * @return 关键字段字符串
     */
    @Override
    public String toString() {
        return "TimelineItemVO{" + "id=" + id + ", " + "action=" + action + ", " + "actionLabel=" + actionLabel + "}";
    }
}
