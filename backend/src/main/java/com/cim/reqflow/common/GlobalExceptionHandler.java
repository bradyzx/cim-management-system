package com.cim.reqflow.common;

import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 全局异常处理器。约定：
 * 业务错误一律 HTTP 200 + code = -1，前端据此走统一错误提示；
 * 只有 401（未登录）与 403（越权、凭证错误）才真正改写 HTTP 状态码。
 *
 * @author Haijie Xiao
 * @date 2026-11-03
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 日志句柄 */
    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * 带业务码的异常：把 bizCode 与 details 一并返回，供前端做机器判断。
     * <p>
     * Spring 会优先匹配最具体的异常类型，所以本方法在 handleBizException 之前生效，
     * 不用手动 instanceof 分支。
     *
     * @param exception 业务异常
     * @param response  HTTP 响应，用于改写状态码
     * @return 统一响应体
     */
    @ExceptionHandler(BizCodeException.class)
    public R<Void> handleBizCodeException(BizCodeException exception,
                                          HttpServletResponse response) {
        response.setStatus(exception.getHttpStatus());
        return R.fail(exception.getBizCode(), exception.getMessage(), exception.getDetails());
    }

    /**
     * 业务异常：状态码由异常自带，响应体固定 code = -1。
     *
     * @param bizException 业务异常
     * @param response     HTTP 响应，用于改写状态码
     * @return 统一响应体
     */
    @ExceptionHandler(BizException.class)
    public R<Void> handleBizException(BizException bizException, HttpServletResponse response) {
        response.setStatus(bizException.getHttpStatus());
        return R.fail(bizException.getMessage());
    }

    /**
     * 参数校验失败：只回第一条提示，避免前端弹出一串。
     *
     * @param exception 校验异常
     * @return 统一响应体
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public R<Void> handleValidationException(MethodArgumentNotValidException exception) {
        if (exception.getBindingResult().getFieldErrors().isEmpty()) {
            return R.fail("参数校验失败");
        }
        String message = exception.getBindingResult().getFieldErrors().get(0).getDefaultMessage();
        return R.fail(message);
    }

    /**
     * 缺少必填请求参数。
     *
     * @param exception 缺参异常
     * @return 统一响应体
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public R<Void> handleMissingParamException(MissingServletRequestParameterException exception) {
        return R.fail("缺少必填参数：" + exception.getParameterName());
    }

    /**
     * 上传文件超过上限。必须在兜底处理之前拦住：
     * 容器抛的是 MaxUploadSizeExceededException，落到兜底分支只会看到「服务器内部错误」，
     * 用户完全不知道该把文件压小。
     *
     * @param exception 超限异常
     * @return 统一响应体
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public R<Void> handleMaxUploadSizeException(MaxUploadSizeExceededException exception) {
        LOGGER.warn("上传文件超过限制：{}", exception.getMessage());
        return R.fail("文件超过大小上限，请压缩后重新上传");
    }

    /**
     * 其余 multipart 解析失败，例如表单字段名不对、请求不是 multipart。
     *
     * @param exception 上传异常
     * @return 统一响应体
     */
    @ExceptionHandler(MultipartException.class)
    public R<Void> handleMultipartException(MultipartException exception) {
        LOGGER.warn("附件上传请求格式错误：{}", exception.getMessage());
        return R.fail("附件上传失败，请确认已选择文件并以表单方式提交");
    }

    /**
     * 请求路径没有任何映射。Spring Boot 3 会把它当「静态资源找不到」抛出来，
     * 落到兜底分支就变成「服务器内部错误」—— 前端拼错路径时，看到这句提示会去查服务是否宕了。
     *
     * @param exception 资源不存在异常
     * @param response  HTTP 响应，用于改写为 404
     * @return 统一响应体
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public R<Void> handleNoResourceException(NoResourceFoundException exception,
                                             HttpServletResponse response) {
        response.setStatus(HttpServletResponse.SC_NOT_FOUND);
        LOGGER.warn("接口不存在：{} {}", exception.getHttpMethod(), exception.getResourcePath());
        return R.fail("接口不存在，请检查请求路径：" + exception.getResourcePath());
    }

    /**
     * 兜底异常：日志打全栈便于排查，但对外只给通用提示，不泄露内部细节。
     *
     * @param exception 未预期异常
     * @return 统一响应体
     */
    @ExceptionHandler(Exception.class)
    public R<Void> handleUnknownException(Exception exception) {
        LOGGER.error("未处理异常", exception);
        return R.fail("服务器内部错误，请稍后重试");
    }
}
