package com.cim.reqflow.ticket.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 用例执行记录。每执行一次写一条，不覆盖历史。
 * <p>
 * 为什么单独一张表：UAT 常有多轮回归，需要看出「这次改完，哪条用例上次通过这次挂了」。
 * 只把结果记在用例行上，历史就被覆盖，回归问题查不出来。
 *
 * @author Haijie Xiao
 * @date 2026-11-05
 */
@TableName("req_case_result")
public class ReqCaseResultDO {

    @TableId(type = IdType.INPUT)
    private String id;

    /** 用例 ID */
    private String caseId;

    /** 所属单据，冗余存一份便于一次查出某单的全部执行记录 */
    private String ticketId;

    /** 第几轮 */
    private Integer roundNo;

    /** PASS / FAIL / SKIP */
    private String execStatus;

    /** 实际结果 */
    private String actualResult;

    /** 执行人 */
    private String executorId;

    /** 执行时间 */
    private String executedAt;

    /** 不通过时关联的 BUG 单 */
    private String bugId;

    /** 备注 */
    private String remark;

    private String createdAt;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getCaseId() {
        return caseId;
    }

    public void setCaseId(String caseId) {
        this.caseId = caseId;
    }

    public String getTicketId() {
        return ticketId;
    }

    public void setTicketId(String ticketId) {
        this.ticketId = ticketId;
    }

    public Integer getRoundNo() {
        return roundNo;
    }

    public void setRoundNo(Integer roundNo) {
        this.roundNo = roundNo;
    }

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

    public String getExecutorId() {
        return executorId;
    }

    public void setExecutorId(String executorId) {
        this.executorId = executorId;
    }

    public String getExecutedAt() {
        return executedAt;
    }

    public void setExecutedAt(String executedAt) {
        this.executedAt = executedAt;
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

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }
}
