package com.cim.reqflow.ticket.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 测试用例实体。一条用例对应一条验收标准，`criteria_index` 是覆盖率的连接键。
 * <p>
 * `steps` 存 JSON 数组字符串，不是换行文本：UAT 时要逐步打勾、
 * 不通过时要能把步骤带进 BUG 单，纯文本没法拆。
 *
 * @author Haijie Xiao
 * @date 2026-11-05
 */
@TableName("req_test_case")
public class ReqTestCaseDO {

    @TableId(type = IdType.INPUT)
    private String id;

    /** 所属单据 */
    private String ticketId;

    /** 用例编号，如 TC-REQ000001-01，全局唯一 */
    private String caseNo;

    /** 用例标题 */
    private String title;

    /** 前置条件 */
    private String precond;

    /** 步骤，JSON 数组字符串 */
    private String steps;

    /** 预期结果 */
    private String expected;

    /** 优先级 P0 / P1 / P2 */
    private String priority;

    /** 对应的验收标准原文 */
    private String sourceCriteria;

    /** 对应第几条验收标准，覆盖率分母的键 */
    private Integer criteriaIndex;

    /** 1 = 重点关注 */
    private Integer focusFlag;

    /** 为什么重点关注；只标红不说原因等于没标 */
    private String focusReason;

    /** AI / HUMAN。人工改过即转 HUMAN */
    private String origin;

    /** NOT_RUN / PASS / FAIL / SKIP */
    private String execStatus;

    /** 不通过时关联的 BUG 单 */
    private String bugId;

    /** 生成批次：设计确认后第 1 轮，UAT 前刷新为第 2 轮 */
    private Integer roundNo;

    /** 创建人 */
    private String createdBy;

    private String createdAt;

    private String updatedAt;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTicketId() {
        return ticketId;
    }

    public void setTicketId(String ticketId) {
        this.ticketId = ticketId;
    }

    public String getCaseNo() {
        return caseNo;
    }

    public void setCaseNo(String caseNo) {
        this.caseNo = caseNo;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getPrecond() {
        return precond;
    }

    public void setPrecond(String precond) {
        this.precond = precond;
    }

    public String getSteps() {
        return steps;
    }

    public void setSteps(String steps) {
        this.steps = steps;
    }

    public String getExpected() {
        return expected;
    }

    public void setExpected(String expected) {
        this.expected = expected;
    }

    public String getPriority() {
        return priority;
    }

    public void setPriority(String priority) {
        this.priority = priority;
    }

    public String getSourceCriteria() {
        return sourceCriteria;
    }

    public void setSourceCriteria(String sourceCriteria) {
        this.sourceCriteria = sourceCriteria;
    }

    public Integer getCriteriaIndex() {
        return criteriaIndex;
    }

    public void setCriteriaIndex(Integer criteriaIndex) {
        this.criteriaIndex = criteriaIndex;
    }

    public Integer getFocusFlag() {
        return focusFlag;
    }

    public void setFocusFlag(Integer focusFlag) {
        this.focusFlag = focusFlag;
    }

    public String getFocusReason() {
        return focusReason;
    }

    public void setFocusReason(String focusReason) {
        this.focusReason = focusReason;
    }

    public String getOrigin() {
        return origin;
    }

    public void setOrigin(String origin) {
        this.origin = origin;
    }

    public String getExecStatus() {
        return execStatus;
    }

    public void setExecStatus(String execStatus) {
        this.execStatus = execStatus;
    }

    public String getBugId() {
        return bugId;
    }

    public void setBugId(String bugId) {
        this.bugId = bugId;
    }

    public Integer getRoundNo() {
        return roundNo;
    }

    public void setRoundNo(Integer roundNo) {
        this.roundNo = roundNo;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
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

    /**
     * 只输出编号与执行状态。
     *
     * @return 关键字段字符串
     */
    @Override
    public String toString() {
        return "ReqTestCaseDO{caseNo=" + caseNo + ", execStatus=" + execStatus + "}";
    }
}
