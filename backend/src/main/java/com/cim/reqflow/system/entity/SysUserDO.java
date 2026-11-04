package com.cim.reqflow.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 用户表实体，与 DDL 的 sys_user 一一对应。
 * 时间为 ISO 字符串，由应用层生成后落库，不使用数据库默认值与触发器。
 * <p>
 * primaryDeptId 仅用于列表展示与表单回显，禁止参与权限计算；
 * 权限计算一律以 sys_user_dept 为准，避免「主部门」这一展示字段被当成权限来源。
 *
 * @author Haijie Xiao
 * @date 2026-11-03
 */
@TableName("sys_user")
public class SysUserDO {

    @TableId(type = IdType.INPUT)
    private String id;

    /** 登录账号，全局唯一 */
    private String username;

    /** 口令哈希，绝不返回前端 */
    private String passwordHash;

    /** 真实姓名 */
    private String realName;

    /** 昵称 */
    private String nickname;

    /** 邮箱 */
    private String email;

    /** 手机号 */
    private String mobile;

    /** 头像地址 */
    private String avatar;

    /** 认证方式：LOCAL 本地口令、AD 域账号 */
    private String authType;

    /** 外部系统账号标识，接 AD 后用于比对 */
    private String externalId;

    /** 用工类型：EMPLOYEE 正式、DISPATCH 派遣等 */
    private String employmentType;

    /** HR 工号 */
    private String hrEmploymentCode;

    /** 主部门 ID，仅用于展示，禁止参与权限计算 */
    private String primaryDeptId;

    /** 状态：ACTIVE 启用、DISABLED 停用 */
    private String status;

    /** 是否必须改密：1 是、0 否 */
    private Integer mustChangePassword;

    /** 备注 */
    private String remark;

    /** 同步来源：LOCAL 本地录入、HR 人力系统 */
    private String syncSource;

    /** 最近同步时间 */
    private String syncAt;

    /** 最近登录时间 */
    private String lastLoginAt;

    /** 创建时间，格式 yyyy-MM-dd HH:mm:ss */
    private String createdAt;

    /** 更新时间，格式 yyyy-MM-dd HH:mm:ss */
    private String updatedAt;

    /**
     * 输出关键字段，便于日志排查。
     *
     * @return 对象字符串
     */
    @Override
    public String toString() {
        return "SysUserDO{id=" + id
                + ", username=" + username
                + ", realName=" + realName
                + ", nickname=" + nickname
                + ", email=" + email
                + ", mobile=" + mobile
                + ", avatar=" + avatar
                + ", authType=" + authType
                + ", externalId=" + externalId
                + ", employmentType=" + employmentType
                + ", hrEmploymentCode=" + hrEmploymentCode
                + ", primaryDeptId=" + primaryDeptId
                + ", status=" + status
                + ", mustChangePassword=" + mustChangePassword
                + ", remark=" + remark
                + ", syncSource=" + syncSource
                + ", syncAt=" + syncAt
                + ", lastLoginAt=" + lastLoginAt
                + ", createdAt=" + createdAt
                + ", updatedAt=" + updatedAt + "}";
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

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getRealName() {
        return realName;
    }

    public void setRealName(String realName) {
        this.realName = realName;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
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

    public String getAvatar() {
        return avatar;
    }

    public void setAvatar(String avatar) {
        this.avatar = avatar;
    }

    public String getAuthType() {
        return authType;
    }

    public void setAuthType(String authType) {
        this.authType = authType;
    }

    public String getExternalId() {
        return externalId;
    }

    public void setExternalId(String externalId) {
        this.externalId = externalId;
    }

    public String getEmploymentType() {
        return employmentType;
    }

    public void setEmploymentType(String employmentType) {
        this.employmentType = employmentType;
    }

    public String getHrEmploymentCode() {
        return hrEmploymentCode;
    }

    public void setHrEmploymentCode(String hrEmploymentCode) {
        this.hrEmploymentCode = hrEmploymentCode;
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

    public String getSyncSource() {
        return syncSource;
    }

    public void setSyncSource(String syncSource) {
        this.syncSource = syncSource;
    }

    public String getSyncAt() {
        return syncAt;
    }

    public void setSyncAt(String syncAt) {
        this.syncAt = syncAt;
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

    public String getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(String updatedAt) {
        this.updatedAt = updatedAt;
    }

}
