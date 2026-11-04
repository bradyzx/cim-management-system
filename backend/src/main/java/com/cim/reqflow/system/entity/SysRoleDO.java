package com.cim.reqflow.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 角色表实体。
 * isSystem 为 1 的内置角色不允许删除、不允许改编码：
 * 角色编码被数据范围规则与代码内判定直接引用，改了会让权限体系整体失联。
 *
 * @author Haijie Xiao
 * @date 2026-11-03
 */
@TableName("sys_role")
public class SysRoleDO {

    @TableId(type = IdType.INPUT)
    private String id;

    /** 角色编码，全局唯一 */
    private String roleCode;

    /** 角色名称 */
    private String roleName;

    /** 是否内置角色：1 是、0 否 */
    private Integer isSystem;

    /** 同层排序号 */
    private Integer sort;

    /** 状态：ACTIVE 启用、DISABLED 停用 */
    private String status;

    /** 备注 */
    private String remark;

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
        return "SysRoleDO{id=" + id
                + ", roleCode=" + roleCode
                + ", roleName=" + roleName
                + ", isSystem=" + isSystem
                + ", sort=" + sort
                + ", status=" + status
                + ", remark=" + remark
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

    public String getRoleCode() {
        return roleCode;
    }

    public void setRoleCode(String roleCode) {
        this.roleCode = roleCode;
    }

    public String getRoleName() {
        return roleName;
    }

    public void setRoleName(String roleName) {
        this.roleName = roleName;
    }

    public Integer getIsSystem() {
        return isSystem;
    }

    public void setIsSystem(Integer isSystem) {
        this.isSystem = isSystem;
    }

    public Integer getSort() {
        return sort;
    }

    public void setSort(Integer sort) {
        this.sort = sort;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
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
