package com.cim.reqflow.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 菜单表实体。menuType 五种：
 * DIR 目录、MENU 菜单、BUTTON 按钮、EMBED 内嵌页、LINK 外链。
 * 只有 DIR 与 MENU 会下发到前端路由树，BUTTON 只作为权限码使用。
 *
 * @author Haijie Xiao
 * @date 2026-11-03
 */
@TableName("sys_menu")
public class SysMenuDO {

    @TableId(type = IdType.INPUT)
    private String id;

    /** 上级菜单 ID，根节点为空 */
    private String parentId;

    /** 菜单名称 */
    private String menuName;

    /** 菜单类型，取值见类注释 */
    private String menuType;

    /** 路由路径，BUTTON 类型为空 */
    private String path;

    /** 前端组件路径 */
    private String component;

    /** 重定向路径 */
    private String redirect;

    /** 权限码，前端按钮级显隐依据 */
    private String authCode;

    /** 图标 */
    private String icon;

    /** 同层排序号 */
    private Integer sort;

    /** 是否可见：1 可见、0 隐藏 */
    private Integer visible;

    /** 是否缓存页面：1 缓存、0 不缓存 */
    private Integer keepAlive;

    /** 外链地址，仅 LINK 类型使用 */
    private String linkUrl;

    /** 状态：ACTIVE 启用、DISABLED 停用 */
    private String status;

    /** 创建时间，格式 yyyy-MM-dd HH:mm:ss */
    private String createdAt;

    /** 更新时间，格式 yyyy-MM-dd HH:mm:ss */
    private String updatedAt;

    /** 逻辑删除标志：1 已删除、0 正常 */
    private Integer isDeleted;

    /**
     * 输出关键字段，便于日志排查。
     *
     * @return 对象字符串
     */
    @Override
    public String toString() {
        return "SysMenuDO{id=" + id
                + ", parentId=" + parentId
                + ", menuName=" + menuName
                + ", menuType=" + menuType
                + ", path=" + path
                + ", component=" + component
                + ", redirect=" + redirect
                + ", authCode=" + authCode
                + ", icon=" + icon
                + ", sort=" + sort
                + ", visible=" + visible
                + ", keepAlive=" + keepAlive
                + ", linkUrl=" + linkUrl
                + ", status=" + status
                + ", createdAt=" + createdAt
                + ", updatedAt=" + updatedAt
                + ", isDeleted=" + isDeleted + "}";
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getParentId() {
        return parentId;
    }

    public void setParentId(String parentId) {
        this.parentId = parentId;
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

    public String getRedirect() {
        return redirect;
    }

    public void setRedirect(String redirect) {
        this.redirect = redirect;
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

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    public String getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(String updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Integer getIsDeleted() {
        return isDeleted;
    }

    public void setIsDeleted(Integer isDeleted) {
        this.isDeleted = isDeleted;
    }
}
