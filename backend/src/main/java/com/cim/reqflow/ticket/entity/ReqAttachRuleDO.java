package com.cim.reqflow.ticket.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 附件必填规则。按「单据类型 + 到达状态」配置，改规则不发版。
 *
 * @author Haijie Xiao
 * @date 2026-11-04
 */
@TableName("req_attach_rule")
public class ReqAttachRuleDO {

    @TableId(type = IdType.INPUT)
    private String id;

    /** 单据类型：REQ / BUG / INC / * */
    private String ticketType;

    /** 目标状态（to_status）；* 表示所有状态 */
    private String status;

    /** 必传的交付物类型 */
    private String attachType;

    /** 至少几份 */
    private Integer minCount;

    /** 是否强制：1 不满足不许流转、0 仅提示 */
    private Integer required;

    /** 是否启用：1 是 */
    private Integer enabled;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private String createdAt;

    /** 更新时间 */
    private String updatedAt;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTicketType() {
        return ticketType;
    }

    public void setTicketType(String ticketType) {
        this.ticketType = ticketType;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getAttachType() {
        return attachType;
    }

    public void setAttachType(String attachType) {
        this.attachType = attachType;
    }

    public Integer getMinCount() {
        return minCount;
    }

    public void setMinCount(Integer minCount) {
        this.minCount = minCount;
    }

    public Integer getRequired() {
        return required;
    }

    public void setRequired(Integer required) {
        this.required = required;
    }

    public Integer getEnabled() {
        return enabled;
    }

    public void setEnabled(Integer enabled) {
        this.enabled = enabled;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    public String getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(String updatedAt) {
        this.updatedAt = updatedAt;
    }

    /**
     * 只输出关键字段，避免长文本（备注、正文）把日志刷屏。
     * 手册要求 POJO 必须写 toString，裁剪范围在此说明。
     *
     * @return 关键字段字符串
     */
    @Override
    public String toString() {
        return "ReqAttachRuleDO{" + "ticketType=" + ticketType + ", " + "status=" + status + ", " + "attachType=" + attachType + ", " + "required=" + required + "}";
    }
}
