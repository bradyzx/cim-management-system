package com.cim.reqflow.system.vo;

import java.util.List;

/**
 * 当前登录人信息，/user/info 与登录返回共用。
 * 只放前端渲染需要的最小字段，口令哈希等敏感字段绝不出后端。
 *
 * @author Haijie Xiao
 * @date 2026-11-03
 */
public class UserInfoVO {

    /** 用户 ID */
    private String id;

    /** 登录账号 */
    private String username;

    /** 真实姓名 */
    private String realName;

    /** 头像地址 */
    private String avatar;

    /** 角色编码列表 */
    private List<String> roles;

    /** 登录后落地页 */
    private String homePath;

    /**
     * 输出关键字段，便于日志排查。
     *
     * @return 对象字符串
     */
    @Override
    public String toString() {
        return "UserInfoVO{id=" + id
                + ", username=" + username
                + ", realName=" + realName
                + ", avatar=" + avatar
                + ", roles=" + roles
                + ", homePath=" + homePath + "}";
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

    public String getAvatar() {
        return avatar;
    }

    public void setAvatar(String avatar) {
        this.avatar = avatar;
    }

    public List<String> getRoles() {
        return roles;
    }

    public void setRoles(List<String> roles) {
        this.roles = roles;
    }

    public String getHomePath() {
        return homePath;
    }

    public void setHomePath(String homePath) {
        this.homePath = homePath;
    }
}
