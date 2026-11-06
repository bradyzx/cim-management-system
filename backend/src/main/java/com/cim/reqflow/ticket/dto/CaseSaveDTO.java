package com.cim.reqflow.ticket.dto;

import java.util.List;

/**
 * 用例编辑请求（契约 §9.3）。
 * <p>
 * 只收可人工调整的字段：编号、来源标准、批次、执行状态都不在这里改 ——
 * 编号是引用键（BUG 单会引它），来源标准是覆盖率的连接键，
 * 让它们可改等于让覆盖率随时能造假。
 *
 * @author Haijie Xiao
 * @date 2026-11-05
 */
public class CaseSaveDTO {

    /** 标题 */
    private String title;

    /** 前置条件 */
    private String precond;

    /** 步骤 */
    private List<String> steps;

    /** 预期结果 */
    private String expected;

    /** P0 / P1 / P2 */
    private String priority;

    /** 1 = 重点关注 */
    private Integer focusFlag;

    /** 重点关注理由 */
    private String focusReason;

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
}
