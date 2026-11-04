package com.cim.reqflow.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 用户-部门关系表实体。
 * 一人可挂多部门，权限判定以本表为唯一数据源，不看 sys_user.primary_dept_id。
 *
 * @author Haijie Xiao
 * @date 2026-11-03
 */
@TableName("sys_user_dept")
public class SysUserDeptDO {

    @TableId(type = IdType.INPUT)
    private String id;

    /** 用户 ID */
    private String userId;

    /** 部门 ID */
    private String deptId;

    /** 是否主部门：1 是、0 否 */
    private Integer isPrimary;

    /** 任职类型：PRIMARY 主岗、SECONDARY 兼岗 */
    private String postType;

    /** HR 岗位编码 */
    private String hrJobCode;

    /** 生效日期 */
    private String startDate;

    /** 失效日期，为空表示长期有效 */
    private String endDate;

    /** 状态：ACTIVE 生效、DISABLED 失效 */
    private String status;

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
        return "SysUserDeptDO{id=" + id
                + ", userId=" + userId
                + ", deptId=" + deptId
                + ", isPrimary=" + isPrimary
                + ", postType=" + postType
                + ", hrJobCode=" + hrJobCode
                + ", startDate=" + startDate
                + ", endDate=" + endDate
                + ", status=" + status
                + ", createdAt=" + createdAt
                + ", updatedAt=" + updatedAt + "}";
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getDeptId() {
        return deptId;
    }

    public void setDeptId(String deptId) {
        this.deptId = deptId;
    }

    public Integer getIsPrimary() {
        return isPrimary;
    }

    public void setIsPrimary(Integer isPrimary) {
        this.isPrimary = isPrimary;
    }

    public String getPostType() {
        return postType;
    }

    public void setPostType(String postType) {
        this.postType = postType;
    }

    public String getHrJobCode() {
        return hrJobCode;
    }

    public void setHrJobCode(String hrJobCode) {
        this.hrJobCode = hrJobCode;
    }

    public String getStartDate() {
        return startDate;
    }

    public void setStartDate(String startDate) {
        this.startDate = startDate;
    }

    public String getEndDate() {
        return endDate;
    }

    public void setEndDate(String endDate) {
        this.endDate = endDate;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
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
