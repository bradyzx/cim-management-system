package com.cim.reqflow.common.constant;

import java.time.format.DateTimeFormatter;

/**
 * 跨模块通用常量。凡在代码中出现两次以上的字面量都必须在此集中定义，
 * 不允许再散落在各个类里（手册「常量定义」：不允许任何魔法值直接出现在代码中）。
 *
 * @author Haijie Xiao
 * @date 2026-11-03
 */
public final class CommonConstants {

    /** 统一响应体：成功码。前端以 code == 0 判定成功 */
    public static final int SUCCESS_CODE = 0;

    /** 统一响应体：业务失败码。业务错误统一走 HTTP 200 + code = -1 */
    public static final int FAIL_CODE = -1;

    /** 统一响应体：成功提示语 */
    public static final String SUCCESS_MESSAGE = "ok";

    /** 分页默认页码 */
    public static final int DEFAULT_PAGE_NO = 1;

    /** 分页默认每页条数 */
    public static final int DEFAULT_PAGE_SIZE = 20;

    /** 分页单页上限，防止前端传超大 pageSize 把库拖死 */
    public static final long MAX_PAGE_SIZE = 500L;

    /** 全系统时间格式：时间由应用层生成并按此格式落库，不使用数据库默认值 */
    public static final String DATE_TIME_PATTERN = "yyyy-MM-dd HH:mm:ss";

    /** 时间格式化器。DateTimeFormatter 线程安全，可作为静态常量复用 */
    public static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern(DATE_TIME_PATTERN);

    /** 令牌请求头 */
    public static final String TOKEN_HEADER = "Authorization";

    /** 令牌前缀，注意末尾带空格 */
    public static final String TOKEN_PREFIX = "Bearer ";

    /** 刷新令牌所在 Cookie 名 */
    public static final String REFRESH_COOKIE_NAME = "jwt";

    /** 拦截器解析后放入请求域的当前用户 ID 键 */
    public static final String ATTR_CURRENT_USER_ID = "currentUserId";

    /** 拦截器解析后放入请求域的当前用户名键 */
    public static final String ATTR_CURRENT_USERNAME = "currentUsername";

    /** 分页默认页码的字符串形式，供 @RequestParam 默认值使用 */
    public static final String DEFAULT_PAGE_NO_TEXT = "1";

    /** 分页默认每页条数的字符串形式，供 @RequestParam 默认值使用 */
    public static final String DEFAULT_PAGE_SIZE_TEXT = "20";

    /**
     * 工具常量类禁止实例化。
     */
    private CommonConstants() {
    }
}
