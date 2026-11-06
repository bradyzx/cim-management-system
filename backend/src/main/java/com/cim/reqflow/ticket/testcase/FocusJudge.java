package com.cim.reqflow.ticket.testcase;

import java.util.List;

/**
 * 重点关注点判定，落 `06-AI测试用例生成.md` 第四节的 7 个维度。
 * <p>
 * 一期是纯规则：关键词命中即标记。规则版判得笨，但它至少做到一件事 ——
 * 每一条重点用例都带得出「为什么盯它」，而不是只标个红点让人猜。
 * <p>
 * 第 5 维「历史高发」一期不做：要按模块统计历史 BUG 密度，得有 BUG 库和足够样本，
 * 现在两个都没有。硬凑一个假分数比不标更糟，等二期接了历史库再补。
 *
 * @author Haijie Xiao
 * @date 2026-11-05
 */
public final class FocusJudge {

    /**
     * 判定结果。
     *
     * @param focus  是否重点关注
     * @param reason 命中的维度与理由，未命中为 null
     */
    public record Verdict(boolean focus, String reason) {
    }

    /**
     * 一个判定维度。
     *
     * @param name    维度名
     * @param reason  展示给测试人员的理由
     * @param words   触发词
     * @param needNum 是否必须先出现数字（硬性指标类要求）
     */
    private record Dimension(String name, String reason, List<String> words, boolean needNum) {
    }

    /** 维度表。顺序即优先级：一条标准命中多个维度时，取靠前的那个作为理由 */
    private static final List<Dimension> DIMENSIONS = List.of(
            new Dimension("硬性指标",
                    "标准里有明确的数量或时间要求，性能与精度最容易在这里翻车",
                    List.of("秒", "毫秒", "分钟", "小时", "并发", "容量", "吞吐", "响应时间",
                            "MB", "GB", "上限", "以内", "不超过", "不低于"), true),
            new Dimension("边界条件",
                    "涉及空值、极值或超限场景，这类输入日常测不到、上线就出问题",
                    List.of("空值", "为空", "超长", "最大", "最小", "临界", "上限", "下限",
                            "零", "负数", "超出", "越界", "重复"), false),
            new Dimension("外部交互",
                    "要跨系统或调接口，重点在超时、返回异常、对方不可用",
                    List.of("接口", "调用", "同步", "推送", "导入", "导出", "上传", "下载",
                            "对接", "第三方", "外部系统", "消息", "回调"), false),
            new Dimension("数据一致性",
                    "涉及多表或多系统写，两端对不上时最难查",
                    List.of("一致", "对账", "两端", "主从", "回写", "双向", "比对", "同时更新"), false),
            new Dimension("权限与安全",
                    "涉及可见范围、越权或敏感数据，测漏了是合规问题",
                    List.of("权限", "越权", "可见", "敏感", "加密", "脱敏", "审批",
                            "授权", "登录", "角色", "授权范围"), false),
            new Dimension("计算类",
                    "涉及统计或计算口径，算错了不容易发现，且影响面大",
                    List.of("计算", "工时", "金额", "比率", "良率", "合格率", "达成率",
                            "统计", "汇总", "合计", "占比", "成本"), false)
    );

    private FocusJudge() {
        // 纯工具类
    }

    /**
     * 判定一条验收标准是否属于重点关注。
     *
     * @param criteria 验收标准原文
     * @return 判定结果
     */
    public static Verdict judge(String criteria) {
        if (criteria == null || criteria.trim().isEmpty()) {
            return new Verdict(false, null);
        }
        boolean 有数字 = criteria.chars().anyMatch(Character::isDigit);

        for (Dimension 维度 : DIMENSIONS) {
            if (维度.needNum() && !有数字) {
                continue;
            }
            for (String 词 : 维度.words()) {
                if (criteria.contains(词)) {
                    return new Verdict(true, 维度.name() + "：" + 维度.reason());
                }
            }
        }
        return new Verdict(false, null);
    }
}
