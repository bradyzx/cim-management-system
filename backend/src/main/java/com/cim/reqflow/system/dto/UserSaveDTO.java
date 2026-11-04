package com.cim.reqflow.system.dto;

import java.util.List;

/**
 * 用户新增 / 修改请求。
 * 新增时 username 与 realName 必填；修改时字段为空表示不修改，因此统一用包装类型。
 * 修改场景不处理 roleIds，角色单独走 /system/user/{id}/roles 接口，
 * 避免「保存用户」误把角色清空。
 *
 * @author Haijie Xiao
 * @date 2026-11-03
 */
public class UserSaveDTO {

    /** 登录账号 */
    private String username;

    /** 真实姓名 */
    private String realName;

    /** 邮箱 */
    private String email;

    /** 手机号 */
    private String mobile;

    /** 用工类型 */
    private String employmentType;

    /** 主部门 ID */
    private String primaryDeptId;

    /** 状态 */
    private String status;

    /** 备注 */
    private String remark;

    /** 角色 ID 列表，仅新增时生效 */
    private List<String> roleIds;

    /**
     * 输出关键字段，便于日志排查。
     *
     * @return 对象字符串
     */
    @Override
    public String toString() {
        return "UserSaveDTO{username=" + username
                + ", realName=" + realName
                + ", email=" + email
                + ", mobile=" + mobile
                + ", employmentType=" + employmentType
                + ", primaryDeptId=" + primaryDeptId
                + ", status=" + status
                + ", remark=" + remark
                + ", roleIds=" + roleIds + "}";
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getRealName() {
        return realName;
    }

    public void setRealName(String realName) {
        this.realName = realName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getMobile() {
        return mobile;
    }

    public void setMobile(String mobile) {
        this.mobile = mobile;
    }

    public String getEmploymentType() {
        return employmentType;
    }

    public void setEmploymentType(String employmentType) {
        this.employmentType = employmentType;
    }

    public String getPrimaryDeptId() {
        return primaryDeptId;
    }

    public void setPrimaryDeptId(String primaryDeptId) {
        this.primaryDeptId = primaryDeptId;
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

    public List<String> getRoleIds() {
        return roleIds;
    }

    public void setRoleIds(List<String> roleIds) {
        this.roleIds = roleIds;
    }
}
