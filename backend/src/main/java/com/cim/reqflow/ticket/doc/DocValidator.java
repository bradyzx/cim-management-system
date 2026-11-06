package com.cim.reqflow.ticket.doc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 需求文档完整性校验，落契约 `11` §8.1.7 的四条规则。
 * <p>
 * 只做「读 JSON 出结论」，不依赖 Spring、不改库。这样前置校验（{@code PreconditionChecker}）
 * 和文档服务（{@code TicketDocService}）都能直接用，不用互相注入 —— 否则
 * flow 包与 service 包会形成构造器循环依赖，启动就报错。
 * <p>
 * 校验只在「确认需求文档」时执行。保存半成品不拦：提出人写到一半要能存，
 * 确认才是交付门槛。
 *
 * @author Haijie Xiao
 * @date 2026-11-05
 */
public final class DocValidator {

    /** 解析与校验共用一个实例，ObjectMapper 是线程安全的 */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 功能清单编号格式：F-01、F-02 */
    private static final String 功能编号格式 = "^F-\\d{2}$";

    private DocValidator() {
        // 纯工具类，不实例化
    }

    /**
     * 校验结果。
     * <p>
     * 两个清单分工明确：`missingSections` 给前端定位到具体卡片标红，
     * `messages` 直接展示给人看。同一条问题两边都出现，只是粒度不同。
     *
     * @param missingSections 不达标的节键名，空表示通过
     * @param messages        可读原因，一条问题一句
     */
    public record Result(List<String> missingSections, List<String> messages) {

        /**
         * 是否通过校验。
         *
         * @return 无缺失节即通过
         */
        public boolean passed() {
            return missingSections.isEmpty();
        }
    }

    /**
     * 规则版完整度评分：五个必填节各占 20 分。
     * <p>
     * 放在校验器里而不是生成器里，是因为它有两个使用时机：首次生成文档时算一次，
     * 之后每次人工保存都要重算。只算一次的话，用户把功能清单补齐了，
     * 页面上还显示生成时的旧分数，看的人会以为文档还是空的。
     *
     * @param sections 12 节节点
     * @return 0~100
     */
    public static int completeness(JsonNode sections) {
        int 得分 = 0;
        for (String key : new String[]{DocSections.NAME, DocSections.ASIS, DocSections.TOBE}) {
            if (!sections.path(key).path("content").asText("").trim().isEmpty()) {
                得分 += 20;
            }
        }
        if (!为空数组(sections.path(DocSections.FUNCTIONS).path("items"))) {
            得分 += 20;
        }
        if (!为空数组(sections.path(DocSections.ACCEPTANCE).path("items"))) {
            得分 += 20;
        }
        return 得分;
    }

    /**
     * 数组节点是否为空或不是数组。
     *
     * @param node 节点
     * @return 空返回 true
     */
    private static boolean 为空数组(JsonNode node) {
        return node == null || !node.isArray() || node.isEmpty();
    }

    /**
     * 执行四条校验。
     *
     * @param docJson 12 节结构化 JSON 字符串
     * @return 校验结果
     */
    public static Result check(String docJson) {
        List<String> 缺失 = new ArrayList<>();
        List<String> 原因 = new ArrayList<>();

        if (docJson == null || docJson.trim().isEmpty()) {
            缺失.add(DocSections.NAME);
            原因.add("需求文档尚未生成");
            return new Result(缺失, 原因);
        }

        JsonNode root;
        try {
            root = MAPPER.readTree(docJson);
        } catch (Exception 异常) {
            缺失.add(DocSections.NAME);
            原因.add("需求文档结构损坏，无法解析，请重新生成");
            return new Result(缺失, 原因);
        }

        JsonNode sections = root.path("sections");
        校验文本节(sections, DocSections.NAME, 缺失, 原因);
        校验文本节(sections, DocSections.ASIS, 缺失, 原因);
        校验文本节(sections, DocSections.TOBE, 缺失, 原因);
        校验功能清单(sections, 缺失, 原因);
        校验工作量(sections, 缺失, 原因);
        校验验收标准(sections, 缺失, 原因);

        return new Result(缺失, 原因);
    }

    /**
     * 规则 ①：标题、背景、目标三节的 content 不能为空。
     *
     * @param sections 12 节节点
     * @param key      节键名
     * @param 缺失     缺失节收集器
     * @param 原因     原因收集器
     */
    private static void 校验文本节(JsonNode sections, String key,
                                   List<String> 缺失, List<String> 原因) {
        String 内容 = sections.path(key).path("content").asText("");
        if (内容.trim().isEmpty()) {
            缺失.add(key);
            原因.add(DocSections.title(key) + "未填写");
        }
    }

