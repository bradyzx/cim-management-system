package com.cim.reqflow.ticket.doc;

/**
 * 需求文档 12 节的固定键名与标题。
 * <p>
 * 键名由契约 `11-业务接口契约.md` §8.1.1 定稿，前端按这些 key 直接渲染卡片、
 * 缺哪节一目了然。所以这里必须是唯一来源，不允许业务代码里散写字符串 ——
 * 一旦某处写成 `s1_background` 之类，前端就取不到值，而且不报错、只是空白。
 * <p>
 * 顺序即展示顺序，`ORDER` 供校验与渲染统一遍历。
 *
 * @author Haijie Xiao
 * @date 2026-11-05
 */
public final class DocSections {

    /** 1 需求名称 */
    public static final String NAME = "s1_name";
    /** 2 业务背景 */
    public static final String ASIS = "s2_asis";
    /** 3 痛点与影响 */
    public static final String PAIN = "s3_pain";
    /** 4 期望目标 */
    public static final String TOBE = "s4_tobe";
    /** 5 功能清单 */
    public static final String FUNCTIONS = "s5_functions";
    /** 6 非功能要求 */
    public static final String NFR = "s6_nfr";
    /** 7 联动范围 */
    public static final String SCOPE = "s7_scope";
    /** 8 优先级 */
    public static final String PRIORITY = "s8_priority";
    /** 9 工作量区间 */
    public static final String EFFORT = "s9_effort";
    /** 10 验收标准 */
    public static final String ACCEPTANCE = "s10_acceptance";
    /** 11 附件清单 */
    public static final String ATTACHMENTS = "s11_attachments";
    /** 12 AI 预审结论 */
    public static final String AI_REVIEW = "s12_ai_review";

    /** 12 节按展示顺序排列 */
    public static final String[] ORDER = {
            NAME, ASIS, PAIN, TOBE, FUNCTIONS, NFR,
            SCOPE, PRIORITY, EFFORT, ACCEPTANCE, ATTACHMENTS, AI_REVIEW
    };

    /** 键名 → 中文标题，markdown 渲染与提示语共用 */
    private static final String[][] TITLES = {
            {NAME, "一、需求名称"},
            {ASIS, "二、业务背景"},
            {PAIN, "三、痛点与影响"},
            {TOBE, "四、期望目标"},
            {FUNCTIONS, "五、功能清单"},
            {NFR, "六、非功能要求"},
            {SCOPE, "七、联动范围"},
            {PRIORITY, "八、优先级"},
            {EFFORT, "九、工作量区间"},
            {ACCEPTANCE, "十、验收标准"},
            {ATTACHMENTS, "十一、附件清单"},
            {AI_REVIEW, "十二、AI 预审结论"},
    };

    private DocSections() {
        // 纯常量类，不实例化
    }

    /**
     * 取某一节的中文标题。用于渲染 markdown 与拼校验提示。
     *
     * @param key 节键名
     * @return 中文标题；未知 key 原样返回
     */
    public static String title(String key) {
        for (String[] 项 : TITLES) {
            if (项[0].equals(key)) {
                return 项[1];
            }
        }
        return key;
    }
}
