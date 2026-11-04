package com.cim.reqflow.auth.vo;

import com.cim.reqflow.system.vo.UserInfoVO;

import java.util.List;

/**
 * 登录成功返回体。字段顺序即前端契约顺序：
 * 用户基本信息 + 访问令牌 + 是否必须先改密。
 *
 * @author Haijie Xiao
 * @date 2026-11-03
 */
public class LoginVO {

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

    /** 访问令牌 */
    private String accessToken;

    /** 是否必须先改密（首次登录或被管理员重置过口令） */
    private Boolean needChangePassword;

    /**
     * 由用户信息体拼装登录返回体。
     *
     * @param userInfo           用户信息
     * @param accessToken        访问令牌
     * @param needChangePassword 是否需要先改密
     * @return 登录返回体
     */
    public static LoginVO of(UserInfoVO userInfo, String accessToken, Boolean needChangePassword) {
        LoginVO loginVO = new LoginVO();
        loginVO.setId(userInfo.getId());
        loginVO.setUsername(userInfo.getUsername());
        loginVO.setRealName(userInfo.getRealName());
        loginVO.setAvatar(userInfo.getAvatar());
        loginVO.setRoles(userInfo.getRoles());
        loginVO.setHomePath(userInfo.getHomePath());
        loginVO.setAccessToken(accessToken);
        loginVO.setNeedChangePassword(needChangePassword);
        return loginVO;
    }

    /**
     * 输出关键字段，便于日志排查。
     *
     * @return 对象字符串
     */
    @Override
    public String toString() {
        return "LoginVO{id=" + id
                + ", username=" + username
                + ", realName=" + realName
                + ", avatar=" + avatar
                + ", roles=" + roles
                + ", homePath=" + homePath
                + ", accessToken=" + accessToken
                + ", needChangePassword=" + needChangePassword + "}";
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

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public Boolean getNeedChangePassword() {
        return needChangePassword;
    }

    public void setNeedChangePassword(Boolean needChangePassword) {
        this.needChangePassword = needChangePassword;
    }
}
