package com.cim.reqflow.ticket.vo;

/**
 * 建单结果。
 *
 * @author Haijie Xiao
 * @date 2026-11-04
 */
public class TicketCreatedVO {

    /** 单据 ID，前端据此跳详情 */
    private String id;

    /** 单据编号 */
    private String ticketNo;

    /** 提交后的状态码，正常为 REQ_DOC_DRAFTED */
    private String status;

    /** 状态中文名 */
    private String statusLabel;

    /** 查重最高加权相似度；未参与查重（如坚持新建）也返回当时值 */
    private Double maxSimilarity;

    /** 相似需求列表（已持久化前的返回用），正常建单后为空 */
    private Object similarList;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTicketNo() {
        return ticketNo;
    }

    public void setTicketNo(String ticketNo) {
        this.ticketNo = ticketNo;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getStatusLabel() {
        return statusLabel;
    }

    public void setStatusLabel(String statusLabel) {
        this.statusLabel = statusLabel;
    }

    public Double getMaxSimilarity() {
        return maxSimilarity;
    }

    public void setMaxSimilarity(Double maxSimilarity) {
        this.maxSimilarity = maxSimilarity;
    }

    public Object getSimilarList() {
        return similarList;
    }

    public void setSimilarList(Object similarList) {
        this.similarList = similarList;
    }

    /**
     * 只输出编号与状态。
     *
     * @return 关键字段字符串
     */
    @Override
    public String toString() {
        return "TicketCreatedVO{ticketNo=" + ticketNo + ", status=" + status + "}";
    }
}
