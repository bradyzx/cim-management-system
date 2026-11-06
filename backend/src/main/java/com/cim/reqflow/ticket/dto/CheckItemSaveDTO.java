package com.cim.reqflow.ticket.dto;

/**
 * 点检登记请求（契约 §10.3）。
 *
 * @author Haijie Xiao
 * @date 2026-11-05
 */
public class CheckItemSaveDTO {

    /** PASS / FAIL / NA */
    private String checkResult;

    /** 不通过说明。FAIL 时必填，否则复盘时不知道哪里不对 */
    private String checkRemark;

    public String getCheckResult() {
        return checkResult;
    }

    public void setCheckResult(String checkResult) {
        this.checkResult = checkResult;
    }

    public String getCheckRemark() {
        return checkRemark;
    }

    public void setCheckRemark(String checkRemark) {
        this.checkRemark = checkRemark;
    }
}
