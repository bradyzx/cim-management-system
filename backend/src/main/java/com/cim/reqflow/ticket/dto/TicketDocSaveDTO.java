package com.cim.reqflow.ticket.dto;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 需求文档保存请求。
 * <p>
 * 只收结构化数据，不收 Markdown 正文 —— 正文由后端从 `docJson` 现算（{@code DocMarkdown}）。
 * 契约 `11` §8.2 的请求体写了 `contentMd`，这里刻意不采纳：让前端同时维护表单和
 * 一段 Markdown，用户在表单里改了验收标准、正文还是旧的，两份必然漂移。
 * 前端多传的字段被 Jackson 忽略，不会报错。
 * <p>
 * `docJson` 收 JSON 对象而不是字符串：拼字符串多一层转义，少个引号整份文档就废了。
 *
 * @author Haijie Xiao
 * @date 2026-11-05
 */
public class TicketDocSaveDTO {

    /** 12 节结构化数据，必须有 sections */
    private JsonNode docJson;

    public JsonNode getDocJson() {
        return docJson;
    }

    public void setDocJson(JsonNode docJson) {
        this.docJson = docJson;
    }
}
