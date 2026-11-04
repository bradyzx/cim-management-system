package com.cim.reqflow.ticket.vo;

/**
 * 附件视图对象。按节点分组展示，带中文标签与可删除标记，前端不再自己查字典。
 *
 * @author Haijie Xiao
 * @date 2026-11-04
 */
public class AttachmentVO {

    /** 附件 ID */
    private String id;

    /** 所属单据 */
    private String ticketId;

    /** 上传时所处状态码 */
    private String stage;

    /** 状态中文名 */
    private String stageLabel;

    /** 交付物类型码 */
    private String attachType;

    /** 交付物类型中文名 */
    private String attachTypeLabel;

    /** 原始文件名 */
    private String originalName;

    /** 字节数 */
    private Long size;

    /** 可读大小，如 1.2 MB */
    private String sizeText;

    /** 媒体类型 */
    private String mimeType;

    /** 上传人 ID */
    private String uploaderId;

    /** 上传人姓名 */
    private String uploaderName;

    /** 是否确认版。同单同类型只有一个 */
    private Boolean confirmed;

    /** 当前用户能否删除 */
    private Boolean canDelete;

    /** 磁盘文件是否已丢失 */
    private Boolean fileMissing;

    /** 是否已提取正文，可供 AI 分析 */
    private Boolean textExtracted;

    /** 上传时间 */
    private String createdAt;

    /** 下载地址 */
    private String downloadUrl;

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

    public String getStage() {
        return stage;
    }

    public void setStage(String stage) {
        this.stage = stage;
    }

    public String getStageLabel() {
        return stageLabel;
    }

    public void setStageLabel(String stageLabel) {
        this.stageLabel = stageLabel;
    }

    public String getAttachType() {
        return attachType;
    }

    public void setAttachType(String attachType) {
        this.attachType = attachType;
    }

    public String getAttachTypeLabel() {
        return attachTypeLabel;
    }

    public void setAttachTypeLabel(String attachTypeLabel) {
        this.attachTypeLabel = attachTypeLabel;
    }

    public String getOriginalName() {
        return originalName;
    }

    public void setOriginalName(String originalName) {
        this.originalName = originalName;
    }

    public Long getSize() {
        return size;
    }

    public void setSize(Long size) {
        this.size = size;
    }

    public String getSizeText() {
        return sizeText;
    }

    public void setSizeText(String sizeText) {
        this.sizeText = sizeText;
    }

    public String getMimeType() {
        return mimeType;
    }

    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }

    public String getUploaderId() {
        return uploaderId;
    }

    public void setUploaderId(String uploaderId) {
        this.uploaderId = uploaderId;
    }

    public String getUploaderName() {
        return uploaderName;
    }

    public void setUploaderName(String uploaderName) {
        this.uploaderName = uploaderName;
    }

    public Boolean getConfirmed() {
        return confirmed;
    }

    public void setConfirmed(Boolean confirmed) {
        this.confirmed = confirmed;
    }

    public Boolean getCanDelete() {
        return canDelete;
    }

    public void setCanDelete(Boolean canDelete) {
        this.canDelete = canDelete;
    }

    public Boolean getFileMissing() {
        return fileMissing;
    }

    public void setFileMissing(Boolean fileMissing) {
        this.fileMissing = fileMissing;
    }

    public Boolean getTextExtracted() {
        return textExtracted;
    }

    public void setTextExtracted(Boolean textExtracted) {
        this.textExtracted = textExtracted;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    public String getDownloadUrl() {
        return downloadUrl;
    }

    public void setDownloadUrl(String downloadUrl) {
        this.downloadUrl = downloadUrl;
    }

    /**
     * 只输出关键字段。
     *
     * @return 关键字段字符串
     */
    @Override
    public String toString() {
        return "AttachmentVO{" + "id=" + id + ", " + "ticketId=" + ticketId + ", " + "stage=" + stage + ", " + "stageLabel=" + stageLabel + "}";
    }
}
