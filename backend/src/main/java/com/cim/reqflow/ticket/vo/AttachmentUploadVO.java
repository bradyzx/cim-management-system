package com.cim.reqflow.ticket.vo;

/**
 * 上传结果。命中重复时不报错而是返回已有附件并标记 duplicated，让用户知道「这份已经在里面了」。
 *
 * @author Haijie Xiao
 * @date 2026-11-04
 */
public class AttachmentUploadVO {

    /** 落库后的附件 */
    private AttachmentVO attachment;

    /** 是否命中同单同类型同指纹的已有附件 */
    private Boolean duplicated;

    public AttachmentVO getAttachment() {
        return attachment;
    }

    public void setAttachment(AttachmentVO attachment) {
        this.attachment = attachment;
    }

    public Boolean getDuplicated() {
        return duplicated;
    }

    public void setDuplicated(Boolean duplicated) {
        this.duplicated = duplicated;
    }

    /**
     * 只输出关键字段。
     *
     * @return 关键字段字符串
     */
    @Override
    public String toString() {
        return "AttachmentUploadVO{" + "attachment=" + attachment + ", " + "duplicated=" + duplicated + "}";
    }
}
