package com.cim.reqflow.common;

import com.cim.reqflow.common.constant.CommonConstants;

/**
 * 统一响应体，结构与前端 mock 契约完全一致：
 * 成功 { code: 0, data: ..., error: null, message: "ok" }
 * 失败 { code: -1, data: null, error: "...", message: "..." }
 * 前端以 code == 0 判定成功，不看 HTTP 状态码（401/403 除外）。
 *
 * @param <T> 业务数据类型
 * @author Haijie Xiao
 * @date 2026-11-03
 */
public class R<T> {

    /** 业务码，0 成功、-1 失败 */
    private int code;

    /** 业务数据 */
    private T data;

    /** 错误标识，前端用于分支判断 */
    private String error;

    /** 提示语，可直接展示给用户 */
    private String message;

    /**
     * 业务错误码，成功时为 null。
     * 只在需要前端做机器判断时返回（ATTACH_REQUIRED 要定位附件区、
     * STATE_CONFLICT 要刷新详情），普通错误不带，避免前端为每个接口写分支。
     */
    private String bizCode;

    /**
     * 错误附加数据，随 bizCode 一起返回。
     * 例如附件缺失时带 attachType / minCount / uploaded，
     * 前端据此直接把用户带到附件区并高亮缺失类型。
     */
    private Object details;

    public R() {
    }

    public R(int code, T data, String error, String message) {
        this.code = code;
        this.data = data;
        this.error = error;
        this.message = message;
    }

    /**
     * 成功响应，携带数据。
     *
     * @param data 业务数据
     * @param <T>  业务数据类型
     * @return 统一响应体
     */
    public static <T> R<T> ok(T data) {
        return new R<>(CommonConstants.SUCCESS_CODE, data, null, CommonConstants.SUCCESS_MESSAGE);
    }

    /**
     * 成功响应，无数据。
     *
     * @return 统一响应体
     */
    public static R<Void> ok() {
        return new R<>(CommonConstants.SUCCESS_CODE, null, null, CommonConstants.SUCCESS_MESSAGE);
    }

    /**
     * 失败响应，error 与 message 同值。
     *
     * @param message 提示语
     * @param <T>     业务数据类型
     * @return 统一响应体
     */
    public static <T> R<T> fail(String message) {
        return new R<>(CommonConstants.FAIL_CODE, null, message, message);
    }

    /**
     * 失败响应，error 与 message 分开。
     *
     * @param error   错误标识
     * @param message 提示语
     * @param <T>     业务数据类型
     * @return 统一响应体
     */
    public static <T> R<T> fail(String error, String message) {
        return new R<>(CommonConstants.FAIL_CODE, null, error, message);
    }

    /**
     * 失败响应，带业务错误码与附加数据。
     *
     * @param bizCode 业务错误码
     * @param message 提示语
     * @param details 附加数据，可为 null
     * @param <T>     业务数据类型
     * @return 统一响应体
     */
    public static <T> R<T> fail(String bizCode, String message, Object details) {
        R<T> response = new R<>(CommonConstants.FAIL_CODE, null, message, message);
        response.setBizCode(bizCode);
        response.setDetails(details);
        return response;
    }

    /**
     * 输出关键字段，便于日志排查。
     *
     * @return 对象字符串
     */
    @Override
    public String toString() {
        return "R{code=" + code
                + ", data=" + data
                + ", error=" + error
                + ", message=" + message
                + ", bizCode=" + bizCode + "}";
    }

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getBizCode() {
        return bizCode;
    }

    public void setBizCode(String bizCode) {
        this.bizCode = bizCode;
    }

    public Object getDetails() {
        return details;
    }

    public void setDetails(Object details) {
        this.details = details;
    }
}
