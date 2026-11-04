package com.cim.reqflow.common.constant;

/**
 * 系统管理模块的跨枚举常量：标志位、角色编码、同步来源等。
 * 状态、菜单类型、数据范围属于固定取值集合，已分别由 RecordStatusEnum、
 * MenuTypeEnum、DataScopeTypeEnum 维护，本类不再重复定义。
 *
 * @author Haijie Xiao
 * @date 2026-11-04
 */
public final class SystemConstants {

    /** 标志位：是 */
    public static final Integer FLAG_YES = 1;

    /** 标志位：否 */
    public static final Integer FLAG_NO = 0;

    /** 全局管理员角色编码。该角色不绑定部门，绑定后反而只在单部门生效 */
    public static final String ROLE_ADMIN = "ADMIN";

    /** 认证方式：本地口令 */
    public static final String AUTH_TYPE_LOCAL = "LOCAL";

    /** 同步来源：本地录入 */
    public static final String SYNC_SOURCE_LOCAL = "LOCAL";

    /** 用工类型：正式员工 */
    public static final String EMPLOYMENT_TYPE_EMPLOYEE = "EMPLOYEE";

    /** 任职类型：主岗 */
    public static final String POST_TYPE_PRIMARY = "PRIMARY";

    /** 单据类型通配符：表示对该角色全部单据类型兜底 */
    public static final String TICKET_TYPE_ALL = "*";

    /** 登录后落地页 */
    public static final String HOME_PATH = "/req/todo";

    /**
     * 工具常量类禁止实例化。
     */
    private SystemConstants() {
    }
}
