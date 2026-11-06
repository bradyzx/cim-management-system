package com.cim.reqflow.ticket.vo;

import java.util.List;

/**
 * 用例覆盖率视图（契约 §9.4）。
 *
 * @author Haijie Xiao
 * @date 2026-11-05
 */
public class CoverageVO {

    /**
     * 未覆盖的验收标准。
     *
     * @param seq     序号
     * @param content 原文
     */
    public record Missing(int seq, String content) {
    }

    /** 验收标准总数 */
    private Integer criteriaTotal;

    /** 已覆盖数 */
    private Integer covered;

    /** 覆盖率 0~1 */
    private Double rate;

    /** 未覆盖的标准 */
    private List<Missing> missing;

    /** 是否已全覆盖 */
    private Boolean full;

    /** 是否已豁免（UAT 覆盖率豁免） */
    private Integer uatWaiver;

    /** 豁免理由 */
    private String waiverReason;

    public Integer getCriteriaTotal() {
        return criteriaTotal;
    }

    public void setCriteriaTotal(Integer criteriaTotal) {
        this.criteriaTotal = criteriaTotal;
    }

    public Integer getCovered() {
        return covered;
    }

    public void setCovered(Integer covered) {
        this.covered = covered;
    }

    public Double getRate() {
        return rate;
    }

    public void setRate(Double rate) {
        this.rate = rate;
    }

    public List<Missing> getMissing() {
        return missing;
    }

    public void setMissing(List<Missing> missing) {
        this.missing = missing;
    }

    public Boolean getFull() {
        return full;
    }

    public void setFull(Boolean full) {
        this.full = full;
    }

    public Integer getUatWaiver() {
        return uatWaiver;
    }

    public void setUatWaiver(Integer uatWaiver) {
        this.uatWaiver = uatWaiver;
    }

    public String getWaiverReason() {
        return waiverReason;
    }

    public void setWaiverReason(String waiverReason) {
        this.waiverReason = waiverReason;
    }
}
