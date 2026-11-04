package com.cim.reqflow.ticket.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 项目实体，需求归属的第一层。
 * <p>
 * project_code 是附件存储路径的第一级，改名等于让历史附件全部失联，
 * 因此业务上不允许修改，只能新建项目。
 *
 * @author Haijie Xiao
 * @date 2026-11-04
 */
@TableName("req_project")
public class ReqProjectDO {

    @TableId(type = IdType.INPUT)
    private String id;

    /** 项目编码，全局唯一，附件目录第一级就是它。一经使用不再改 */
    private String projectCode;

    /** 项目名称 */
    private String projectName;

    /** 项目类型：PILOT 中试 / MASS 量产 / OTHER */
    private String projectType;

    /** 状态：PLANNING / ACTIVE / SUSPENDED / CLOSED */
    private String status;

    /** 项目负责人 */
    private String ownerId;

    /** 归口部门 */
    private String deptId;

    /** 开始日期 YYYY-MM-DD */
    private String startDate;

    /** 结束日期 YYYY-MM-DD */
    private String endDate;

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

    public String getProjectCode() {
        return projectCode;
    }

    public void setProjectCode(String projectCode) {
        this.projectCode = projectCode;
    }

    public String getProjectName() {
        return projectName;
    }

    public void setProjectName(String projectName) {
        this.projectName = projectName;
    }

    public String getProjectType() {
        return projectType;
    }

    public void setProjectType(String projectType) {
        this.projectType = projectType;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(String ownerId) {
        this.ownerId = ownerId;
    }

    public String getDeptId() {
        return deptId;
    }

    public void setDeptId(String deptId) {
        this.deptId = deptId;
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
        return "ReqProjectDO{projectCode=" + projectCode + ", projectName=" + projectName + "}";
    }
}
