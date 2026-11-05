package com.cim.reqflow.ticket.flow;

/**
 * 退回点码（19 个，与 02 第六节、字典 return_point 一一对应）。
 * <p>
 * 编号 ①~⑰ 沿用历史；⑱⑲ 是 v0.7 并行化新增的合并点：三方确认 ⑦⑩⑪ 合并为 ⑱，
 * 设计评审 ⑫⑬⑭ 合并为 ⑲。旧六个码保留在字典里翻译历史数据，新代码不再产生。
 * <p>
 * ④ 保留为「设计评审退回」的统计总称，不单独产生数据。
 *
 * @author Haijie Xiao
 * @date 2026-11-05
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
     * ⑱ 三方确认驳回（v0.7 并行化新增）。
     * 原 ⑦⑩⑪ 三个点合并：确认不分先后，驳回也不分是谁驳的，
     * 统一计次到同一个点，同单累计第 3 次转挂起评审。
     */
    public static final String CONFS = "RT_CONFS";
    /**
     * ⑲ 设计评审退回（v0.7 并行化新增）。
     * 原 ⑫⑬⑭ 三个点合并，语义同 ⑱：设计评审三步并行后按一个点计次。
     */
    public static final String DSGN_REVIEW = "RT_DSGN_REVIEW";
}
