package com.cim.reqflow.ticket.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 流转时间线实体，只追加不修改。每条记录对应一次动作，是退回计次与追溯的唯一依据。
 *
 * @author Haijie Xiao
 * @date 2026-11-04
 */
@TableName("req_flow_log")
public class ReqFlowLogDO {

    @TableId(type = IdType.INPUT)
    private String id;

    /** 所属单据 */
    private String ticketId;

    /** 动作前的状态 */
    private String fromStatus;

    /** 动作后的状态；点检通过这类不换状态的动作，两者相同 */
    private String toStatus;

    /** 动作码，对应 FlowAction */
    private String action;

    /** 操作人；系统动作为空 */
    private String operatorId;

    /** 操作人部门快照 */
    private String operatorDeptId;

    /** 原因码，退回与驳回必填 */
    private String reasonCode;

    /** 附加说明 */
    private String remark;

    /** 退回点码，非退活动作为空 */
    private String returnPoint;

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

    public String getTicketId() {
        return ticketId;
    }

    public void setTicketId(String ticketId) {
        this.ticketId = ticketId;
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

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getOperatorId() {
        return operatorId;
    }

    public void setOperatorId(String operatorId) {
        this.operatorId = operatorId;
    }

    public String getOperatorDeptId() {
        return operatorDeptId;
    }

    public void setOperatorDeptId(String operatorDeptId) {
        this.operatorDeptId = operatorDeptId;
    }

    public String getReasonCode() {
        return reasonCode;
    }

    public void setReasonCode(String reasonCode) {
        this.reasonCode = reasonCode;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public String getReturnPoint() {
        return returnPoint;
    }

    public void setReturnPoint(String returnPoint) {
        this.returnPoint = returnPoint;
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
     * 只输出关键字段，避免长文本（备注、正文）把日志刷屏。
     * 手册要求 POJO 必须写 toString，裁剪范围在此说明。
     *
     * @return 关键字段字符串
     */
    @Override
    public String toString() {
        return "ReqFlowLogDO{" + "ticketId=" + ticketId + ", " + "action=" + action + ", " + "fromStatus=" + fromStatus + ", " + "toStatus=" + toStatus + "}";
    }
}
