package com.cim.reqflow.ticket.vo;

import java.util.List;

/**
 * 单据详情。一次返回基本信息与完整时间线，详情页只需一个请求。
 * <p>
 * 所有编码字段都配一个 `xxxLabel`，前端直接渲染，不维护第二份字典映射：
 * 字典改一次标签，界面要跟着变，前端自己缓存映射迟早会飘。
 * <p>
 * 附件与可执行动作不在这里：前者有独立的增删改接口，后者按当前登录人算，
 * 混进详情会让「同一个人刷新两次结果不同」变得难以解释。
 *
 * @author Haijie Xiao
 * @date 2026-11-04
 */
public class TicketDetailVO {

    /** 单据 ID */
    private String id;

    /** 单据编号 */
    private String ticketNo;

    /** 类型：REQ / BUG / INC */
    private String ticketType;

    /** 类型中文名 */
    private String ticketTypeLabel;

    /** 标题 */
    private String title;

    /** 需求简述 */
    private String description;

    /** 现状 As Is */
    private String contentAsis;

    /** 期望 To Be */
    private String contentTobe;

    /** 状态码 */
    private String status;

    /** 状态中文名 */
    private String statusLabel;

    /** 状态标签的语义色，取自字典 css_class（default / processing / warning / success / error） */
    private String statusCssClass;

    /** 优先级 */
    private String priority;

    /** 优先级中文名 */
    private String priorityLabel;

    /** 优先级标签的语义色，取自字典 css_class */
    private String priorityCssClass;

    /** 提出人 */
    private String reporterName;

    /** 录入人 */
    private String creatorName;

    /** 需求 Owner */
    private String ownerName;

    /** 承办人 */
    private String assigneeName;

    /** 提出部门 */
    private String deptName;

    /** 所属项目 */
    private String projectName;

    /** 系统编码 */
    private String systemCode;

    /** 系统名称 */
    private String systemName;

    /** 模块 */
    private String moduleCode;

    /** ROI 说明 */
    private String roiDesc;

    /** ROI 量化值 */
    private Double roiValue;

    /** 提出日期 */
    private String proposeDate;

    /** 期望完成日期 */
    private String expectDueDate;

    /** 预估工时 */
    private Double estimatedHours;

    /** 实际工时 */
    private Double actualHours;

    /** 当前状态应完成时间 */
    private String slaDueAt;

    /** 累计超期小时 */
    private Double slaOverdueHours;

    /** 累计退回次数 */
    private Integer returnCount;

    /** 提交时间 */
    private String submittedAt;

    /** 派单时间 */
    private String assignedAt;

    /** 提交设计时间 */
    private String designAt;

    /** 进入开发时间 */
    private String devAt;

    /** 进入 UAT 时间 */
    private String uatAt;

    /** 上线时间 */
    private String releaseAt;

    /** 验收标准全文 */
    private String acceptanceCriteria;

    /** 验收标准是否已锁定 */
    private Integer acceptanceLocked;

    /** 挂起前状态码 */
    private String suspendedFrom;

    /** 挂起前状态中文名 */
    private String suspendedFromLabel;

    /** 挂起原因 */
    private String suspendReason;

    /** 预计恢复时间 */
    private String expectResumeAt;

    /** 运行监控结论 */
    private String monitorNote;

    // ---- v0.7 并行化：确认与评审进度，前端据此画三方进度标 ----

    /** MIT 已确认：1 是 */
    private Integer mitPassed;

    /** 平台已确认：1 是 */
    private Integer platformPassed;

    /** 上级已确认：1 是 */
    private Integer superiorPassed;

    /** 设计评审 IT 方已通过：1 是 */
    private Integer dsgnItPassed;

    /** 设计评审平台方已通过：1 是 */
    private Integer dsgnPlatformPassed;

    /** 设计评审用户方已通过：1 是 */
    private Integer dsgnUserPassed;

    /** 评价结论：PASS / FAIL */
    private String evalResult;

    /** 满意度 1~5 */
    private Double satisfactionScore;

    /** 评价不符合的原因 */
    private String evalComment;

    /** 评价时间 */
    private String evaluatedAt;

    /** 关闭原因码 */
    private String closeReason;

    /** 关闭原因中文名 */
    private String closeReasonLabel;

    /** 关闭时间 */
    private String closedAt;

    /** 建议分支名 */
    private String branchNameSuggest;

    /** 创建时间 */
    private String createdAt;

    /** 更新时间 */
    private String updatedAt;

    /** 流转时间线，按时间升序 */
    private List<TimelineItemVO> timeline;

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

    public String getCreatorName() {
        return creatorName;
    }

