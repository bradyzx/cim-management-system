package com.cim.reqflow.ticket.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 验收标准子表。逐条结构化存储，是用例覆盖率的分母、UAT 逐条勾选的依据。
 *
 * @author Haijie Xiao
 * @date 2026-11-04
 */
@TableName("req_acceptance_criteria")
public class ReqAcceptanceCriteriaDO {

    @TableId(type = IdType.INPUT)
    private String id;

    /** 所属单据 */
    private String ticketId;

    /** 第几条，从 1 开始；与用例的 criteria_index 对应 */
    private Integer seq;

    /** 单条验收标准 */
    private String content;

    /** 是否已锁定：1 是。设计评审通过后锁定 */
    private Integer locked;

    /** 创建时间 */
    private String createdAt;

    /** 更新时间 */
    private String updatedAt;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTicketId() {
        return ticketId;
    }

    public void setTicketId(String ticketId) {
        this.ticketId = ticketId;
    }

    public Integer getSeq() {
        return seq;
    }

    public void setSeq(Integer seq) {
        this.seq = seq;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Integer getLocked() {
        return locked;
    }

    public void setLocked(Integer locked) {
        this.locked = locked;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    public String getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(String updatedAt) {
        this.updatedAt = updatedAt;
    }

    /**
     * 只输出关键字段，避免长文本（备注、正文）把日志刷屏。
     * 手册要求 POJO 必须写 toString，裁剪范围在此说明。
     *
     * @return 关键字段字符串
     */
    @Override
    public String toString() {
        return "ReqAcceptanceCriteriaDO{" + "ticketId=" + ticketId + ", " + "seq=" + seq + ", " + "content=" + content + "}";
    }
}
