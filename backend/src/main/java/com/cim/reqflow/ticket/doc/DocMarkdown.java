package com.cim.reqflow.ticket.doc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

/**
 * 把 12 节结构化 JSON 渲染成 Markdown 正文。
 * <p>
 * 存在的意义是「单一真相」：结构化数据 {@code doc_json} 是可编辑、可校验、
 * 可被下游消费的那一份，Markdown 只是它的投影。如果让前端在编辑表单之外
 * 再手工维护一段 Markdown，两边必然漂移 —— 用户在表单里改了验收标准，
 * 下载下来的文档还是旧的。所以 Markdown 一律由后端从 docJson 现算。
 *
 * @author Haijie Xiao
 * @date 2026-11-05
 */
public final class DocMarkdown {

    /** 与校验器共用，线程安全 */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 工作量小数位：人天允许 0.5，展示时最多两位 */
    private static final String 人天格式 = "%.2f";

    private DocMarkdown() {
        // 纯工具类，不实例化
    }

    /**
     * 渲染整份文档。
     *
     * @param docJson 12 节结构化 JSON 字符串
     * @return Markdown 正文；结构损坏时返回一句提示，不抛异常
     */
    public static String render(String docJson) {
        if (docJson == null || docJson.trim().isEmpty()) {
            return "";
        }

        JsonNode sections;
        try {
            sections = MAPPER.readTree(docJson).path("sections");
        } catch (Exception 异常) {
            return "（需求文档结构损坏，无法渲染）\n";
        }

        StringBuilder md = new StringBuilder();
        // 标题用第 1 节的内容，正文里不再重复「一、需求名称」
        String 名称 = 取文本(sections.path(DocSections.NAME).path("content"));
        md.append("# ").append(名称.isEmpty() ? "需求文档" : 名称).append("\n\n");

        渲染背景(md, sections.path(DocSections.ASIS));
        渲染痛点(md, sections.path(DocSections.PAIN));
        渲染目标(md, sections.path(DocSections.TOBE));
        渲染功能清单(md, sections.path(DocSections.FUNCTIONS));
        渲染非功能(md, sections.path(DocSections.NFR));
        渲染联动范围(md, sections.path(DocSections.SCOPE));
        渲染优先级(md, sections.path(DocSections.PRIORITY));
        渲染工作量(md, sections.path(DocSections.EFFORT));
        渲染验收标准(md, sections.path(DocSections.ACCEPTANCE));
        渲染附件清单(md, sections.path(DocSections.ATTACHMENTS));
        渲染预审结论(md, sections.path(DocSections.AI_REVIEW));

        return md.toString();
    }

    /**
     * 二级标题 + 正文段落。第 2 节（业务背景）走这条路。
     *
     * @param md   输出
     * @param node 节节点
     */
    private static void 渲染背景(StringBuilder md, JsonNode node) {
        md.append("## ").append(DocSections.title(DocSections.ASIS)).append("\n\n");
        md.append(或待补充(取文本(node.path("content")))).append("\n\n");
    }

    /**
     * 第 3 节：正文 + 量化指标列表。
     *
     * @param md   输出
     * @param node 节节点
     */
    private static void 渲染痛点(StringBuilder md, JsonNode node) {
        md.append("## ").append(DocSections.title(DocSections.PAIN)).append("\n\n");
        md.append(或待补充(取文本(node.path("content")))).append("\n\n");

        JsonNode metrics = node.path("metrics");
        if (metrics.isArray() && !metrics.isEmpty()) {
            md.append("| 指标 | 数值 |\n| --- | --- |\n");
            for (JsonNode 项 : metrics) {
                md.append("| ").append(取文本(项.path("name")))
                        .append(" | ").append(取文本(项.path("value")))
                        .append(" |\n");
            }
            md.append('\n');
        }
    }

    /**
     * 第 4 节：期望目标。按契约不写实现方式，这里只照原样输出，不做加工。
     *
     * @param md   输出
     * @param node 节节点
     */
    private static void 渲染目标(StringBuilder md, JsonNode node) {
        md.append("## ").append(DocSections.title(DocSections.TOBE)).append("\n\n");
        md.append(或待补充(取文本(node.path("content")))).append("\n\n");
    }

