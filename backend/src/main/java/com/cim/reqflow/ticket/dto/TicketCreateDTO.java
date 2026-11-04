package com.cim.reqflow.ticket.dto;

/**
 * 新建单据请求。
 * <p>
 * 表单只收「提出人想说的」，需求文档由 AI 规则模板在提交后生成 ——
 * 让提出人填 12 节文档等于把写文档的活推回给业务。
 *
 * @author Haijie Xiao
 * @date 2026-11-04
 */
public class TicketCreateDTO {

    /** 单据类型：REQ / BUG / INC */
    private String ticketType;

    /** 所属项目 */
    private String projectId;

    /** 所属系统，必须是该项目下的系统 */
    private String systemId;

    /** 模块，可选 */
    private String moduleCode;

    /** 标题 */
    private String title;

    /** 优先级 P0~P3 */
    private String priority;

    /** 需求简述 */
    private String description;

    /** 现状 As Is */
    private String contentAsis;

    /** 期望 To Be */
    private String contentTobe;

    /** 期望完成日期，可选 */
    private String expectDueDate;

    /** 验收标准，可选；按行拆条落子表 */
    private String acceptanceCriteria;

    /** ROI 说明，可选 */
    private String roiDesc;

    /** 查重命中后仍要新建：1 是 */
    private Integer dupOverride;

    /** 仍要新建的原因，dupOverride=1 时必填 */
    private String overrideReason;

    public String getTicketType() {
        return ticketType;
    }

    public void setTicketType(String ticketType) {
        this.ticketType = ticketType;
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

    public String getModuleCode() {
        return moduleCode;
    }

    public void setModuleCode(String moduleCode) {
        this.moduleCode = moduleCode;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getPriority() {
        return priority;
    }

    public void setPriority(String priority) {
        this.priority = priority;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getContentAsis() {
        return contentAsis;
    }

    public void setContentAsis(String contentAsis) {
        this.contentAsis = contentAsis;
    }

    public String getContentTobe() {
        return contentTobe;
    }

    public void setContentTobe(String contentTobe) {
        this.contentTobe = contentTobe;
    }

    public String getExpectDueDate() {
        return expectDueDate;
    }

    public void setExpectDueDate(String expectDueDate) {
        this.expectDueDate = expectDueDate;
    }

    public String getAcceptanceCriteria() {
        return acceptanceCriteria;
    }

    public void setAcceptanceCriteria(String acceptanceCriteria) {
        this.acceptanceCriteria = acceptanceCriteria;
    }

    public String getRoiDesc() {
        return roiDesc;
    }

    public void setRoiDesc(String roiDesc) {
        this.roiDesc = roiDesc;
    }

    public Integer getDupOverride() {
        return dupOverride;
    }

    public void setDupOverride(Integer dupOverride) {
        this.dupOverride = dupOverride;
    }

    public String getOverrideReason() {
        return overrideReason;
    }

    public void setOverrideReason(String overrideReason) {
        this.overrideReason = overrideReason;
    }

    /**
     * 只输出关键条件，便于排查。
     *
     * @return 关键字段字符串
     */
    @Override
    public String toString() {
        return "TicketCreateDTO{title=" + title + ", ticketType=" + ticketType
                + ", systemId=" + systemId + "}";
    }
}
