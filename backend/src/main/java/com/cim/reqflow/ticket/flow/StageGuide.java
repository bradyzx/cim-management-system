package com.cim.reqflow.ticket.flow;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 环节指引：停在这个状态时，要拿到什么材料、谁在处理、做完交给谁。
 * <p>
 * 数据与流程图的两份产物同源（`需求管理系统/flow_data.json` 的 stateStages），
 * 改流程时两边要一起改 —— 图上写「设计评审中要 FDS + 原型」，系统里却提示
 * 别的材料，用户只会信一处，另一处就变成摆设。
 * <p>
 * <b>材料在这里只是提示，不是门禁。</b>真正拦流转的是 `req_attach_rule` 表 +
 * {@code PreconditionChecker}：规则可按项目、按状态配，还能配份数与是否必填。
 * 这里写成常量而不是查库，是因为「这一步理论上要交什么」是流程定义，
 * 不随项目变；如果把它做成可配的，每个项目都要维护一份，配漏了照样没人提示。
 * <p>
 * 单例表在类加载时建好，只读，查询是 O(1)。
 *
 * @param status          状态码，与 {@link TicketStatus} 一一对应
 * @param stageLabel      环节名，如「设计」
 * @param roleLabel       处理人，写岗位而不是具体账号
 * @param need            开工前要拿到的文档或材料
 * @param deliverable     这一步要落地的单据
 * @param deliverableType 交付物类型：`formal` 正式模板 / `record` 过程记录
 * @param handoff         做完交给谁
 * @author Haijie Xiao
 * @date 2026-11-05
 */