    /**
     * 第 5 节：功能清单表格。带 F-xx 编号，供点检项生成与变更比对引用。
     *
     * @param md   输出
     * @param node 节节点
     */
    private static void 渲染功能清单(StringBuilder md, JsonNode node) {
        md.append("## ").append(DocSections.title(DocSections.FUNCTIONS)).append("\n\n");
        JsonNode items = node.path("items");
        if (!items.isArray() || items.isEmpty()) {
            md.append("（待补充）\n\n");
            return;
        }
        md.append("| 编号 | 功能 | 优先级 | 类型 |\n| --- | --- | --- | --- |\n");
        for (JsonNode 项 : items) {
            md.append("| ").append(取文本(项.path("code")))
                    .append(" | ").append(取文本(项.path("text")))
                    .append(" | ").append(取文本(项.path("priority")))
                    .append(" | ").append(取文本(项.path("type")))
                    .append(" |\n");
        }
        md.append('\n');
    }

    /**
     * 第 6 节：非功能要求，按类别列。
     *
     * @param md   输出
     * @param node 节节点
     */
    private static void 渲染非功能(StringBuilder md, JsonNode node) {
        md.append("## ").append(DocSections.title(DocSections.NFR)).append("\n\n");
        JsonNode items = node.path("items");
        if (!items.isArray() || items.isEmpty()) {
            md.append("（无）\n\n");
            return;
        }
        md.append("| 类别 | 要求 |\n| --- | --- |\n");
        for (JsonNode 项 : items) {
            md.append("| ").append(类别名(取文本(项.path("category"))))
                    .append(" | ").append(取文本(项.path("text")))
                    .append(" |\n");
        }
        md.append('\n');
    }

    /**
     * 第 7 节：联动范围。系统和部门分开列，每项带一句影响说明。
     *
     * @param md   输出
     * @param node 节节点
     */
    private static void 渲染联动范围(StringBuilder md, JsonNode node) {
        md.append("## ").append(DocSections.title(DocSections.SCOPE)).append("\n\n");

        List<String> 行 = new ArrayList<>();
        for (JsonNode 项 : node.path("systems")) {
            行.add("- 系统 " + 取文本(项.path("systemCode")) + "：" + 取文本(项.path("impact")));
        }
        for (JsonNode 项 : node.path("depts")) {
            行.add("- 部门 " + 取文本(项.path("deptCode")) + "：" + 取文本(项.path("impact")));
        }
        if (行.isEmpty()) {
            md.append("（待补充）\n\n");
            return;
        }
        for (String 一行 : 行) {
            md.append(一行).append('\n');
        }
        md.append('\n');
    }

    /**
     * 第 8 节：优先级。打分三项 + 最终等级 + 理由。
     *
     * @param md   输出
     * @param node 节节点
     */
    private static void 渲染优先级(StringBuilder md, JsonNode node) {
        md.append("## ").append(DocSections.title(DocSections.PRIORITY)).append("\n\n");
        JsonNode scores = node.path("scores");
        md.append("- 最终等级：").append(或待补充(取文本(node.path("level")))).append('\n');
        md.append("- 打分：影响 ").append(scores.path("impact").asInt(0))
                .append(" / 紧急 ").append(scores.path("urgency").asInt(0))
                .append(" / 合规 ").append(scores.path("compliance").asInt(0))
                .append('\n');
        md.append("- 理由：").append(或待补充(取文本(node.path("reason")))).append("\n\n");
    }

    /**
     * 第 9 节：工作量区间。AI 估算的必须带水印提示，避免被当成已确认值。
     *
     * @param md   输出
     * @param node 节节点
     */
    private static void 渲染工作量(StringBuilder md, JsonNode node) {
        md.append("## ").append(DocSections.title(DocSections.EFFORT)).append("\n\n");
        double 最小 = node.path("minDays").asDouble(0);
        double 最大 = node.path("maxDays").asDouble(0);
        if (最小 <= 0 && 最大 <= 0) {
            md.append("（待补充）\n\n");
            return;
        }
        md.append("- 预估人天：").append(String.format(人天格式, 最小))
                .append(" ~ ").append(String.format(人天格式, 最大)).append('\n');

        String 建议上线 = 取文本(node.path("suggestReleaseDate"));
        if (!建议上线.isEmpty()) {
            md.append("- 建议上线日期：").append(建议上线).append('\n');
        }
        if (node.path("aiEstimated").asInt(0) == 1) {
            md.append("- 以上为 AI 估算，需 MIT 确认");
        }
        md.append('\n');
    }

