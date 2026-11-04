package com.cim.reqflow.ticket.vo;

import com.cim.reqflow.ticket.flow.Blocker;

import java.util.List;

/**
 * 当前用户在某张单上可执行的动作。是前端按钮渲染的唯一来源。
 * <p>
 * 不在角色白名单里的动作不会出现在返回列表里（不是返回 enabled=false），
 * 前端无需自己过滤；enabled=false 的动作一定带 blockers，且可能有多条。
 *
 * @author Haijie Xiao
 * @date 2026-11-04
 */
public class ActionVO {

    /** 动作码 */
    private String action;

    /** 按钮文案 */
    private String label;

    /** 按钮权限码 */
    private String authCode;

    /** 是否可点。false 时 blockers 非空 */
    private Boolean enabled;

    /** 阻塞原因，可多条 */
    private List<Blocker> blockers;

    /**
     * 退回点码，非退回类动作为 null。
     * <p>
     * 前端靠它判断「这个动作必须选原因」：退回点一共 17 个，动作码命名又不统一
     * （RETURN / REJECT / FAIL / REFUSE 都有），按后缀猜必漏。
     */
    private String returnPoint;

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getAuthCode() {
        return authCode;
    }

    public void setAuthCode(String authCode) {
        this.authCode = authCode;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public List<Blocker> getBlockers() {
        return blockers;
    }

    public void setBlockers(List<Blocker> blockers) {
        this.blockers = blockers;
    }

    public String getReturnPoint() {
        return returnPoint;
    }

    public void setReturnPoint(String returnPoint) {
        this.returnPoint = returnPoint;
    }
}
