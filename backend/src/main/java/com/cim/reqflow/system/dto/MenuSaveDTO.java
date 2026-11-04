package com.cim.reqflow.system.dto;

import com.cim.reqflow.common.json.FlagDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

/**
 * 菜单新增 / 修改请求。
 * 新增时 menuName 与 menuType 必填；修改不支持改上级与类型，
 * 改父级要把子树的可见性全部重算，等真有需求再做。
 * <p>
 * visible / keepAlive 前端可能提交布尔也可能提交 0/1，统一由 FlagDeserializer 收敛。
 *
 * @author Haijie Xiao
 * @date 2026-11-03
 */
public class MenuSaveDTO {

    /** 菜单名称 */
    private String menuName;

    /** 菜单类型，仅新增时生效 */
    private String menuType;

    /** 上级菜单 ID，仅新增时生效 */
    private String parentId;

    /** 路由路径 */
    private String path;

    /** 前端组件路径 */
    private String component;

    /** 权限码 */
    private String authCode;

    /** 图标 */
    private String icon;

    /** 排序号 */
    private Integer sort;

    /** 是否可见 */
    @JsonDeserialize(using = FlagDeserializer.class)
    private Integer visible;

    /** 是否缓存页面 */
    @JsonDeserialize(using = FlagDeserializer.class)
    private Integer keepAlive;

    /** 外链地址，仅 LINK 类型使用 */
    private String linkUrl;

    /** 状态 */
    private String status;

    /**
     * 输出关键字段，便于日志排查。
     *
     * @return 对象字符串
     */
    @Override
    public String toString() {
        return "MenuSaveDTO{menuName=" + menuName
                + ", menuType=" + menuType
                + ", parentId=" + parentId
                + ", path=" + path
                + ", component=" + component
                + ", authCode=" + authCode
                + ", icon=" + icon
                + ", sort=" + sort
                + ", visible=" + visible
                + ", keepAlive=" + keepAlive
                + ", linkUrl=" + linkUrl
                + ", status=" + status + "}";
    }

    public String getMenuName() {
        return menuName;
    }

    public void setMenuName(String menuName) {
        this.menuName = menuName;
    }

    public String getMenuType() {
        return menuType;
    }

    public void setMenuType(String menuType) {
        this.menuType = menuType;
    }

    public String getParentId() {
        return parentId;
    }

    public void setParentId(String parentId) {
        this.parentId = parentId;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getComponent() {
        return component;
    }

    public void setComponent(String component) {
        this.component = component;
    }

    public String getAuthCode() {
        return authCode;
    }

    public void setAuthCode(String authCode) {
        this.authCode = authCode;
    }

    public String getIcon() {
        return icon;
    }

    public void setIcon(String icon) {
        this.icon = icon;
    }

    public Integer getSort() {
        return sort;
    }

    public void setSort(Integer sort) {
        this.sort = sort;
    }

    public Integer getVisible() {
        return visible;
    }

    public void setVisible(Integer visible) {
        this.visible = visible;
    }

    public Integer getKeepAlive() {
        return keepAlive;
    }

    public void setKeepAlive(Integer keepAlive) {
        this.keepAlive = keepAlive;
    }

    public String getLinkUrl() {
        return linkUrl;
    }

    public void setLinkUrl(String linkUrl) {
        this.linkUrl = linkUrl;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
