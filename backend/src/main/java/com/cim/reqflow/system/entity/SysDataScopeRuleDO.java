package com.cim.reqflow.system.entity;

import com.cim.reqflow.common.enums.DataScopeTypeEnum;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 数据范围规则表实体。一条规则 = 角色 × 单据类型 的可见 / 可写范围。
 * 唯一键为 (roleCode, ticketType)；ticketType 为 * 表示对该角色的兜底规则。
 * 本表没有逻辑删除字段，删除是物理删（规则数量少、无审计要求）。
 *
 * @author Haijie Xiao
 * @date 2026-11-03
 */
@TableName("sys_data_scope_rule")
public class SysDataScopeRuleDO {

    @TableId(type = IdType.INPUT)
    private String id;

    /** 角色编码 */
    private String roleCode;

    /** 单据类型，* 表示全部 */
    private String ticketType;

    /** 数据范围类型，取值见 DataScopeTypeEnum */
    private String scopeType;

    /** 自定义范围扩展配置，JSON 字符串 */
    private String extraJson;

    /** 是否可读：1 是、0 否 */
    private Integer readable;

    /** 是否可写：1 是、0 否 */
    private Integer writable;

    /** 是否启用：1 是、0 否 */
    private Integer enabled;

    /** 备注 */
    private String remark;

    /** 创建时间，格式 yyyy-MM-dd HH:mm:ss */
    private String createdAt;

    /** 更新时间，格式 yyyy-MM-dd HH:mm:ss */
    private String updatedAt;

    /**
     * 输出关键字段，便于日志排查。
     *
     * @return 对象字符串
     */
    @Override
    public String toString() {
        return "SysDataScopeRuleDO{id=" + id
                + ", roleCode=" + roleCode
                + ", ticketType=" + ticketType
                + ", scopeType=" + scopeType
                + ", extraJson=" + extraJson
                + ", readable=" + readable
                + ", writable=" + writable
                + ", enabled=" + enabled
                + ", remark=" + remark
                + ", createdAt=" + createdAt
                + ", updatedAt=" + updatedAt + "}";
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getRoleCode() {
        return roleCode;
    }

    public void setRoleCode(String roleCode) {
        this.roleCode = roleCode;
    }

    public String getTicketType() {
        return ticketType;
    }

    public void setTicketType(String ticketType) {
        this.ticketType = ticketType;
    }

    public String getScopeType() {
        return scopeType;
    }

    public void setScopeType(String scopeType) {
        this.scopeType = scopeType;
    }

    public String getExtraJson() {
        return extraJson;
    }

    public void setExtraJson(String extraJson) {
        this.extraJson = extraJson;
    }

    public Integer getReadable() {
        return readable;
    }

    public void setReadable(Integer readable) {
        this.readable = readable;
    }

    public Integer getWritable() {
        return writable;
    }

    public void setWritable(Integer writable) {
        this.writable = writable;
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
}
