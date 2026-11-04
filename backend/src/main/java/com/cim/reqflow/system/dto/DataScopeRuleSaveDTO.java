package com.cim.reqflow.system.dto;

import com.cim.reqflow.common.json.FlagDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

/**
 * 数据范围规则新增 / 修改请求。
 * readable / writable / enabled 前端 Switch 传布尔、表格回显传 0/1，统一由 FlagDeserializer 收敛，
 * 避免 Controller 里再写一遍类型判断。
 *
 * @author Haijie Xiao
 * @date 2026-11-03
 */
public class DataScopeRuleSaveDTO {

    /** 角色编码，修改时不允许变更 */
    private String roleCode;

    /** 单据类型，* 表示全部 */
    private String ticketType;

    /** 数据范围类型 */
    private String scopeType;

    /** 是否可读 */
    @JsonDeserialize(using = FlagDeserializer.class)
    private Integer readable;

    /** 是否可写 */
    @JsonDeserialize(using = FlagDeserializer.class)
    private Integer writable;

    /** 是否启用 */
    @JsonDeserialize(using = FlagDeserializer.class)
    private Integer enabled;

    /** 自定义范围扩展配置，JSON 字符串 */
    private String extraJson;

    /** 备注 */
    private String remark;

    /**
     * 输出关键字段，便于日志排查。
     *
     * @return 对象字符串
     */
    @Override
    public String toString() {
        return "DataScopeRuleSaveDTO{roleCode=" + roleCode
                + ", ticketType=" + ticketType
                + ", scopeType=" + scopeType
                + ", readable=" + readable
                + ", writable=" + writable
                + ", enabled=" + enabled
                + ", extraJson=" + extraJson
                + ", remark=" + remark + "}";
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

    public String getExtraJson() {
        return extraJson;
    }

    public void setExtraJson(String extraJson) {
        this.extraJson = extraJson;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }
}
