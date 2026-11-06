package com.cim.reqflow.basic.vo;

/**
 * 项目管理列表行。
 * <p>
 * 比实体多两类信息：负责人与部门的显示名（前端不再自己查两次字典），
 * 以及下挂的系统数与单据数 —— 删除前必须先知道挂了多少东西，
 * 否则用户点删除才被告知不能删，体验很差。
 *
 * @author Haijie Xiao
 * @date 2026-11-05
 */
public class ProjectVO {

    /** 项目 ID */
    private String id;

    /** 项目编码 */
    private String projectCode;

    /** 项目名称 */
    private String projectName;

    /** 项目类型码 */
    private String projectType;

    /** 状态码 */
    private String status;

    /** 负责人 ID，编辑回填用 */
    private String ownerId;

    /** 负责人姓名 */
    private String ownerName;

    /** 归口部门 ID，编辑回填用 */
    private String deptId;

    /** 归口部门名称 */
    private String deptName;

    /** 开始日期 */
    private String startDate;

    /** 结束日期 */
    private String endDate;

    /** 备注 */
    private String remark;

    /** 排序号 */
    private Integer sortNo;

    /** 本项目下的系统数量 */
    private Integer systemCount;

    /** 本项目下的需求单数量 */
    private Integer ticketCount;

    /** 创建时间 */
    private String createdAt;

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

    public String getOwnerName() {
        return ownerName;
    }

    public void setOwnerName(String ownerName) {
        this.ownerName = ownerName;
    }

    public String getDeptId() {
        return deptId;
    }

    public void setDeptId(String deptId) {
        this.deptId = deptId;
    }

    public String getDeptName() {
        return deptName;
    }

    public void setDeptName(String deptName) {
        this.deptName = deptName;
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

    public Integer getSystemCount() {
        return systemCount;
    }

    public void setSystemCount(Integer systemCount) {
        this.systemCount = systemCount;
    }

    public Integer getTicketCount() {
        return ticketCount;
    }

    public void setTicketCount(Integer ticketCount) {
        this.ticketCount = ticketCount;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }
}
