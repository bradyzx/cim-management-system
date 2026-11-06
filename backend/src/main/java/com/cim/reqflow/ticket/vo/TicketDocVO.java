package com.cim.reqflow.ticket.vo;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

/**
 * 需求文档视图。
 * <p>
 * 除原始数据外多带三个「前端要用、但不该由前端自己算」的字段：
 * `editable` 由状态与确认时间决定、`missingSections` / `missingMessages`
 * 由确认校验规则决定。前端拿到就能直接标红，不用把业务规则抄一遍。
 *
 * @author Haijie Xiao
 * @date 2026-11-05
 */
public class TicketDocVO {

    /** 文档 ID */
    private String id;

    /** 所属单据 */
    private String ticketId;

    /** 版本号，重新生成为 1，人工编辑不升版 */
    private Integer version;

    /** Markdown 正文，由后端从 docJson 渲染，与结构化数据始终一致 */
    private String contentMd;

    /** 12 节结构化数据，已解析为对象 */
    private JsonNode docJson;

    /** 生成模型；人工编辑后仍保留原始生成模型，便于追溯 AI 参与程度 */
    private String aiModel;

    /** 1 = 降级模板空壳。人工编辑保存后置 0 */
    private Integer aiDegraded;

    /** 确认时间，非空即锁定 */
    private String confirmedAt;

    /** 确认人 ID */
    private String confirmedBy;

    /** 确认人姓名 */
    private String confirmedByName;

    /** 当前是否可编辑：仅「需求文档待确认」且尚未确认时为 true */
    private Boolean editable;

    /** 若现在点确认会挡住哪些节，空表示能过 */
    private List<String> missingSections;

    /** 每个阻塞项的可读原因 */
    private List<String> missingMessages;

    /** 单据当前状态，前端据此决定「去确认」按钮是否出现 */
    private String ticketStatus;

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

    public JsonNode getDocJson() {
        return docJson;
    }

    public void setDocJson(JsonNode docJson) {
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

    public String getConfirmedByName() {
        return confirmedByName;
    }

    public void setConfirmedByName(String confirmedByName) {
        this.confirmedByName = confirmedByName;
    }

    public Boolean getEditable() {
        return editable;
    }

    public void setEditable(Boolean editable) {
        this.editable = editable;
    }

    public List<String> getMissingSections() {
        return missingSections;
    }

    public void setMissingSections(List<String> missingSections) {
        this.missingSections = missingSections;
    }

    public List<String> getMissingMessages() {
        return missingMessages;
    }

    public void setMissingMessages(List<String> missingMessages) {
        this.missingMessages = missingMessages;
    }

    public String getTicketStatus() {
        return ticketStatus;
    }

    public void setTicketStatus(String ticketStatus) {
        this.ticketStatus = ticketStatus;
    }
}
