package com.cim.reqflow.system.dto;

import java.util.List;

/**
 * 角色菜单授权请求。前端树形勾选只传勾中的节点即可，父节点链由后端补全。
 *
 * @author Haijie Xiao
 * @date 2026-11-03
 */
public class RoleMenuDTO {

    /** 菜单 ID 列表 */
    private List<String> menuIds;

    /**
     * 输出关键字段，便于日志排查。
     *
     * @return 对象字符串
     */
    @Override
    public String toString() {
        return "RoleMenuDTO{menuIds=" + menuIds + "}";
    }

    public List<String> getMenuIds() {
        return menuIds;
    }

    public void setMenuIds(List<String> menuIds) {
        this.menuIds = menuIds;
    }
}
