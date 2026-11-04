package com.cim.reqflow.ticket.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 需求文档实体。同单可多版本，最新版为当前版；doc_json 是 12 节结构化数据。
 *
 * @author Haijie Xiao
 * @date 2026-11-04
 */
@TableName("req_doc")
public class ReqDocDO {

    @TableId(type = IdType.INPUT)
    private String id;

    /** 所属单据 */
    private String ticketId;

    /** 版本号，重生成 +1 */
    private Integer version;

    /** Markdown 正文 */
    private String contentMd;

    /** 12 节结构化 JSON 字符串 */
    private String docJson;

    /** 生成模型标识；规则模板生成填 RULE_TEMPLATE_V1 */
    private String aiModel;

    /** 1 = 降级模板空壳 */
    private Integer aiDegraded;

    /** 提出人确认时间 */
    private String confirmedAt;

    /** 确认人 */
    private String confirmedBy;

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

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    public String getContentMd() {
        return contentMd;
    }

    public void setContentMd(String contentMd) {
        this.contentMd = contentMd;
    }

    public String getDocJson() {
        return docJson;
    }

    public void setDocJson(String docJson) {
        this.docJson = docJson;
    }

    public String getAiModel() {
        return aiModel;
    }

    public void setAiModel(String aiModel) {
        this.aiModel = aiModel;
    }

    public Integer getAiDegraded() {
        return aiDegraded;
    }

    public void setAiDegraded(Integer aiDegraded) {
        this.aiDegraded = aiDegraded;
    }

    public String getConfirmedAt() {
        return confirmedAt;
    }

    public void setConfirmedAt(String confirmedAt) {
        this.confirmedAt = confirmedAt;
    }

    public String getConfirmedBy() {
        return confirmedBy;
    }

    public void setConfirmedBy(String confirmedBy) {
        this.confirmedBy = confirmedBy;
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
     * 只输出关键字段。
     *
     * @return 关键字段字符串
     */
    @Override
    public String toString() {
        return "ReqDocDO{" + "ticketId=" + ticketId + ", " + "version=" + version + ", " + "contentMd=" + contentMd + "}";
    }
}
