package com.cim.reqflow.ticket.vo;

import java.util.List;

/**
 * 步骤条的一个节点，供详情页鼠标悬停时展示。
 * <p>
 * 一个节点装四件事，顺序就是用户看图的顺序：
 * 这一步谁在处理（人）→ 需要交什么（需求）→ 已经交了什么（已传）→ 还差什么（缺口）→ 交给谁（下游）。
 * <p>
 * 人是具体账号，不是岗位：「MIT 开发」这种写法在图上没用，用户要知道现在压在谁手上。
 * 已走过的步骤给人名，没走到的是按角色推算的预计接手人。
 *
 * @author Haijie Xiao
 * @date 2026-11-05
 */
public class FlowStepVO {

    /** 步骤键，前端做 key 用 */
    private String key;

    /** 步骤名，如「设计」 */
    private String label;

    /** 该步覆盖的流程节点编号，如 ["N13", "N14"]，悬停时列出来 */
    private List<String> nodeCodes;

    /** 进度：`DONE` 已走过 / `CURRENT` 停在这里 / `TODO` 还没到 */
    private String state;

    /** 实际处理过的人，只写姓名，系统动作记「系统」 */
    private List<String> handlers;

    /**
     * 预计处理人。
     * <p>
     * 还没走到的步骤只给这一项；已经走到但还没人动过的步骤（刚进入）也给这一项，
     * 前端按 handlers 是否为空决定显示「处理」还是「预计」。
     */
    private List<String> plannedHandlers;

    /** 这一步需要什么材料，取环节指引里的人话描述 */
    private List<String> need;

    /** 已上传的附件，格式「文件名（类型）」 */
    private List<String> uploaded;

    /** 必填但还没交的材料类型名 */
    private List<String> missing;

    /** 做完交给谁，取下一步的实际或预计处理人 */
    private List<String> handoff;

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public List<String> getNodeCodes() {
        return nodeCodes;
    }

    public void setNodeCodes(List<String> nodeCodes) {
        this.nodeCodes = nodeCodes;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public List<String> getHandlers() {
        return handlers;
    }

    public void setHandlers(List<String> handlers) {
        this.handlers = handlers;
    }

    public List<String> getPlannedHandlers() {
        return plannedHandlers;
    }

    public void setPlannedHandlers(List<String> plannedHandlers) {
        this.plannedHandlers = plannedHandlers;
    }

    public List<String> getNeed() {
        return need;
    }

    public void setNeed(List<String> need) {
        this.need = need;
    }

    public List<String> getUploaded() {
        return uploaded;
    }

    public void setUploaded(List<String> uploaded) {
        this.uploaded = uploaded;
    }

    public List<String> getMissing() {
        return missing;
    }

    public void setMissing(List<String> missing) {
        this.missing = missing;
    }

    public List<String> getHandoff() {
        return handoff;
    }

    public void setHandoff(List<String> handoff) {
        this.handoff = handoff;
    }
}
