package com.cim.reqflow.ticket.flow;

import java.util.List;
import java.util.Set;

/**
 * 单据状态码常量（27 态，与 02 第四节、字典 ticket_status 一一对应）。
 * <p>
 * 用常量而不是散落各处的字符串字面量：状态码拼错时，写常量会编译不过，
 * 写字符串只会在运行时变成一个查不到翻译的英文码，报错点离出错点很远。
 * <p>
 * 三个旧码（ANALYZED / LEADER_CONFIRM / DESIGN_REVIEW）不在此列：
 * 它们只存在于字典里用于翻译历史数据，新代码不允许产生。
 *
 * @author Haijie Xiao
 * @date 2026-11-04
 */
public final class TicketStatus {

    private TicketStatus() {
    }

    /** 草稿 */
    public static final String DRAFT = "DRAFT";
    /** 待分析 */
    public static final String SUBMITTED = "SUBMITTED";
    /** AI 分析中 */
    public static final String AI_ANALYZING = "AI_ANALYZING";
    /** 待补充澄清 */
    public static final String AI_CLARIFYING = "AI_CLARIFYING";
    /** 需求文档待确认 */
    public static final String REQ_DOC_DRAFTED = "REQ_DOC_DRAFTED";
    /** MIT 确认 */
    public static final String CONF_MIT = "CONF_MIT";
    /** 平台确认 */
    public static final String CONF_PLATFORM = "CONF_PLATFORM";
    /** 上级确认 */
    public static final String CONF_SUPERIOR = "CONF_SUPERIOR";
    /** 需求评审 */
    public static final String REQ_REVIEW = "REQ_REVIEW";
    /** 待分配 */
    public static final String PENDING_ASSIGN = "PENDING_ASSIGN";
    /** 待接收 */
    public static final String ASSIGNED = "ASSIGNED";
    /** 设计中 */
    public static final String DESIGNING = "DESIGNING";
    /** 设计 · IT 评审 */
    public static final String DSGN_REVIEW_IT = "DSGN_REVIEW_IT";
    /** 设计 · 平台评审 */
    public static final String DSGN_REVIEW_PLATFORM = "DSGN_REVIEW_PLATFORM";
    /** 设计 · 用户评审 */
    public static final String DSGN_REVIEW_USER = "DSGN_REVIEW_USER";
    /** 开发中 */
    public static final String DEVELOPING = "DEVELOPING";
    /** 开发自测 */
    public static final String SELF_TEST = "SELF_TEST";
    /** 部署中（二期启用，一期不参与流转） */
    public static final String DEPLOYING = "DEPLOYING";
    /** UAT 测试 */
    public static final String UAT = "UAT";
    /** 待上线 */
    public static final String RELEASE_PENDING = "RELEASE_PENDING";
    /** 已上线 · 待验收 */
    public static final String RELEASED = "RELEASED";
    /** 运行监控 */
    public static final String MONITORING = "MONITORING";
    /** IT 点检 */
    public static final String IT_INSPECTION = "IT_INSPECTION";
    /** 待评价 */
    public static final String EVALUATING = "EVALUATING";
    /** 已关闭 */
    public static final String CLOSED = "CLOSED";
    /** 挂起 */
    public static final String SUSPENDED = "SUSPENDED";
    /** 挂起 · 待评审 */
    public static final String SUSPENDED_REVIEW = "SUSPENDED_REVIEW";

    /**
     * 可被挂起的中间态：1~24，不含 CLOSED。
     * 挂起是全局动作，任意在途单据都能挂；终态不能挂，挂了就无法恢复。
     */
    public static final List<String> SUSPENDABLE = List.of(
            DRAFT, SUBMITTED, AI_ANALYZING, AI_CLARIFYING, REQ_DOC_DRAFTED,
            CONF_MIT, CONF_PLATFORM, CONF_SUPERIOR, REQ_REVIEW, PENDING_ASSIGN,
            ASSIGNED, DESIGNING, DSGN_REVIEW_IT, DSGN_REVIEW_PLATFORM, DSGN_REVIEW_USER,
            DEVELOPING, SELF_TEST, DEPLOYING, UAT, RELEASE_PENDING,
            RELEASED, MONITORING, IT_INSPECTION, EVALUATING);

    /**
     * 终态：不可再流转（除 CLOSED 在超时关闭的情况下可重开）。
     */
    public static final Set<String> TERMINAL = Set.of(CLOSED);
}
