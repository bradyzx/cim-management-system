package com.cim.reqflow.ticket.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 需求单据实体，与 DDL 的 req_ticket 一一对应（69 列）。
 * <p>
 * 时间为 ISO 字符串，由应用层生成，不使用数据库默认值。
 * 单据编号在提交时由发号器取号，草稿不占号。
 * <p>
 * 状态字段只由 TicketFlowService 写入，业务代码不允许直接 UPDATE status ——
 * 一旦绕过流转引擎改状态，req_flow_log 时间线必然和状态对不上，报表全是错的。
 *
 * @author Haijie Xiao
 * @date 2026-11-04
 */
@TableName("req_ticket")
public class ReqTicketDO {

    @TableId(type = IdType.INPUT)
    private String id;

    /** 单据编号，如 REQ-2026-000001 */
    private String ticketNo;

    /** 单据类型：REQ 需求 / BUG 缺陷 / INC 故障 */
    private String ticketType;

    /** 标题 */
    private String title;

    /** 需求简述 */
    private String description;

    /** 现状 As Is */
    private String contentAsis;

    /** 期望 To Be */
    private String contentTobe;

    /** 状态码，27 态之一，见 TicketStatus */
    private String status;

    /** 优先级 P0~P3 */
    private String priority;

    /** 提出人 */
    private String reporterId;

    /** 录入人，与提出人分开记（代录场景） */
    private String creatorId;

    /** 需求 Owner，业务侧责任人 */
    private String ownerId;

    /** 提交时提出人主部门快照，不随调岗变 */
    private String deptId;

    /** 承办人 */
    private String assigneeId;

    /** 所属项目，提单时快照 */
    private String projectId;

    /** 所属系统，主关联 */
    private String systemId;

    /** 系统编码冗余快照，只供导出与报表，不参与业务判断 */
    private String systemCode;

    /** 系统内模块 */
    private String moduleCode;

    /** 验收标准长文本；逐条数据在 req_acceptance_criteria */
    private String acceptanceCriteria;

    /** 验收标准是否已锁定：1 是、0 否 */
    private Integer acceptanceLocked;

    /** 锁定时间 */
    private String acceptanceLockedAt;

    /** 锁定人 */
    private String acceptanceLockedBy;

    /** ROI 说明 */
    private String roiDesc;

    /** ROI 量化值 */
    private Double roiValue;

    /** 提出日期，业务口径，可早于提交时间 */
    private String proposeDate;

    /** 期望完成日期 */
    private String expectDueDate;

    /** 预估工时（小时） */
    private Double estimatedHours;

    /** 实际工时（小时） */
    private Double actualHours;

    /** 父单：BUG 挂 REQ，或需求分解 */
    private String parentId;

    /** 来源：WEB / API */
    private String ticketSource;

    /** AI 判重复但仍坚持新建：1 是 */
    private Integer dupOverride;

    /** 坚持新建的原因 */
    private String overrideReason;

    /** 累计退回次数；按点计次查 req_flow_log 聚合 */
    private Integer returnCount;

    /** 挂起前的状态，恢复时回读 */
    private String suspendedFrom;

    /** 挂起原因 */
    private String suspendReason;

    /** 预计恢复时间 */
    private String expectResumeAt;

    /** 关闭原因：DONE / REJECT / DUP / CANCEL / TIMEOUT */
    private String closeReason;

    /** 关闭时间 */
    private String closedAt;

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

    /** 上线时间，满 7 天定时器的起算点 */
    private String releaseAt;

    /** 当前状态的应完成时间 */
    private String slaDueAt;

    /** 累计超期小时，退回不归零 */
    private Double slaOverdueHours;

    /** 提单时生成的建议分支名 */
    private String branchNameSuggest;

    /** 上级确认人快照；本人是负责人时为空 */
    private String superiorId;

    /** 上级确认时间 */
    private String superiorConfirmedAt;

    /** 是否跳过上级确认：1 是 */
    private Integer superiorSkipped;

    /** 需求评审通过时间 */
    private String reviewPassedAt;

    /** 上线评价结论：PASS 符合 / FAIL 不符合 */
    private String evalResult;

    /** 满意度 1~5，仅 PASS 时填 */
    private Double satisfactionScore;

    /** FAIL 时必填的不符合原因 */
    private String evalComment;

    /** 评价时间 */
    private String evaluatedAt;

    /** 评价人 */
    private String evaluatedBy;

    /** 是否跳过 MIT 确认：1 是 */
    private Integer mitSkipped;

    /** 是否跳过平台确认：1 是 */
    private Integer platformSkipped;

    // ---- v0.7 并行化：确认与评审通过标志，一方置 1 后不许再点 ----

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

    /** 需求文档确认时间 */
    private String docConfirmedAt;

    /** 观察期天数：REQ 7 / BUG 3 */
    private Integer runStableDays;

    /** AI 是否降级为模板空壳：1 是 */
    private Integer aiDegraded;

    /** 已进行的澄清轮次 */
    private Integer aiClarifyRound;

    /** 运行监控结论，提交时校验不少于 20 字 */
    private String monitorNote;

    /** 关联故障单；未关闭则满 7 天计时暂停 */
    private String relatedIncId;

    /** 用例覆盖率不足是否由 Leader 豁免：1 是 */
    private Integer uatWaiver;

    /** 豁免理由 */
    private String uatWaiverReason;

    /** 创建时间 */
    private String createdAt;

    /** 更新时间 */
    private String updatedAt;

    /** 最后更新人 */
    private String updatedBy;

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

    public String getPriority() {
        return priority;
    }

    public void setPriority(String priority) {
        this.priority = priority;
    }

    public String getReporterId() {
        return reporterId;
    }

