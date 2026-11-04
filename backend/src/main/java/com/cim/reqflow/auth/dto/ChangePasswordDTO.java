package com.cim.reqflow.auth.dto;

/**
 * 自助改密请求。
 * 两个字段均为明文口令，属敏感数据。
 *
 * @author Haijie Xiao
 * @date 2026-11-04
 */
public class ChangePasswordDTO {

    /** 原口令（明文，不落库、不写日志） */
    private String oldPassword;

    /** 新口令（明文，不落库、不写日志） */
    private String newPassword;

    /**
     * 只输出是否已填值，绝不输出明文口令。
     * 手册要求 POJO 必须写 toString，同时对敏感数据脱敏，两者在此合并处理。
     *
     * @return 脱敏后的对象字符串
     */
    @Override
    public String toString() {
        return "ChangePasswordDTO{oldPasswordProvided=" + (oldPassword != null)
                + ", newPasswordProvided=" + (newPassword != null) + "}";
    }

    public String getOldPassword() {
        return oldPassword;
    }

    public void setOldPassword(String oldPassword) {
        this.oldPassword = oldPassword;
    }

    public String getNewPassword() {
        return newPassword;
    }

    public void setNewPassword(String newPassword) {
        this.newPassword = newPassword;
    }
}
