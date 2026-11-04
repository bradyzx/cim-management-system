package com.cim.reqflow.system.dto;

/**
 * 用户启停请求。
 *
 * @author Haijie Xiao
 * @date 2026-11-03
 */
public class UserStatusDTO {

    /** 目标状态：ACTIVE 启用、DISABLED 停用 */
    private String status;

    /**
     * 输出关键字段，便于日志排查。
     *
     * @return 对象字符串
     */
    @Override
    public String toString() {
        return "UserStatusDTO{status=" + status + "}";
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
