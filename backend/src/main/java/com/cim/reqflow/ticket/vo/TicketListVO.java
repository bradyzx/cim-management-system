package com.cim.reqflow.ticket.vo;

/**
 * 单据列表行。
 * <p>
 * 列表不返回正文与验收标准：一页 20 条的 As Is / To Be 会让响应体积翻十倍，
 * 而列表页一个字都不显示。需要正文的点进详情。
 *
 * @author Haijie Xiao
 * @date 2026-11-04
 */
public class TicketListVO {

    /** 单据 ID，前端据此跳详情 */
    private String id;

    /** 单据编号 */
    private String ticketNo;

    /** 类型码 */
    private String ticketType;

    /** 类型中文名 */
    private String ticketTypeLabel;

    /** 标题 */
    private String title;

    /** 状态码 */
    private String status;

    /** 状态中文名 */
    private String statusLabel;

    /** 状态语义色 */
    private String statusCssClass;

    /** 优先级 */
    private String priority;

    /** 优先级中文名 */
    private String priorityLabel;

    /** 优先级语义色 */
    private String priorityCssClass;

    /** 提出人 */
    private String reporterName;

    /** 承办人 */
    private String assigneeName;

    /** 提出部门 */
    private String deptName;

    /** 项目 */
    private String projectName;

    /** 系统编码 */
    private String systemCode;

    /** 当前状态应完成时间 */
    private String slaDueAt;

    /** 累计超期小时 */
    private Double slaOverdueHours;

    /** 累计退回次数 */
    private Integer returnCount;

    /** 创建时间 */
    private String createdAt;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTicketNo() {
        return ticketNo;
    }

    public void setTicketNo(String ticketNo) {
        this.ticketNo = ticketNo;
    }

    public String getTicketType() {
        return ticketType;
    }

    public void setTicketType(String ticketType) {
        this.ticketType = ticketType;
    }

    public String getTicketTypeLabel() {
        return ticketTypeLabel;
    }

    public void setTicketTypeLabel(String ticketTypeLabel) {
        this.ticketTypeLabel = ticketTypeLabel;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getStatusLabel() {
        return statusLabel;
    }

    public void setStatusLabel(String statusLabel) {
        this.statusLabel = statusLabel;
    }

    public String getStatusCssClass() {
        return statusCssClass;
    }

    public void setStatusCssClass(String statusCssClass) {
        this.statusCssClass = statusCssClass;
    }

    public String getPriority() {
        return priority;
    }

    public void setPriority(String priority) {
        this.priority = priority;
    }

    public String getPriorityLabel() {
        return priorityLabel;
    }

    public void setPriorityLabel(String priorityLabel) {
        this.priorityLabel = priorityLabel;
    }

    public String getPriorityCssClass() {
        return priorityCssClass;
    }

    public void setPriorityCssClass(String priorityCssClass) {
        this.priorityCssClass = priorityCssClass;
    }

    public String getReporterName() {
        return reporterName;
    }

    public void setReporterName(String reporterName) {
        this.reporterName = reporterName;
    }

    public String getAssigneeName() {
        return assigneeName;
    }

    public void setAssigneeName(String assigneeName) {
        this.assigneeName = assigneeName;
    }

    public String getDeptName() {
        return deptName;
    }

    public void setDeptName(String deptName) {
        this.deptName = deptName;
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

    public String getSlaDueAt() {
        return slaDueAt;
    }

    public void setSlaDueAt(String slaDueAt) {
        this.slaDueAt = slaDueAt;
    }

    public Double getSlaOverdueHours() {
        return slaOverdueHours;
    }

    public void setSlaOverdueHours(Double slaOverdueHours) {
        this.slaOverdueHours = slaOverdueHours;
    }

    public Integer getReturnCount() {
        return returnCount;
    }

    public void setReturnCount(Integer returnCount) {
        this.returnCount = returnCount;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    /**
     * 只输出编号与状态。
     *
     * @return 关键字段字符串
     */
    @Override
    public String toString() {
        return "TicketListVO{ticketNo=" + ticketNo + ", status=" + status + "}";
    }
}
