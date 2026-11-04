package com.cim.reqflow.system.vo;

import java.util.List;

/**
 * 用户列表行。部门名与角色编码由 Service 批量补齐，避免每行各查一次库。
 * 不含口令哈希等敏感字段。
 *
 * @author Haijie Xiao
 * @date 2026-11-03
 */
public class UserListVO {

    /** 用户 ID */
    private String id;

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

    /** 主部门名称 */
    private String deptName;

    /** 认证方式 */
    private String authType;

    /** 状态 */
    private String status;

    /** 是否必须改密：1 是、0 否 */
    private Integer mustChangePassword;

    /** 备注 */
    private String remark;

    /** 最近登录时间 */
    private String lastLoginAt;

    /** 创建时间 */
    private String createdAt;

    /** 角色编码列表 */
    private List<String> roles;

    /**
     * 输出关键字段，便于日志排查。
     *
     * @return 对象字符串
     */
    @Override
    public String toString() {
        return "UserListVO{id=" + id
                + ", username=" + username
                + ", realName=" + realName
                + ", email=" + email
                + ", mobile=" + mobile
                + ", employmentType=" + employmentType
                + ", primaryDeptId=" + primaryDeptId
                + ", deptName=" + deptName
                + ", authType=" + authType
                + ", status=" + status
                + ", mustChangePassword=" + mustChangePassword
                + ", remark=" + remark
                + ", lastLoginAt=" + lastLoginAt
                + ", createdAt=" + createdAt
                + ", roles=" + roles + "}";
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
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

    public String getDeptName() {
        return deptName;
    }

    public void setDeptName(String deptName) {
        this.deptName = deptName;
    }

    public String getAuthType() {
        return authType;
    }

    public void setAuthType(String authType) {
        this.authType = authType;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getMustChangePassword() {
        return mustChangePassword;
    }

    public void setMustChangePassword(Integer mustChangePassword) {
        this.mustChangePassword = mustChangePassword;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public String getLastLoginAt() {
        return lastLoginAt;
    }

    public void setLastLoginAt(String lastLoginAt) {
        this.lastLoginAt = lastLoginAt;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    public List<String> getRoles() {
        return roles;
    }

    public void setRoles(List<String> roles) {
        this.roles = roles;
    }
}