    /**
     * 第 10 节：验收标准。有序列表，序号即覆盖率分母的键。
     *
     * @param md   输出
     * @param node 节节点
     */
    private static void 渲染验收标准(StringBuilder md, JsonNode node) {
        md.append("## ").append(DocSections.title(DocSections.ACCEPTANCE)).append("\n\n");
        JsonNode items = node.path("items");
        if (!items.isArray() || items.isEmpty()) {
            md.append("（待补充）\n\n");
            return;
        }
        for (JsonNode 项 : items) {
            md.append(项.path("seq").asInt(0)).append(". ")
                    .append(取文本(项.path("text"))).append('\n');
        }
        md.append('\n');
    }

    /**
     * 第 11 节：附件清单。用于核对「清单里有、附件区没传」。
     *
     * @param md   输出
     * @param node 节节点
     */
    private static void 渲染附件清单(StringBuilder md, JsonNode node) {
        md.append("## ").append(DocSections.title(DocSections.ATTACHMENTS)).append("\n\n");
        JsonNode items = node.path("items");
        if (!items.isArray() || items.isEmpty()) {
            md.append("（无）\n\n");
            return;
        }
        for (JsonNode 项 : items) {
            md.append("- ").append(取文本(项.path("name")))
                    .append("（").append(取文本(项.path("attachType"))).append("）\n");
        }
        md.append('\n');
    }

    /**
     * 第 12 节：AI 预审结论。这一段是给评审人看的，不是给人改的。
     *
     * @param md   输出
     * @param node 节节点
     */
    private static void 渲染预审结论(StringBuilder md, JsonNode node) {
        md.append("## ").append(DocSections.title(DocSections.AI_REVIEW)).append("\n\n");
        md.append("- 查重相似度：").append(node.path("dupScore").asDouble(0)).append('\n');
        md.append("- 查重结论：").append(或待补充(取文本(node.path("verdict")))).append('\n');
        md.append("- 完整度评分：").append(node.path("completeness").asInt(0)).append('\n');

        JsonNode risks = node.path("risks");
        if (risks.isArray() && !risks.isEmpty()) {
            StringBuilder 风险 = new StringBuilder();
            for (JsonNode 项 : risks) {
                if (!风险.isEmpty()) {
                    风险.append("；");
                }
                风险.append(取文本(项));
            }
            md.append("- 风险提示：").append(风险).append('\n');
        }

        JsonNode unclarified = node.path("unclarified");
        if (unclarified.isArray() && !unclarified.isEmpty()) {
            StringBuilder 未澄清 = new StringBuilder();
            for (JsonNode 项 : unclarified) {
                if (!未澄清.isEmpty()) {
                    未澄清.append("；");
                }
                未澄清.append(取文本(项));
            }
            md.append("- 未澄清项：").append(未澄清).append('\n');
        }
        md.append('\n');
    }

    /**
     * 取节点文本值，null 节点返回空串。
     *
     * @param node 节点
     * @return 文本
     */
    private static String 取文本(JsonNode node) {
        return node == null || node.isNull() ? "" : node.asText("");
    }

    /**
     * 空内容统一显示为「待补充」，避免文档里出现空白节。
     *
     * @param 文本 原文
     * @return 兜底后的文本
     */
    private static String 或待补充(String 文本) {
        return 文本 == null || 文本.trim().isEmpty() ? "（待补充）" : 文本;
    }

    /**
     * 非功能类别码转中文。码值由契约 §8.1.2 定死。
     *
     * @param category 类别码
     * @return 中文名；未知码原样返回
     */
    private static String 类别名(String category) {
        return switch (category) {
            case "PERF" -> "性能";
            case "COMPAT" -> "兼容性";
            case "SECURITY" -> "安全";
            case "RETENTION" -> "数据保留";
            case "OTHER" -> "其他";
            default -> category;
        };
    }
}
