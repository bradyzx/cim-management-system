package com.cim.reqflow.ticket.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 附件实体。按节点归档：stage 记上传时所处状态，attachType 记交付物类型。
 *
 * @author Haijie Xiao
 * @date 2026-11-04
 */
@TableName("req_attachment")
public class ReqAttachmentDO {

    @TableId(type = IdType.INPUT)
    private String id;

    /** 所属单据 */
    private String ticketId;

    /** 上传时所处的状态码，用于按节点分组展示 */
    private String stage;

    /** 交付物类型，走字典 attach_type */
    private String attachType;

    /** 原始文件名，下载时还原 */
    private String originalName;

    /** 相对路径，绝对路径在配置里，换机器不用改数据 */
    private String relativePath;

    /** 字节数 */
    private Long size;

    /** 媒体类型 */
    private String mimeType;

    /** 文件指纹，用于重复上传提示与完整性校验 */
    private String sha256;

    /** 正文提取结果，供 AI 分析使用 */
    private String extractedText;

    /** 上传人 */
    private String uploaderId;

    /** 是否确认版：1 是。同单同类型只允许一个 */
    private Integer isConfirmed;

    /** 是否软删除：1 是。附件是审计证据，不物理删 */
    private Integer deleted;

    /** 上传时间 */
    private String createdAt;

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

    public String getAttachType() {
        return attachType;
    }

    public void setAttachType(String attachType) {
        this.attachType = attachType;
    }

    public String getOriginalName() {
        return originalName;
    }

    public void setOriginalName(String originalName) {
        this.originalName = originalName;
    }

    public String getRelativePath() {
        return relativePath;
    }

    public void setRelativePath(String relativePath) {
        this.relativePath = relativePath;
    }

    public Long getSize() {
        return size;
    }

    public void setSize(Long size) {
        this.size = size;
    }

    public String getMimeType() {
        return mimeType;
    }

    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }

    public String getSha256() {
        return sha256;
    }

    public void setSha256(String sha256) {
        this.sha256 = sha256;
    }

    public String getExtractedText() {
        return extractedText;
    }

    public void setExtractedText(String extractedText) {
        this.extractedText = extractedText;
    }

    public String getUploaderId() {
        return uploaderId;
    }

    public void setUploaderId(String uploaderId) {
        this.uploaderId = uploaderId;
    }

    public Integer getIsConfirmed() {
        return isConfirmed;
    }

    public void setIsConfirmed(Integer isConfirmed) {
        this.isConfirmed = isConfirmed;
    }

    public Integer getDeleted() {
        return deleted;
    }

    public void setDeleted(Integer deleted) {
        this.deleted = deleted;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    /**
     * 只输出关键字段，避免长文本（备注、正文）把日志刷屏。
     * 手册要求 POJO 必须写 toString，裁剪范围在此说明。
     *
     * @return 关键字段字符串
     */
    @Override
    public String toString() {
        return "ReqAttachmentDO{" + "ticketId=" + ticketId + ", " + "attachType=" + attachType + ", " + "stage=" + stage + ", " + "originalName=" + originalName + "}";
    }
}
