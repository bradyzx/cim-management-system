package com.cim.reqflow.common;

/**
 * 业务异常。抛出后由 GlobalExceptionHandler 统一转成 code = -1 的响应。
 * httpStatus 默认 200：业务校验失败走 HTTP 200 + code = -1；
 * 只有鉴权类失败才显式传 401 / 403，因为前端拦截器依赖真实状态码判断是否跳登录。
 *
 * @author Haijie Xiao
 * @date 2026-11-03
 */
public class BizException extends RuntimeException {

    /** 序列化版本号，异常可能跨进程传递，显式声明避免反序列化不兼容 */
    private static final long serialVersionUID = 1L;

    /** HTTP 200：业务错误 */
    public static final int STATUS_BAD_REQUEST = 200;

    /** HTTP 400：入参缺失 */
    public static final int STATUS_PARAM_ERROR = 400;

    /** HTTP 401：未登录或令牌失效 */
    public static final int STATUS_UNAUTHORIZED = 401;

    /** HTTP 403：已登录但无权访问或凭证错误 */
    public static final int STATUS_FORBIDDEN = 403;

    /** 该异常对应的 HTTP 状态码 */
    private final int httpStatus;

    public BizException(String message) {
        super(message);
        this.httpStatus = STATUS_BAD_REQUEST;
    }

    public BizException(String message, int httpStatus) {
        super(message);
        this.httpStatus = httpStatus;
    }

    /**
     * 保留原始异常的构造器。用于把 IO、算法不可用这类底层失败包装成业务异常，
     * 同时不丢堆栈 —— 只写一句 message 会让排查退化成猜。
     *
     * @param message 面向用户的提示
     * @param cause   原始异常
     */
    public BizException(String message, Throwable cause) {
        super(message, cause);
        this.httpStatus = STATUS_BAD_REQUEST;
    }

    public int getHttpStatus() {
        return httpStatus;
    }
}
