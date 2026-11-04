package com.cim.reqflow.system.dto;

import java.util.List;

/**
 * 用户角色分配请求。传空列表表示解除全部角色绑定。
 *
 * @author Haijie Xiao
 * @date 2026-11-03
 */
public class UserRoleDTO {

    /** 角色 ID 列表 */
    private List<String> roleIds;

    /**
     * 输出关键字段，便于日志排查。
     *
     * @return 对象字符串
     */
    @Override
    public String toString() {
        return "UserRoleDTO{roleIds=" + roleIds + "}";
    }

    public List<String> getRoleIds() {
        return roleIds;
    }

    public void setRoleIds(List<String> roleIds) {
        this.roleIds = roleIds;
    }
}