    public void setCreatorName(String creatorName) {
        this.creatorName = creatorName;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public void setOwnerName(String ownerName) {
        this.ownerName = ownerName;
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

    public String getSystemName() {
        return systemName;
    }

    public void setSystemName(String systemName) {
        this.systemName = systemName;
    }

    public String getModuleCode() {
        return moduleCode;
    }

    public void setModuleCode(String moduleCode) {
        this.moduleCode = moduleCode;
    }

    public String getRoiDesc() {
        return roiDesc;
    }

    public void setRoiDesc(String roiDesc) {
        this.roiDesc = roiDesc;
    }

    public Double getRoiValue() {
        return roiValue;
    }

    public void setRoiValue(Double roiValue) {
        this.roiValue = roiValue;
    }

    public String getProposeDate() {
        return proposeDate;
    }

    public void setProposeDate(String proposeDate) {
        this.proposeDate = proposeDate;
    }

    public String getExpectDueDate() {
        return expectDueDate;
    }

    public void setExpectDueDate(String expectDueDate) {
        this.expectDueDate = expectDueDate;
    }

    public Double getEstimatedHours() {
        return estimatedHours;
    }

    public void setEstimatedHours(Double estimatedHours) {
        this.estimatedHours = estimatedHours;
    }

    public Double getActualHours() {
        return actualHours;
    }

    public void setActualHours(Double actualHours) {
        this.actualHours = actualHours;
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

    public String getSubmittedAt() {
        return submittedAt;
    }

    public void setSubmittedAt(String submittedAt) {
        this.submittedAt = submittedAt;
    }

    public String getAssignedAt() {
        return assignedAt;
    }

    public void setAssignedAt(String assignedAt) {
        this.assignedAt = assignedAt;
    }

    public String getDesignAt() {
        return designAt;
    }

    public void setDesignAt(String designAt) {
        this.designAt = designAt;
    }

    public String getDevAt() {
        return devAt;
    }

    public void setDevAt(String devAt) {
        this.devAt = devAt;
    }

    public String getUatAt() {
        return uatAt;
    }

    public void setUatAt(String uatAt) {
        this.uatAt = uatAt;
    }

    public String getReleaseAt() {
        return releaseAt;
    }

    public void setReleaseAt(String releaseAt) {
        this.releaseAt = releaseAt;
    }

    public String getAcceptanceCriteria() {
        return acceptanceCriteria;
    }

    public void setAcceptanceCriteria(String acceptanceCriteria) {
        this.acceptanceCriteria = acceptanceCriteria;
    }

    public Integer getAcceptanceLocked() {
        return acceptanceLocked;
    }

    public void setAcceptanceLocked(Integer acceptanceLocked) {
        this.acceptanceLocked = acceptanceLocked;
    }

    public String getSuspendedFrom() {
        return suspendedFrom;
    }

    public void setSuspendedFrom(String suspendedFrom) {
        this.suspendedFrom = suspendedFrom;
    }

    public String getSuspendedFromLabel() {
        return suspendedFromLabel;
    }

    public void setSuspendedFromLabel(String suspendedFromLabel) {
        this.suspendedFromLabel = suspendedFromLabel;
    }

    public String getSuspendReason() {
        return suspendReason;
    }

    public void setSuspendReason(String suspendReason) {
        this.suspendReason = suspendReason;
    }

    public String getExpectResumeAt() {
        return expectResumeAt;
    }

    public void setExpectResumeAt(String expectResumeAt) {
        this.expectResumeAt = expectResumeAt;
    }

    public String getMonitorNote() {
        return monitorNote;
    }

    public void setMonitorNote(String monitorNote) {
        this.monitorNote = monitorNote;
    }

    public Integer getMitPassed() {
        return mitPassed;
    }

    public void setMitPassed(Integer mitPassed) {
        this.mitPassed = mitPassed;
    }

    public Integer getPlatformPassed() {
        return platformPassed;
    }

    public void setPlatformPassed(Integer platformPassed) {
        this.platformPassed = platformPassed;
    }

    public Integer getSuperiorPassed() {
        return superiorPassed;
    }

    public void setSuperiorPassed(Integer superiorPassed) {
        this.superiorPassed = superiorPassed;
    }

    public Integer getDsgnItPassed() {
        return dsgnItPassed;
    }

    public void setDsgnItPassed(Integer dsgnItPassed) {
        this.dsgnItPassed = dsgnItPassed;
    }

    public Integer getDsgnPlatformPassed() {
        return dsgnPlatformPassed;
    }

    public void setDsgnPlatformPassed(Integer dsgnPlatformPassed) {
        this.dsgnPlatformPassed = dsgnPlatformPassed;
    }

    public Integer getDsgnUserPassed() {
        return dsgnUserPassed;
    }

    public void setDsgnUserPassed(Integer dsgnUserPassed) {
        this.dsgnUserPassed = dsgnUserPassed;
    }

    public String getEvalResult() {
        return evalResult;
    }

    public void setEvalResult(String evalResult) {
        this.evalResult = evalResult;
    }

    public Double getSatisfactionScore() {
        return satisfactionScore;
    }

    public void setSatisfactionScore(Double satisfactionScore) {
        this.satisfactionScore = satisfactionScore;
    }

    public String getEvalComment() {
        return evalComment;
    }

    public void setEvalComment(String evalComment) {
        this.evalComment = evalComment;
    }

    public String getEvaluatedAt() {
        return evaluatedAt;
    }

    public void setEvaluatedAt(String evaluatedAt) {
        this.evaluatedAt = evaluatedAt;
    }

    public String getCloseReason() {
        return closeReason;
    }

    public void setCloseReason(String closeReason) {
        this.closeReason = closeReason;
    }

    public String getCloseReasonLabel() {
        return closeReasonLabel;
    }

    public void setCloseReasonLabel(String closeReasonLabel) {
        this.closeReasonLabel = closeReasonLabel;
    }

    public String getClosedAt() {
        return closedAt;
    }

    public void setClosedAt(String closedAt) {
        this.closedAt = closedAt;
    }

    public String getBranchNameSuggest() {
        return branchNameSuggest;
    }

    public void setBranchNameSuggest(String branchNameSuggest) {
        this.branchNameSuggest = branchNameSuggest;
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

    public List<TimelineItemVO> getTimeline() {
        return timeline;
    }

    public void setTimeline(List<TimelineItemVO> timeline) {
        this.timeline = timeline;
    }

    /**
     * 只输出关键字段。
     *
     * @return 关键字段字符串
     */
    @Override
    public String toString() {
        return "TicketDetailVO{" + "id=" + id + ", " + "ticketNo=" + ticketNo + ", " + "ticketType=" + ticketType + "}";
    }
}
