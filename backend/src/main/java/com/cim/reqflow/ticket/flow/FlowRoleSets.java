package com.cim.reqflow.ticket.flow;

import java.util.Set;

/**
 * 动作可执行的角色白名单。
 * <p>
 * 单独一个类而不是写进 {@link FlowAction}，因为枚举常量在类初始化时先于静态字段完成，
 * 在枚举里引用本类的静态集合会拿到 null。跨类引用没有这个先后问题。
 * <p>
 * 集合名刻意与状态码、角色码区分开（不用 CONF_MIT 这种名字）：
 * {@link FlowAction} 同时静态导入状态码与角色集，同名会导致编译期歧义。
 * <p>
 * 约定：ADMIN 出现在每个集合里，但**不是万能**，仍要过前置校验
 * （附件没传，管理员也批不了上线）。系统动作 {@link #SYSTEM_ROLES} 是空集，
 * 表示只允许后端以 SYS 身份触发，永不暴露给登录态。
 *
 * @author Haijie Xiao
 * @date 2026-11-04
 */
public final class FlowRoleSets {

    private FlowRoleSets() {
    }

    /** 提出人 */
    public static final String REPORTER = "REPORTER";
    /** MIT Leader */
    public static final String MIT_LEADER = "MIT_LEADER";
    /** MIT 开发人员 */
    public static final String MIT_DEV = "MIT_DEV";
    /** 部门负责人 */
    public static final String DEPT_MANAGER = "DEPT_MANAGER";
    /** 平台负责人 */
    public static final String PLATFORM_LEADER = "PLATFORM_LEADER";
    /** 评审领导 */
    public static final String REVIEW_LEADER = "REVIEW_LEADER";
    /** IT 运维 */
    public static final String IT_OPS = "IT_OPS";
    /** 系统管理员 */
    public static final String ADMIN = "ADMIN";

    /** 全员可提单：目前 8 个角色都能提需求 */
    public static final Set<String> ALL_STAFF = Set.of(
            ADMIN, MIT_LEADER, MIT_DEV, REPORTER,
            DEPT_MANAGER, PLATFORM_LEADER, REVIEW_LEADER, IT_OPS);

    /** 仅系统。空集即「不授任何人」 */
    public static final Set<String> SYSTEM_ROLES = Set.of();

    /** MIT 确认 */
    public static final Set<String> MIT_CONF = Set.of(ADMIN, MIT_LEADER);

    /** 平台确认 / 平台评审 */
    public static final Set<String> PLATFORM_CONF = Set.of(ADMIN, PLATFORM_LEADER);

    /** 上级确认：部门负责人是最常见的确认人，不能因为 writable = 0 就把他挡掉 */
    public static final Set<String> SUPERIOR_CONF = Set.of(ADMIN, DEPT_MANAGER);

    /** 需求评审 */
    public static final Set<String> REVIEWERS = Set.of(ADMIN, REVIEW_LEADER);

    /** Leader 侧动作：派单、驳回、上线审批、挂起、评审结论 */
    public static final Set<String> LEADERS = Set.of(ADMIN, MIT_LEADER);

    /** 承办开发侧动作 */
    public static final Set<String> DEVS = Set.of(ADMIN, MIT_DEV);

    /** 提出人侧动作 */
    public static final Set<String> REPORTERS = Set.of(ADMIN, REPORTER);

    /** IT 侧动作：IT 评审、IT 点检 */
    public static final Set<String> IT_ROLES = Set.of(ADMIN, IT_OPS);
}
