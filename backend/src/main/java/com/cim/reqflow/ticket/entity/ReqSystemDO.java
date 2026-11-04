package com.cim.reqflow.ticket.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 系统实体，需求归属的第二层。
 * <p>
 * system_code 只在项目内唯一，不同项目可以各有一个 MES。
 * 单据上另存了一份 system_code 冗余快照，那是给报表与导出用的，业务判断一律走 system_id。
 *
 * @author Haijie Xiao
 * @date 2026-11-04
 */
@TableName("req_system")
public class ReqSystemDO {

    @TableId(type = IdType.INPUT)
    private String id;

    /** 所属项目 */
    private String projectId;

    /** 系统编码，项目内唯一 */
    private String systemCode;

    /** 系统名称 */
    private String systemName;

    /** 类型：PURCHASED 外购 / INHOUSE 自研 / RUNNING 在运 */
    private String systemType;

    /** 系统负责人 */
    private String ownerId;

    /** 状态：BUILDING / LIVE / OFFLINE */
    private String status;

    /** 备注 */
    private String remark;

    /** 排序号 */
    private Integer sortNo;

    /** 创建时间 */
    private String createdAt;

    /** 更新时间 */
    private String updatedAt;

    /** 是否删除：1 是 */
    private Integer deleted;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getProjectId() {
        return projectId;
    }

    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }

    public String getSystemCode() {
        return systemCode;
    }

    public void setSystemCode(String systemCode) {
        this.systemCode = systemCode;
    }

    public String getSystemName() {
        return systemName;
    }

    public void setSystemName(String systemName) {
        this.systemName = systemName;
    }

    public String getSystemType() {
        return systemType;
    }

    public void setSystemType(String systemType) {
        this.systemType = systemType;
    }

    public String getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(String ownerId) {
        this.ownerId = ownerId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public Integer getSortNo() {
        return sortNo;
    }

    public void setSortNo(Integer sortNo) {
        this.sortNo = sortNo;
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

    public Integer getDeleted() {
        return deleted;
    }

    public void setDeleted(Integer deleted) {
        this.deleted = deleted;
    }

    /**
     * 只输出编码与名称，便于日志核对。
     *
     * @return 关键字段字符串
     */
    @Override
    public String toString() {
        return "ReqSystemDO{systemCode=" + systemCode + ", systemName=" + systemName + "}";
    }
}
