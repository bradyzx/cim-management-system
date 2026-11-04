package com.cim.reqflow.ticket.dto;

/**
 * 单据列表查询条件。
 * <p>
 * 只放「用户能填的业务条件」。数据范围由后端按角色算，绝不从前端传 ——
 * 前端传范围等于把可见性交给客户端，改个参数就能看到别人的单。
 *
 * @author Haijie Xiao
 * @date 2026-11-04
 */
public class TicketQueryDTO {

    /** 页码，从 1 开始 */
    private Integer page;

    /** 每页条数，上限见 CommonConstants.MAX_PAGE_SIZE */
    private Integer pageSize;

    /** 关键字，同时匹配单号与标题 */
    private String keyword;

    /** 单据类型：REQ / BUG / INC */
    private String ticketType;

    /** 状态码，支持逗号分隔多选 */
    private String status;

    /** 优先级 */
    private String priority;

    /** 所属项目 */
    private String projectId;

    /** 所属系统 */
    private String systemId;

    /** 承办人；「我经手的」筛选用 */
    private String assigneeId;

    /** 创建日期起，yyyy-MM-dd */
    private String startDate;

    /** 创建日期止，yyyy-MM-dd */
    private String endDate;

    public Integer getPage() {
        return page;
    }

    public void setPage(Integer page) {
        this.page = page;
    }

    public Integer getPageSize() {
        return pageSize;
    }

    public void setPageSize(Integer pageSize) {
        this.pageSize = pageSize;
    }

    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }

    public String getTicketType() {
        return ticketType;
    }

    public void setTicketType(String ticketType) {
        this.ticketType = ticketType;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getPriority() {
        return priority;
    }

    public void setPriority(String priority) {
        this.priority = priority;
    }

    public String getProjectId() {
        return projectId;
    }

    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }

    public String getSystemId() {
        return systemId;
    }

    public void setSystemId(String systemId) {
        this.systemId = systemId;
    }

    public String getAssigneeId() {
        return assigneeId;
    }

    public void setAssigneeId(String assigneeId) {
        this.assigneeId = assigneeId;
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

    /**
     * 只输出查询条件，便于排查「为什么查不到这张单」。
     *
     * @return 关键字段字符串
     */
    @Override
    public String toString() {
        return "TicketQueryDTO{keyword=" + keyword + ", status=" + status
                + ", ticketType=" + ticketType + ", assigneeId=" + assigneeId + "}";
    }
}
