package com.cim.reqflow.system.dto;

/**
 * 角色新增 / 修改请求。
 * 新增时 roleCode 与 roleName 必填；修改时字段为空表示不修改。
 *
 * @author Haijie Xiao
 * @date 2026-11-03
 */
public class RoleSaveDTO {

    /** 角色编码 */
    private String roleCode;

    /** 角色名称 */
    private String roleName;

    /** 排序号 */
    private Integer sort;

    /** 状态 */
    private String status;

    /** 备注 */
    private String remark;

    /**
     * 输出关键字段，便于日志排查。
     *
     * @return 对象字符串
     */
    @Override
    public String toString() {
        return "RoleSaveDTO{roleCode=" + roleCode
                + ", roleName=" + roleName
                + ", sort=" + sort
                + ", status=" + status
                + ", remark=" + remark + "}";
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
}
