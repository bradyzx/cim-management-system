package com.cim.reqflow.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 角色-菜单授权关系表实体。
 * 本表只有关联意义、无独立生命周期：授权走「全删全插」，角色或菜单删除时同步清理。
 *
 * @author Haijie Xiao
 * @date 2026-11-03
 */
@TableName("sys_role_menu")
public class SysRoleMenuDO {

    @TableId(type = IdType.INPUT)
    private String id;

    /** 角色 ID */
    private String roleId;

    /** 菜单 ID */
    private String menuId;

    /** 创建时间，格式 yyyy-MM-dd HH:mm:ss */
    private String createdAt;

    /**
     * 输出关键字段，便于日志排查。
     *
     * @return 对象字符串
     */
    @Override
    public String toString() {
        return "SysRoleMenuDO{id=" + id
                + ", roleId=" + roleId
                + ", menuId=" + menuId
                + ", createdAt=" + createdAt + "}";
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getRoleId() {
        return roleId;
    }

    public void setRoleId(String roleId) {
        this.roleId = roleId;
    }

    public String getMenuId() {
        return menuId;
    }

    public void setMenuId(String menuId) {
        this.menuId = menuId;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }
}
