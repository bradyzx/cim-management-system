package com.cim.reqflow.ticket.vo;

import java.util.List;

/**
 * 测试用例视图。
 * <p>
 * `steps` 返回数组而不是 JSON 字符串：库里存的是 `["步骤1","步骤2"]`，
 * 让前端再 parse 一次，解析失败时整页白屏，不如后端解析好、
 * 坏了就返回空数组。
 *
 * @author Haijie Xiao
 * @date 2026-11-05
 */
public class TestCaseVO {

    /** 用例 ID */
    private String id;

    /** 用例编号 TC-REQ000001-01 */
    private String caseNo;

    /** 标题 */
    private String title;

    /** 前置条件 */
    private String precond;

    /** 步骤，已从 JSON 解析 */
    private List<String> steps;

    /** 预期结果 */
    private String expected;

    /** P0 / P1 / P2 */
    private String priority;

    /** 对应验收标准原文 */
    private String sourceCriteria;

    /** 对应第几条验收标准 */
    private Integer criteriaIndex;

    /** 1 = 重点关注 */
    private Integer focusFlag;

    /** 重点关注理由 */
    private String focusReason;

    /** AI / HUMAN */
    private String origin;

    /** NOT_RUN / PASS / FAIL / SKIP */
    private String execStatus;

    /** 执行状态中文名 */
    private String execStatusLabel;

    /** 不通过时关联的 BUG 单 */
    private String bugId;

    /** 生成批次 */
    private Integer roundNo;

    private String updatedAt;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
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

    public List<String> getSteps() {
        return steps;
    }

    public void setSteps(List<String> steps) {
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

    public String getExecStatusLabel() {
        return execStatusLabel;
    }

    public void setExecStatusLabel(String execStatusLabel) {
        this.execStatusLabel = execStatusLabel;
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

    public String getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(String updatedAt) {
        this.updatedAt = updatedAt;
    }
}
