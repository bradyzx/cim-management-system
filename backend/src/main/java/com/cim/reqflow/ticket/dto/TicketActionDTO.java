package com.cim.reqflow.ticket.dto;

import java.util.Map;

/**
 * 流转动作请求。
 * <p>
 * `extra` 是可选字段的容器，按动作取用：61 个动作如果各开一个 DTO，
 * 会有 61 个只有一两个字段的类，Controller 还得按动作码分发到不同方法。
 * 用一个容器 + 服务端按动作读取，接口只有一条路径，前端也只需要记一个报文形状。
 *
 * @author Haijie Xiao
 * @date 2026-11-04
 */
public class TicketActionDTO {

    /** 动作码，对应 FlowAction */
    private String action;

    /**
     * 期望的前置状态。用于并发保护：与库里当前状态不一致时返回 STATE_CONFLICT。
     * 两个人同时点「确认」，后点的那个必须被挡住，否则会重复写时间线、重复推进两格。
     */
    private String expectStatus;

    /** 幂等键。同一 requestId 在 60 秒内重复提交，返回首次结果 */
    private String requestId;

    /** 附加说明 */
    private String remark;

    /** 原因码，退回与驳回必填 */
    private String reasonCode;

    /**
     * 动作专属参数：
     * ASSIGN 用 assigneeId / estimatedHours；REVIEW_CONTINUE 用 targetStatus；
     * UAT_PASS 用 waiverReason；EVAL_PASS 用 satisfactionScore；
     * UAT_RAISE_BUG 用 bugTitle / bugDescription / severity。
     */
    private Map<String, Object> extra;

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getExpectStatus() {
        return expectStatus;
    }

    public void setExpectStatus(String expectStatus) {
        this.expectStatus = expectStatus;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public String getReasonCode() {
        return reasonCode;
    }

    public void setReasonCode(String reasonCode) {
        this.reasonCode = reasonCode;
    }

    public Map<String, Object> getExtra() {
        return extra;
    }

    public void setExtra(Map<String, Object> extra) {
        this.extra = extra;
    }
}