    public void setReporterId(String reporterId) {
        this.reporterId = reporterId;
    }

    public String getCreatorId() {
        return creatorId;
    }

    public void setCreatorId(String creatorId) {
        this.creatorId = creatorId;
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

    public String getAssigneeId() {
        return assigneeId;
    }

    public void setAssigneeId(String assigneeId) {
        this.assigneeId = assigneeId;
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

    public String getSystemCode() {
        return systemCode;
    }

    public void setSystemCode(String systemCode) {
        this.systemCode = systemCode;
    }

    public String getModuleCode() {
        return moduleCode;
    }

    public void setModuleCode(String moduleCode) {
        this.moduleCode = moduleCode;
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

    public String getAcceptanceLockedAt() {
        return acceptanceLockedAt;
    }

    public void setAcceptanceLockedAt(String acceptanceLockedAt) {
        this.acceptanceLockedAt = acceptanceLockedAt;
    }

    public String getAcceptanceLockedBy() {
        return acceptanceLockedBy;
    }

    public void setAcceptanceLockedBy(String acceptanceLockedBy) {
        this.acceptanceLockedBy = acceptanceLockedBy;
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

    public String getParentId() {
        return parentId;
    }

    public void setParentId(String parentId) {
        this.parentId = parentId;
    }

    public String getTicketSource() {
        return ticketSource;
    }

    public void setTicketSource(String ticketSource) {
        this.ticketSource = ticketSource;
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

    public Integer getReturnCount() {
        return returnCount;
    }

    public void setReturnCount(Integer returnCount) {
        this.returnCount = returnCount;
    }

    public String getSuspendedFrom() {
        return suspendedFrom;
    }

    public void setSuspendedFrom(String suspendedFrom) {
        this.suspendedFrom = suspendedFrom;
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

    public String getCloseReason() {
        return closeReason;
    }

    public void setCloseReason(String closeReason) {
        this.closeReason = closeReason;
    }

    public String getClosedAt() {
        return closedAt;
    }

    public void setClosedAt(String closedAt) {
        this.closedAt = closedAt;
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

    public String getBranchNameSuggest() {
        return branchNameSuggest;
    }

    public void setBranchNameSuggest(String branchNameSuggest) {
        this.branchNameSuggest = branchNameSuggest;
    }

    public String getSuperiorId() {
        return superiorId;
    }

    public void setSuperiorId(String superiorId) {
        this.superiorId = superiorId;
    }

    public String getSuperiorConfirmedAt() {
        return superiorConfirmedAt;
    }

    public void setSuperiorConfirmedAt(String superiorConfirmedAt) {
        this.superiorConfirmedAt = superiorConfirmedAt;
    }

    public Integer getSuperiorSkipped() {
        return superiorSkipped;
    }

    public void setSuperiorSkipped(Integer superiorSkipped) {
        this.superiorSkipped = superiorSkipped;
    }

    public String getReviewPassedAt() {
        return reviewPassedAt;
    }

    public void setReviewPassedAt(String reviewPassedAt) {
        this.reviewPassedAt = reviewPassedAt;
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

    public String getEvaluatedBy() {
        return evaluatedBy;
    }

    public void setEvaluatedBy(String evaluatedBy) {
        this.evaluatedBy = evaluatedBy;
    }

    public Integer getMitSkipped() {
        return mitSkipped;
    }

    public void setMitSkipped(Integer mitSkipped) {
        this.mitSkipped = mitSkipped;
    }

    public Integer getPlatformSkipped() {
        return platformSkipped;
    }

    public void setPlatformSkipped(Integer platformSkipped) {
        this.platformSkipped = platformSkipped;
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

    public String getDocConfirmedAt() {
        return docConfirmedAt;
    }

    public void setDocConfirmedAt(String docConfirmedAt) {
        this.docConfirmedAt = docConfirmedAt;
    }

    public Integer getRunStableDays() {
        return runStableDays;
    }

    public void setRunStableDays(Integer runStableDays) {
        this.runStableDays = runStableDays;
    }

    public Integer getAiDegraded() {
        return aiDegraded;
    }

    public void setAiDegraded(Integer aiDegraded) {
        this.aiDegraded = aiDegraded;
    }

    public Integer getAiClarifyRound() {
        return aiClarifyRound;
    }

    public void setAiClarifyRound(Integer aiClarifyRound) {
        this.aiClarifyRound = aiClarifyRound;
    }

    public String getMonitorNote() {
        return monitorNote;
    }

    public void setMonitorNote(String monitorNote) {
        this.monitorNote = monitorNote;
    }

    public String getRelatedIncId() {
        return relatedIncId;
    }

    public void setRelatedIncId(String relatedIncId) {
        this.relatedIncId = relatedIncId;
    }

    public Integer getUatWaiver() {
        return uatWaiver;
    }

    public void setUatWaiver(Integer uatWaiver) {
        this.uatWaiver = uatWaiver;
    }

    public String getUatWaiverReason() {
        return uatWaiverReason;
    }

    public void setUatWaiverReason(String uatWaiverReason) {
        this.uatWaiverReason = uatWaiverReason;
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

    public String getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(String updatedBy) {
        this.updatedBy = updatedBy;
    }

    /**
     * 只输出主键、编号与状态。
     * <p>
     * 本类 69 列，全字段输出会把日志刷屏，且验收标准、监控结论都是长文本，
     * 排查流转问题时真正需要的只有这三个值。手册要求 POJO 必须写 toString，
     * 这里的裁剪已在此说明理由。
     *
     * @return 关键字段字符串
     */
    @Override
    public String toString() {
        return "ReqTicketDO{id=" + id + ", ticketNo=" + ticketNo
                + ", status=" + status + "}";
    }
}
