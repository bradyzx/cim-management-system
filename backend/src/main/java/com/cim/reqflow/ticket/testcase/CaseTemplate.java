package com.cim.reqflow.ticket.testcase;

import java.util.List;

/**
 * 规则模板生成测试用例：把每条验收标准套成「给定 – 当 – 则」三步。
 * <p>
 * 一期不接模型（`06` 第九节的分期）。模板生成的用例确实笨 —— 它不会拆解场景、
 * 不会推断边界 —— 但它强制把每条验收标准过一遍，光这一点就比「口头说测过了」强。
 * 而且零模型依赖，模型挂了也不影响流转。
 * <p>
 * 一条标准出一条用例，不刻意凑数量：`06` 第八节说单需求建议≤15 条，
 * 标准通常只有 3~8 条，按标准对齐既不会超量，也保证覆盖率能到 100%。
 *
 * @author Haijie Xiao
 * @date 2026-11-05
 */
public final class CaseTemplate {

    /** 标题截断长度：超过这个长度的标准原文放进标题会看不清 */
    private static final int TITLE_MAX = 40;

    /** 前几条标准给 P0：最基本的功能点，测不过就没必要往下测 */
    private static final int P0_SEQ_LIMIT = 2;

    private CaseTemplate() {
        // 纯工具类
    }

    /**
     * 一条用例草稿。落库前由服务补编号、单据、轮次、审计字段。
     *
     * @param title         标题
     * @param precond       前置条件
     * @param steps         步骤
     * @param expected      预期结果
     * @param priority      优先级
     * @param sourceCriteria 对应验收标准原文
     * @param criteriaIndex 对应第几条验收标准
     * @param focusFlag     1 = 重点关注
     * @param focusReason   重点关注理由
     */
    public record Draft(String title, String precond, List<String> steps, String expected,
                        String priority, String sourceCriteria, Integer criteriaIndex,
                        Integer focusFlag, String focusReason) {
    }

    /**
     * 按一条验收标准生成用例草稿。
     *
     * @param seq    验收标准序号，从 1 起
     * @param content 验收标准原文
     * @return 用例草稿
     */
    public static Draft build(int seq, String content) {
        String 标准 = content == null ? "" : content.trim();
        FocusJudge.Verdict 判定 = FocusJudge.judge(标准);

        return new Draft(
                取标题(标准),
                // 前置条件写「已具备入口」而不是「环境已就绪」：后者正确但没用，
                // 测试的人看了还是不知道自己该准备什么
                "系统可访问，「" + 摘要(标准) + "」对应的功能入口已开通，账号具备相应权限",
                List.of(
                        "准备数据：构造满足「" + 摘要(标准) + "」的业务数据与前置条件",
                        "执行操作：按该验收标准描述的业务路径操作一次",
                        "核对结果：逐项比对实际表现与验收标准是否一致"),
                标准,
                判定.focus() || seq <= P0_SEQ_LIMIT ? "P0" : "P1",
                标准,
                seq,
                判定.focus() ? 1 : 0,
                判定.reason());
    }

    /**
     * 标题：能一眼看出测什么，超长截断。
     *
     * @param 标准 验收标准原文
     * @return 标题
     */
    private static String 取标题(String 标准) {
        if (标准.isEmpty()) {
            return "验证：未填写的验收标准";
        }
        String 去换行 = 标准.replaceAll("\\s+", " ");
        if (去换行.length() <= TITLE_MAX) {
            return "验证：" + 去换行;
        }
        return "验证：" + 去换行.substring(0, TITLE_MAX) + "…";
    }

    /**
     * 摘要：塞进句子中间时不能太长，否则「准备数据：构造满足『…』的业务数据」会读不通。
     *
     * @param 标准 验收标准原文
     * @return 摘要
     */
    private static String 摘要(String 标准) {
        String 去换行 = 标准.replaceAll("\\s+", " ");
        if (去换行.isEmpty()) {
            return "该验收标准";
        }
        return 去换行.length() <= 20 ? 去换行 : 去换行.substring(0, 20) + "…";
    }
}
