package com.cim.reqflow.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * SLA 配置实体。按「单据类型 + 优先级 + 状态」配时限，改阈值不发版。
 * <p>
 * limit_hours 是工作日折算后的工时（1 个工作日 = 8 小时），
 * 到期时间必须由 {@code SlaCalculator} 按工作日历逐日累减得到，
 * 禁止在别处直接「当前时间 + limit_hours」。
 *
 * @author Haijie Xiao
 * @date 2026-11-04
 */
@TableName("req_sla_config")
public class ReqSlaConfigDO {

    @TableId(type = IdType.INPUT)
    private String id;

    /** 单据类型：REQ / BUG / INC */
    private String ticketType;

    /** 优先级，* 表示不限 */
    private String priority;

    /** 状态码 */
    private String status;

    /** 时限，按工作日折算后的工时；0 表示不计时 */
    private Double limitHours;

    /** 预警阈值，先于到期提醒 */
    private Double warnHours;

    /** 超时动作：NOTIFY 提醒 / ESCALATE 升级 */
    private String overdueAction;

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

    public String getPriority() {
        return priority;
    }

    public void setPriority(String priority) {
        this.priority = priority;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Double getLimitHours() {
        return limitHours;
    }

    public void setLimitHours(Double limitHours) {
        this.limitHours = limitHours;
    }

    public Double getWarnHours() {
        return warnHours;
    }

    public void setWarnHours(Double warnHours) {
        this.warnHours = warnHours;
    }

    public String getOverdueAction() {
        return overdueAction;
    }

    public void setOverdueAction(String overdueAction) {
        this.overdueAction = overdueAction;
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
     * 只输出类型、状态与时限，便于核对配置。
     *
     * @return 关键字段字符串
     */
    @Override
    public String toString() {
        return "ReqSlaConfigDO{ticketType=" + ticketType + ", status=" + status
                + ", limitHours=" + limitHours + "}";
    }
}
