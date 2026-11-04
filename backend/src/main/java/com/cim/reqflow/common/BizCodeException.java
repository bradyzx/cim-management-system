package com.cim.reqflow.common;

/**
 * 带业务错误码的业务异常。
 * <p>
 * 与 {@link BizException} 的区别：前者只有一句话，前端只能把话显示出来；
 * 本类额外带 bizCode 与 details，前端能做机器判断 ——
 * 附件缺失时定位到附件区、状态冲突时刷新详情、退回超限时禁用按钮。
 * <p>
 * 不直接给 BizException 加字段：那会让 76 个已有接口的异常也带上两个恒为 null 的属性，
 * 接口返回体跟着变脏。新增子类只影响新写的地方。
 *
 * @author Haijie Xiao
 * @date 2026-11-04
 */
public class BizCodeException extends BizException {

    /** 序列化版本号 */
    private static final long serialVersionUID = 1L;

    /** 业务错误码，如 ATTACH_REQUIRED */
    private final String bizCode;

    /** 附加数据，如缺失的附件类型与份数，可为 null */
    private final Object details;

    /**
     * 构造业务错误。
     *
     * @param bizCode 业务错误码
     * @param message 提示语，可直接展示
     * @param details 附加数据，可为 null
     */
    public BizCodeException(String bizCode, String message, Object details) {
        super(message);
        this.bizCode = bizCode;
        this.details = details;
    }

    public String getBizCode() {
        return bizCode;
    }

    public Object getDetails() {
        return details;
    }
}
