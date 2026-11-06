package com.cim.reqflow.basic.dto;

/**
 * 项目新增 / 修改请求。
 * <p>
 * projectCode 只在新增时有意义：它是附件存储路径的第一级，改名等于让历史附件全部失联，
 * 因此修改接口收到不同的编码会直接拒绝，不做静默忽略。
 *
 * @author Haijie Xiao
 * @date 2026-11-05
 */
public class ProjectSaveDTO {

    /** 项目编码，全局唯一，如 ZS-PILOT */
    private String projectCode;

    /** 项目名称 */
    private String projectName;

    /** 项目类型：PILOT 中试 / MASS 量产 / OTHER */
    private String projectType;

    /** 状态：PLANNING / ACTIVE / SUSPENDED / CLOSED */
    private String status;

    /** 项目负责人用户 ID */
    private String ownerId;

    /** 归口部门 ID */
    private String deptId;

    /** 开始日期 YYYY-MM-DD */
    private String startDate;

    /** 结束日期 YYYY-MM-DD */
    private String endDate;

    /** 排序号，越小越靠前 */
    private Integer sortNo;

    /** 备注 */
    private String remark;

    /**
     * 输出关键字段，便于日志排查。
     *
     * @return 对象字符串
     */
    @Override
    public String toString() {
        return "ProjectSaveDTO{projectCode=" + projectCode
                + ", projectName=" + projectName
                + ", projectType=" + projectType
                + ", status=" + status
                + ", ownerId=" + ownerId
                + ", deptId=" + deptId
                + ", startDate=" + startDate
                + ", endDate=" + endDate
                + ", sortNo=" + sortNo + "}";
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

    public Integer getSortNo() {
        return sortNo;
    }

    public void setSortNo(Integer sortNo) {
        this.sortNo = sortNo;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }
}
