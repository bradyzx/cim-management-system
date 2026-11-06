package com.cim.reqflow.basic.vo;

/**
 * 系统管理列表行。
 * <p>
 * 带上所属项目名称，是因为列表可能跨项目展示（按项目筛选时也不该让用户去别处对照 ID）。
 * ticketCount 用于删除前提示：挂着单据的系统不允许删。
 *
 * @author Haijie Xiao
 * @date 2026-11-05
 */
public class SystemVO {

    /** 系统 ID */
    private String id;

    /** 所属项目 ID */
    private String projectId;

    /** 所属项目编码 */
    private String projectCode;

    /** 所属项目名称 */
    private String projectName;

    /** 系统编码，项目内唯一 */
    private String systemCode;

    /** 系统名称 */
    private String systemName;

    /** 系统类型码 */
    private String systemType;

    /** 负责人 ID，编辑回填用 */
    private String ownerId;

    /** 负责人姓名 */
    private String ownerName;

    /** 状态码 */
    private String status;

    /** 备注 */
    private String remark;

    /** 排序号 */
    private Integer sortNo;

    /** 本系统下的需求单数量 */
    private Integer ticketCount;

    /** 创建时间 */
    private String createdAt;

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

    public String getOwnerName() {
        return ownerName;
    }

    public void setOwnerName(String ownerName) {
        this.ownerName = ownerName;
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
