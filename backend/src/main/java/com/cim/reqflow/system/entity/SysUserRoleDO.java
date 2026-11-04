package com.cim.reqflow.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 用户-角色关系表实体。
 * deptId 非空表示该角色只在指定部门内生效（兼岗场景）；
 * 全局角色（如 ADMIN）留空，绑定部门反而会把它限制在单个部门。
 *
 * @author Haijie Xiao
 * @date 2026-11-03
 */
@TableName("sys_user_role")
public class SysUserRoleDO {

    @TableId(type = IdType.INPUT)
    private String id;

    /** 用户 ID */
    private String userId;

    /** 角色 ID */
    private String roleId;

    /** 生效部门 ID，为空表示全局生效 */
    private String deptId;

    /** 创建时间，格式 yyyy-MM-dd HH:mm:ss */
    private String createdAt;

    /**
     * 输出关键字段，便于日志排查。
     *
     * @return 对象字符串
     */
    @Override
    public String toString() {
        return "SysUserRoleDO{id=" + id
                + ", userId=" + userId
                + ", roleId=" + roleId
                + ", deptId=" + deptId
                + ", createdAt=" + createdAt + "}";
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getRoleId() {
        return roleId;
    }

    public void setRoleId(String roleId) {
        this.roleId = roleId;
    }

    public String getDeptId() {
        return deptId;
    }

    public void setDeptId(String deptId) {
        this.deptId = deptId;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }
}
