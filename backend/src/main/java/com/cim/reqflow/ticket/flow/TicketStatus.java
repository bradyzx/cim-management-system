package com.cim.reqflow.ticket.flow;

import java.util.List;
import java.util.Set;

/**
 * 单据状态码常量（23 态，与 02 第四节、字典 ticket_status 一一对应）。
 * <p>
 * 用常量而不是散落各处的字符串字面量：状态码拼错时，写常量会编译不过，
 * 写字符串只会在运行时变成一个查不到翻译的英文码，报错点离出错点很远。
 * <p>
 * v0.7 并行化：三方确认三态合并为 {@link #CONFIRMING}，设计评审三态合并为
 * {@link #DSGN_REVIEWING}，各自靠 req_ticket 上的通过标志区分进度。
 * 六个旧码（CONF_MIT / CONF_PLATFORM / CONF_SUPERIOR / DSGN_REVIEW_*）
 * 不在此列：只存在于字典里用于翻译历史数据与新代码不允许产生，同早期三个旧码。
 *
 * @author Haijie Xiao
 * @date 2026-11-05
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
    /** 三方确认中：MIT / 平台 / 上级不分先后，各自通过后置位，全部通过进需求评审 */
    public static final String CONFIRMING = "CONFIRMING";
    /** 需求评审 */
    public static final String REQ_REVIEW = "REQ_REVIEW";
    /** 待分配 */
    public static final String PENDING_ASSIGN = "PENDING_ASSIGN";
    /** 待接收 */
    public static final String ASSIGNED = "ASSIGNED";
    /** 设计中 */
    public static final String DESIGNING = "DESIGNING";
    /** 设计评审中：IT / 平台 / 用户不分先后，全部通过进开发并锁定验收标准 */
    public static final String DSGN_REVIEWING = "DSGN_REVIEWING";
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

    /** v0.7 并行化前的旧码：三方确认，只在字典里翻译历史数据 */
    public static final Set<String> LEGACY_CONFIRM = Set.of(
            "CONF_MIT", "CONF_PLATFORM", "CONF_SUPERIOR");
    /** v0.7 并行化前的旧码：设计评审，只在字典里翻译历史数据 */
    public static final Set<String> LEGACY_DSGN_REVIEW = Set.of(
            "DSGN_REVIEW_IT", "DSGN_REVIEW_PLATFORM", "DSGN_REVIEW_USER");

    /**
     * 可被挂起的中间态：1~20，不含 CLOSED。
     * 挂起是全局动作，任意在途单据都能挂；终态不能挂，挂了就无法恢复。
     */
    public static final List<String> SUSPENDABLE = List.of(
            DRAFT, SUBMITTED, AI_ANALYZING, AI_CLARIFYING, REQ_DOC_DRAFTED,
            CONFIRMING, REQ_REVIEW, PENDING_ASSIGN,
            ASSIGNED, DESIGNING, DSGN_REVIEWING,
            DEVELOPING, SELF_TEST, DEPLOYING, UAT, RELEASE_PENDING,
            RELEASED, MONITORING, IT_INSPECTION, EVALUATING);

    /**
     * 终态：不可再流转（除 CLOSED 在超时关闭的情况下可重开）。
     */
    public static final Set<String> TERMINAL = Set.of(CLOSED);
}
