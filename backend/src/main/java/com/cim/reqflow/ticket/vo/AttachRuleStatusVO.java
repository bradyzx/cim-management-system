package com.cim.reqflow.ticket.vo;

/**
 * 附件必填缺口，按「目标状态」分组。
 * <p>
 * 规则挂在到达状态上（见 07 §2.3），所以详情页要说清的是
 * 「想走到下一步，还差哪份材料」，而不是「进入当前这一步时交过什么」。
 * 因此本对象必须带 targetStatus，前端据此分组展示。
 * <p>
 * 与流转拦截读同一张表，不会出现界面显示齐了、点按钮被拦下的情况。
 *
 * @author Haijie Xiao
 * @date 2026-11-04
 */
public class AttachRuleStatusVO {

    /** 目标状态码：想走到哪一步需要这份材料 */
    private String targetStatus;

    /** 目标状态中文名 */
    private String targetStatusLabel;

    /** 交付物类型码 */
    private String attachType;

    /** 交付物类型中文名 */
    private String attachTypeLabel;

    /** 是否强制：false 表示仅提示 */
    private Boolean required;

    /** 至少几份 */
    private Integer minCount;

    /** 已上传几份 */
    private Integer uploaded;

    /** 是否已满足 */
    private Boolean satisfied;

    public String getTargetStatus() {
        return targetStatus;
    }

    public void setTargetStatus(String targetStatus) {
        this.targetStatus = targetStatus;
    }

    public String getTargetStatusLabel() {
        return targetStatusLabel;
    }

    public void setTargetStatusLabel(String targetStatusLabel) {
        this.targetStatusLabel = targetStatusLabel;
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

    public Boolean getRequired() {
        return required;
    }

    public void setRequired(Boolean required) {
        this.required = required;
    }

    public Integer getMinCount() {
        return minCount;
    }

    public void setMinCount(Integer minCount) {
        this.minCount = minCount;
    }

    public Integer getUploaded() {
        return uploaded;
    }

    public void setUploaded(Integer uploaded) {
        this.uploaded = uploaded;
    }

    public Boolean getSatisfied() {
        return satisfied;
    }

    public void setSatisfied(Boolean satisfied) {
        this.satisfied = satisfied;
    }

    /**
     * 只输出目标状态与类型，缺口状态一眼可见。
     *
     * @return 关键字段字符串
     */
    @Override
    public String toString() {
        return "AttachRuleStatusVO{targetStatus=" + targetStatus + ", attachType=" + attachType
                + ", uploaded=" + uploaded + ", minCount=" + minCount + "}";
    }
}
