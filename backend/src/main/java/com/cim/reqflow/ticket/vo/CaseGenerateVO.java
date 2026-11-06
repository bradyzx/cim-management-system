package com.cim.reqflow.ticket.vo;

/**
 * 用例生成结果（契约 §9.2）。
 *
 * @author Haijie Xiao
 * @date 2026-11-05
 */
public class CaseGenerateVO {

    /** 生成后的轮次 */
    private Integer roundNo;

    /** 本轮生成条数 */
    private Integer generated;

    /** 跳过条数（验收标准条数为 0 时全跳过） */
    private Integer skipped;

    /** 本轮总条数 */
    private Integer total;

    /** 覆盖率 */
    private CoverageVO coverage;

    /** 提示语，如超量时的建议。无异常为 null */
    private String warning;

    public Integer getRoundNo() {
        return roundNo;
    }

    public void setRoundNo(Integer roundNo) {
        this.roundNo = roundNo;
    }

    public Integer getGenerated() {
        return generated;
    }

    public void setGenerated(Integer generated) {
        this.generated = generated;
    }

    public Integer getSkipped() {
        return skipped;
    }

    public void setSkipped(Integer skipped) {
        this.skipped = skipped;
    }

    public Integer getTotal() {
        return total;
    }

    public void setTotal(Integer total) {
        this.total = total;
    }

    public CoverageVO getCoverage() {
        return coverage;
    }

    public void setCoverage(CoverageVO coverage) {
        this.coverage = coverage;
    }

    public String getWarning() {
        return warning;
    }

    public void setWarning(String warning) {
        this.warning = warning;
    }
}
