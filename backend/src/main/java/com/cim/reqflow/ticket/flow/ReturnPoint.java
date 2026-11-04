package com.cim.reqflow.ticket.flow;

import java.util.List;

/**
 * 退回点码（17 个，与 02 第六节、字典 return_point 一一对应）。
 * <p>
 * 编号 ①~⑨ 是早期就有的，⑩~⑮ 随三方确认与设计评审拆分追加，⑯⑰ 补的是两条原本没有编号的
 * 回退边（监控异常、点检不通过）。**只增不改、不重排**：已有编号被历史报表、SLA 配置、
 * 前端文案引用，插在中间会让后面全部顺延，改一处漏一处。
 * <p>
 * ④ 保留为「设计评审退回」的统计总称，不单独产生数据 —— 细分落在 ⑫⑬⑭ 三个点上，
 * 这样历史报表里 ④ 的数字不会突然变成 0。
 *
 * @author Haijie Xiao
 * @date 2026-11-04
 */
public final class ReturnPoint {

    private ReturnPoint() {
    }

    /** ① 需求文档退回重生成 */
    public static final String DOC_REGEN = "RT_DOC_REGEN";
    /** ② 待分配退回 / 不予立项 */
    public static final String ASSIGN_REJECT = "RT_ASSIGN_REJECT";
    /** ③ 待接收拒接 */
    public static final String ASSIGN_REFUSE = "RT_ASSIGN_REFUSE";
    /** ④ 设计评审退回（统计总称，不产生数据） */
    public static final String DESIGN_TOTAL = "RT_DESIGN_TOTAL";
    /** ⑤ UAT 退回 */
    public static final String UAT_FAIL = "RT_UAT_FAIL";
    /** ⑥ 上线审批驳回 */
    public static final String RELEASE_REJECT = "RT_RELEASE_REJECT";
    /** ⑦ 上级确认驳回 */
    public static final String CONF_SUPERIOR = "RT_CONF_SUPERIOR";
    /** ⑧ 需求评审驳回 */
    public static final String REQ_REVIEW = "RT_REQ_REVIEW";
    /** ⑨ 上线评价不符合 */
    public static final String EVAL_FAIL = "RT_EVAL_FAIL";
    /** ⑩ MIT 确认驳回 */
    public static final String CONF_MIT = "RT_CONF_MIT";
    /** ⑪ 平台确认驳回 */
    public static final String CONF_PLATFORM = "RT_CONF_PLATFORM";
    /** ⑫ 设计 · IT 评审退回 */
    public static final String DSGN_IT = "RT_DSGN_IT";
    /** ⑬ 设计 · 平台评审退回 */
    public static final String DSGN_PLATFORM = "RT_DSGN_PLATFORM";
    /** ⑭ 设计 · 用户评审退回 */
    public static final String DSGN_USER = "RT_DSGN_USER";
    /** ⑮ 上线验收不通过 */
    public static final String PROD_ACCEPT = "RT_PROD_ACCEPT";
    /** ⑯ 运行监控异常退回 */
    public static final String MONITOR = "RT_MONITOR";
    /** ⑰ IT 点检不通过 */
    public static final String IT_CHECK = "RT_IT_CHECK";

    /**
     * 设计评审三步的合计计数组。
     * ⑫⑬⑭ 各自单点 2 次，但同一张单在这三个点上的退回次数合计到 3 次也要升级 ——
     * 否则可以在 IT 退 2 次、平台退 2 次、用户退 2 次，六轮打转。
     */
    public static final List<String> DSGN_GROUP = List.of(DSGN_IT, DSGN_PLATFORM, DSGN_USER);
}
