package com.cim.reqflow.ticket.dto;

/**
 * 用例执行登记请求（契约 §9.3）。
 *
 * @author Haijie Xiao
 * @date 2026-11-05
 */
public class CaseExecDTO {

    /** PASS / FAIL / SKIP */
    private String execStatus;

    /** 实际结果。FAIL 时必填，否则复盘时看不出当时到底怎么不对 */
    private String actualResult;

    /** 关联的 BUG 单，可选 */
    private String bugId;

    /** 备注 */
    private String remark;

    public String getExecStatus() {
        return execStatus;
    }

    public void setExecStatus(String execStatus) {
        this.execStatus = execStatus;
    }

    public String getActualResult() {
        return actualResult;
    }

    public void setActualResult(String actualResult) {
        this.actualResult = actualResult;
    }

    public String getBugId() {
        return bugId;
    }

    public void setBugId(String bugId) {
        this.bugId = bugId;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }
}
