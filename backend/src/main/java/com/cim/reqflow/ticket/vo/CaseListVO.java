package com.cim.reqflow.ticket.vo;

import java.util.List;

/**
 * 用例列表（契约 §9.1）：某一轮的用例 + 该单的覆盖率 + 可选轮次。
 *
 * @author Haijie Xiao
 * @date 2026-11-05
 */
public class CaseListVO {

    /** 本次返回的轮次 */
    private Integer roundNo;

    /** 该单已生成过哪些轮次，倒序，供前端切换 */
    private List<Integer> rounds;

    /** 用例 */
    private List<TestCaseVO> items;

    /** 覆盖率 */
    private CoverageVO coverage;

    /** 本轮执行进度：已执行条数 */
    private Integer executedCount;

    /** 本轮通过条数 */
    private Integer passedCount;

    /** 本轮不通过条数 */
    private Integer failedCount;

    public Integer getRoundNo() {
        return roundNo;
    }

    public void setRoundNo(Integer roundNo) {
        this.roundNo = roundNo;
    }

    public List<Integer> getRounds() {
        return rounds;
    }

    public void setRounds(List<Integer> rounds) {
        this.rounds = rounds;
    }

    public List<TestCaseVO> getItems() {
        return items;
    }

    public void setItems(List<TestCaseVO> items) {
        this.items = items;
    }

    public CoverageVO getCoverage() {
        return coverage;
    }

    public void setCoverage(CoverageVO coverage) {
        this.coverage = coverage;
    }

    public Integer getExecutedCount() {
        return executedCount;
    }

    public void setExecutedCount(Integer executedCount) {
        this.executedCount = executedCount;
    }

    public Integer getPassedCount() {
        return passedCount;
    }

    public void setPassedCount(Integer passedCount) {
        this.passedCount = passedCount;
    }

    public Integer getFailedCount() {
        return failedCount;
    }

    public void setFailedCount(Integer failedCount) {
        this.failedCount = failedCount;
    }
}
