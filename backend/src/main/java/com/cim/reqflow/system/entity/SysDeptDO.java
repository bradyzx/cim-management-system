package com.cim.reqflow.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 部门表实体。
 * ancestors 存「从根到父」的 ID 路径串（如 /1/5/，末尾带斜杠），靠它一次算出层级与子树范围，
 * 不用递归查询，同时规避 SQLite 与 Oracle 递归语法不一致的问题。
 *
 * @author Haijie Xiao
 * @date 2026-11-03
 */
@TableName("sys_dept")
public class SysDeptDO {

    @TableId(type = IdType.INPUT)
    private String id;

    /** 部门编码，全局唯一 */
    private String deptCode;

    /** 部门名称 */
    private String deptName;

    /** 上级部门 ID，根部门为空 */
    private String parentId;

    /** 祖先路径串，根部门为 / */
    private String ancestors;

    /** 层级，根部门为 1 */
    private Integer deptLevel;

    /** 部门负责人用户 ID */
    private String leaderUserId;

    /** 部门类型：MIT / PLATFORM / BUSINESS / IT。三方确认定位确认人的唯一依据 */
    private String deptType;

    /** 同层排序号 */
    private Integer sort;

    /** 状态：ACTIVE 启用、DISABLED 停用 */
    private String status;

    /** 备注 */
    private String remark;

    /** 外部系统部门标识 */
    private String externalId;

    /** 同步来源 */
    private String syncSource;

    /** 最近同步时间 */
    private String syncAt;

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
        return "SysDeptDO{id=" + id
                + ", deptCode=" + deptCode
                + ", deptName=" + deptName
                + ", parentId=" + parentId
                + ", ancestors=" + ancestors
                + ", deptLevel=" + deptLevel
                + ", leaderUserId=" + leaderUserId
                + ", sort=" + sort
                + ", status=" + status
                + ", remark=" + remark
                + ", externalId=" + externalId
                + ", syncSource=" + syncSource
                + ", syncAt=" + syncAt
                + ", createdAt=" + createdAt
                + ", updatedAt=" + updatedAt + "}";
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getDeptCode() {
        return deptCode;
    }

    public void setDeptCode(String deptCode) {
        this.deptCode = deptCode;
    }

    public String getDeptName() {
        return deptName;
    }

    public void setDeptName(String deptName) {
        this.deptName = deptName;
    }

    public String getParentId() {
        return parentId;
    }

    public void setParentId(String parentId) {
        this.parentId = parentId;
    }

    public String getAncestors() {
        return ancestors;
    }

    public void setAncestors(String ancestors) {
        this.ancestors = ancestors;
    }

    public Integer getDeptLevel() {
        return deptLevel;
    }

    public void setDeptLevel(Integer deptLevel) {
        this.deptLevel = deptLevel;
    }

    public String getLeaderUserId() {
        return leaderUserId;
    }

    public void setLeaderUserId(String leaderUserId) {
        this.leaderUserId = leaderUserId;
    }

    public String getDeptType() {
        return deptType;
    }

    public void setDeptType(String deptType) {
        this.deptType = deptType;
    }

    public Integer getSort() {
        return sort;
    }

    public void setSort(Integer sort) {
        this.sort = sort;
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

    public String getExternalId() {
        return externalId;
    }

    public void setExternalId(String externalId) {
        this.externalId = externalId;
    }

    public String getSyncSource() {
        return syncSource;
    }

    public void setSyncSource(String syncSource) {
        this.syncSource = syncSource;
    }

    public String getSyncAt() {
        return syncAt;
    }

    public void setSyncAt(String syncAt) {
        this.syncAt = syncAt;
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