public record StageGuide(String status, String stageLabel, String roleLabel,
                         String need, String deliverable, String deliverableType,
                         String handoff) {

    /** 正式模板：要走公司模板、要签字或归档 */
    public static final String FORMAL = "formal";

    /** 过程记录：系统留痕或一句话结论，不进模板库 */
    public static final String RECORD = "record";

    /** 状态码 → 指引。用 LinkedHashMap 保持流程顺序，遍历时看到的就是流转顺序 */
    private static final Map<String, StageGuide> BY_STATUS = new LinkedHashMap<>();

    static {
        收录(TicketStatus.DRAFT, "草稿", "提出人",
                "问题描述 · 现场材料", "需求草案", RECORD,
                "本人提交");

        收录(TicketStatus.SUBMITTED, "待分析", "系统",
                "需求单全文", "需求单", RECORD,
                "系统 AI 预审");

        收录(TicketStatus.AI_ANALYZING, "AI 分析中", "系统",
                "需求单 · 历史需求库", "预审结论", RECORD,
                "提出人答澄清");

        收录(TicketStatus.AI_CLARIFYING, "待补充澄清", "提出人",
                "疑点问题清单", "澄清记录", RECORD,
                "系统重新分析");

        收录(TicketStatus.REQ_DOC_DRAFTED, "需求文档待确认", "提出人",
                "需求文档草稿", "需求文档", FORMAL,
                "MIT · 平台 · 上级三方确认");

        收录(TicketStatus.CONFIRMING, "三方确认中", "MIT Leader ＋ 平台负责人 ＋ 直属上级",
                "文档定版", "三方确认意见", RECORD,
                "评审领导评审");

        收录(TicketStatus.REQ_REVIEW, "需求评审", "评审领导",
                "定版文档 · 三方意见", "评审纪要", FORMAL,
                "MIT Leader 派单");

        收录(TicketStatus.PENDING_ASSIGN, "待分配", "MIT Leader",
                "评审纪要", "需求池记录", RECORD,
                "被指派的 MIT 开发");

        收录(TicketStatus.ASSIGNED, "待接收", "MIT 开发",
                "派工单", "派工单（接收）", FORMAL,
                "开发本人（进设计）");

        收录(TicketStatus.DESIGNING, "设计中", "MIT 开发",
                "需求文档 · FDS 模板", "功能设计文档 FDS", FORMAL,
                "IT · 平台 · 用户设计评审");

        收录(TicketStatus.DSGN_REVIEWING, "设计评审中", "IT 运维 ＋ 平台负责人 ＋ 提出人",
                "FDS · 原型", "设计评审意见", RECORD,
                "MIT 开发编码");

        收录(TicketStatus.DEVELOPING, "开发中", "MIT 开发",
                "FDS 定版", "代码提交记录", RECORD,
                "开发本人自测");

        收录(TicketStatus.SELF_TEST, "开发自测", "MIT 开发",
                "代码 · 测试用例", "用例 + 自测报告", FORMAL,
                "提出人 UAT");

        收录(TicketStatus.DEPLOYING, "部署中", "系统",
                "自测通过记录", "部署记录", RECORD,
                "提出人 UAT");

        收录(TicketStatus.UAT, "UAT 测试", "提出人",
                "测试地址 · 用例 · 验收标准", "UAT 测试/验收报告", FORMAL,
                "MIT Leader 上线审批");

        收录(TicketStatus.RELEASE_PENDING, "待上线", "MIT Leader",
                "UAT 报告 · 申请单 · 回滚方案", "上线申请单 + 回滚方案", FORMAL,
                "系统上线执行");

        收录(TicketStatus.RELEASED, "已上线 · 待验收", "提出人",
                "生产环境 · 上线记录", "上线记录", RECORD,
                "开发人员运行监控");

        收录(TicketStatus.MONITORING, "运行监控", "开发人员",
                "监控清单 · INC 记录", "运行监控记录", RECORD,
                "IT 运维点检");

        收录(TicketStatus.IT_INSPECTION, "IT 点检", "IT 运维",
                "重点功能清单 · 监控记录", "IT 点检报告", RECORD,
                "系统满 7 天计时");

        收录(TicketStatus.EVALUATING, "待评价", "提出人",
                "点检报告 · 运行数据", "需求评价表", FORMAL,
                "系统关闭归档");

        收录(TicketStatus.CLOSED, "已关闭", "系统 / MIT Leader",
                "评价表 · 全部交付物", "结项验收报告", FORMAL,
                "归档（流程终点）");

        // 两个全局态不在主链上，但停在这里时同样要告诉人该做什么：
        // 挂起要人推一把恢复，待评审要 Leader 给结论，都是真待办。
        收录(TicketStatus.SUSPENDED, "挂起", "MIT Leader",
                "挂起说明单", "挂起说明单", FORMAL,
                "恢复回原状态");

        收录(TicketStatus.SUSPENDED_REVIEW, "挂起 · 待评审", "MIT Leader ＋ 双方主管",
                "退回记录 · 挂起说明", "挂起恢复评审单", FORMAL,
                "Leader 出评审结论");
    }

    /**
     * 往表里放一条。
     * <p>
     * 单独抽一个方法只为一件事：23 条的写法保持一致，少写一类重复的构造参数。
     *
     * @param status          状态码
     * @param stageLabel      环节名
     * @param roleLabel       处理人
     * @param need            需要的材料
     * @param deliverable     交付物
     * @param deliverableType 交付物类型
     * @param handoff         交给谁
     */
    private static void 收录(String status, String stageLabel, String roleLabel,
                             String need, String deliverable, String deliverableType,
                             String handoff) {
        BY_STATUS.put(status, new StageGuide(
                status, stageLabel, roleLabel, need, deliverable, deliverableType, handoff));
    }

    /**
     * 按状态码取指引。
     *
     * @param status 状态码
     * @return 指引；未收录的状态返回 null，由调用方决定怎么兜底
     */
    public static StageGuide of(String status) {
        if (status == null) {
            return null;
        }
        return BY_STATUS.get(status);
    }

    /**
     * 全部指引，按流程顺序。
     * <p>
     * 供「流程说明」页与自检用。返回不可变列表，调用方改不动这张表。
     *
     * @return 全部环节指引
     */
    public static List<StageGuide> all() {
        return Collections.unmodifiableList(new ArrayList<>(BY_STATUS.values()));
    }

    /**
     * 是否正式模板。前端据此决定画 📄 还是 🧾，不由前端自己比字符串。
     *
     * @return 正式模板返回 true
     */
    public boolean formal() {
        return FORMAL.equals(deliverableType);
    }
}
