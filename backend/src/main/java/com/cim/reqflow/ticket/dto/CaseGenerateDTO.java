package com.cim.reqflow.ticket.dto;

/**
 * 用例生成请求。
 *
 * @author Haijie Xiao
 * @date 2026-11-05
 */
public class CaseGenerateDTO {

    /** 指定轮次；不传则按已有最大轮次 +1 */
    private Integer roundNo;

    public Integer getRoundNo() {
        return roundNo;
    }

    public void setRoundNo(Integer roundNo) {
        this.roundNo = roundNo;
    }
}