    /**
     * 规则 ②：功能清单非空，编号形如 F-01，从 F-01 起连续、不重复。
     * <p>
     * 编号是三处结构化数据的锚点（点检项生成、变更差异比对、验收标准引用），
     * 所以这里必须严：跳号或重号会让下游全部指错，且错得很隐蔽。
     *
     * @param sections 12 节节点
     * @param 缺失     缺失节收集器
     * @param 原因     原因收集器
     */
    private static void 校验功能清单(JsonNode sections, List<String> 缺失, List<String> 原因) {
        JsonNode items = sections.path(DocSections.FUNCTIONS).path("items");
        if (!items.isArray() || items.isEmpty()) {
            缺失.add(DocSections.FUNCTIONS);
            原因.add("功能清单至少需要一条（编号从 F-01 起）");
            return;
        }

        Set<String> 已出现 = new HashSet<>();
        for (int i = 0; i < items.size(); i++) {
            String code = items.get(i).path("code").asText("").trim();
            String 期望 = String.format("F-%02d", i + 1);
            if (!code.matches(功能编号格式)) {
                缺失.add(DocSections.FUNCTIONS);
                原因.add("功能清单第 " + (i + 1) + " 条编号「" + code + "」格式不对，应为 " + 期望);
                return;
            }
            if (已出现.contains(code)) {
                缺失.add(DocSections.FUNCTIONS);
                原因.add("功能清单编号「" + code + "」重复");
                return;
            }
            if (!code.equals(期望)) {
                缺失.add(DocSections.FUNCTIONS);
                原因.add("功能清单编号必须从 F-01 连续递增，第 " + (i + 1) + " 条应为 " + 期望);
                return;
            }
            已出现.add(code);
        }
    }

    /**
     * 规则 ③：工作量 minDays / maxDays 都大于 0，且 minDays ≤ maxDays。
     * <p>
     * 拆两个数是为了派单默认工时和 SLA 阈值能直接算，不做字符串解析。
     *
     * @param sections 12 节节点
     * @param 缺失     缺失节收集器
     * @param 原因     原因收集器
     */
    private static void 校验工作量(JsonNode sections, List<String> 缺失, List<String> 原因) {
        JsonNode effort = sections.path(DocSections.EFFORT);
        double 最小 = effort.path("minDays").asDouble(0);
        double 最大 = effort.path("maxDays").asDouble(0);
        if (最小 <= 0 || 最大 <= 0) {
            缺失.add(DocSections.EFFORT);
            原因.add("工作量需填写预估人天（最小与最大都要大于 0）");
            return;
        }
        if (最小 > 最大) {
            缺失.add(DocSections.EFFORT);
            原因.add("工作量的最小值不能大于最大值");
            return;
        }
        // 契约要求最多两位小数：再细的精度没人能估出来，写进去只会让派单默认值显得精确
        if (小数位(最小) > 2 || 小数位(最大) > 2) {
            缺失.add(DocSections.EFFORT);
            原因.add("工作量最多保留两位小数");
        }
    }

    /**
     * 数值的小数位数。用 BigDecimal 而不是字符串截取，
     * 免得 8.10 这种尾随零被算成两位以上。
     *
     * @param 值 数值
     * @return 有效小数位
     */
    private static int 小数位(double 值) {
        return BigDecimal.valueOf(值).stripTrailingZeros().scale();
    }

    /**
     * 规则 ④：验收标准非空，seq 从 1 连续递增无重复。
     * <p>
     * seq 是覆盖率的对应键 —— `req_acceptance_criteria.seq` 与
     * `req_test_case.criteria_index` 都指向它，断了就统计不出「哪条没测」。
     *
     * @param sections 12 节节点
     * @param 缺失     缺失节收集器
     * @param 原因     原因收集器
     */
    private static void 校验验收标准(JsonNode sections, List<String> 缺失, List<String> 原因) {
        JsonNode items = sections.path(DocSections.ACCEPTANCE).path("items");
        if (!items.isArray() || items.isEmpty()) {
            缺失.add(DocSections.ACCEPTANCE);
            原因.add("验收标准至少需要一条（序号从 1 起）");
            return;
        }

        Set<Integer> 已出现 = new HashSet<>();
        for (int i = 0; i < items.size(); i++) {
            int seq = items.get(i).path("seq").asInt(0);
            // 先判重复再判连续：[1,1] 这种既重复又不连续，先说重复更好懂
            if (已出现.contains(seq)) {
                缺失.add(DocSections.ACCEPTANCE);
                原因.add("验收标准序号 " + seq + " 重复");
                return;
            }
            if (seq != i + 1) {
                缺失.add(DocSections.ACCEPTANCE);
                原因.add("验收标准序号必须从 1 连续递增，第 " + (i + 1) + " 条应为 " + (i + 1));
                return;
            }
            已出现.add(seq);
            if (items.get(i).path("text").asText("").trim().isEmpty()) {
                缺失.add(DocSections.ACCEPTANCE);
                原因.add("验收标准第 " + seq + " 条内容为空");
                return;
            }
        }
    }
}
