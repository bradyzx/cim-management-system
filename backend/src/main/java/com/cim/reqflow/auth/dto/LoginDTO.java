package com.cim.reqflow.auth.dto;

/**
 * 登录请求。
 * 不提供 toString：请求体含明文口令，一旦被日志框架打印就是安全事故。
 *
 * @author Haijie Xiao
 * @date 2026-11-03
 */
public class LoginDTO {

    /** 登录账号 */
    private String username;

    /** 明文口令 */
    private String password;

    /**
     * 输出关键字段，便于日志排查。
     *
     * @return 对象字符串
     */
    @Override
    public String toString() {
        return "LoginDTO{username=" + username + "}";
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
