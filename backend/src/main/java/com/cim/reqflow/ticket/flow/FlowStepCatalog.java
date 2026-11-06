package com.cim.reqflow.ticket.flow;

import java.util.List;

/**
 * 详情页步骤条的节点定义：10 步覆盖 24 个流程环节。
 * <p>
 * <b>为什么后端也要有这张表</b>：以前主链映射只写在前端 {@code ticket-steps.vue} 里，
 * 步骤条能画、但算不出「这一步谁在处理、交了什么」。现在后端要按同一套节点组装人和材料，
 * 两处各写一份必然错位 —— 图上前端说这是「设计」，后端按另一套映射去捞日志，捞出来的
 * 是别人的记录。所以映射搬到这里，前端只负责渲染。
 * <p>
 * <b>为什么是 10 步而不是 24 个</b>：用户 2026-11-04 拍板 —— 三方确认、需求评审、设计评审、
 * 点检这些「停留即审批」的环节并进相邻主节点，24 个全铺开在详情页就是一条挤不下的小字。
 * 被并掉的节点编号仍记在 {@code nodeCodes} 里，鼠标悬停时列出来，知道自己停在第几号环节。
 *
 * @param key          步骤键，前端用来做 key 与图标
 * @param label        步骤名
 * @param states       该步覆盖的状态码，含被并进来的审批态
 * @param nodeCodes    该步覆盖的流程节点编号，仅用于展示
 * @param plannedRoles 还没走到这步时，预计由哪些角色处理
 * @author Haijie Xiao
 * @date 2026-11-05
 */
public record FlowStepCatalog(String key, String label, List<String> states,
                              List<String> nodeCodes, List<String> plannedRoles) {

    /** 全部步骤，顺序即流转顺序 */
    public static final List<FlowStepCatalog> STEPS = List.of(

            new FlowStepCatalog("PROPOSE", "提出需求",
                    List.of(TicketStatus.DRAFT, TicketStatus.SUBMITTED,
                            TicketStatus.AI_ANALYZING, TicketStatus.AI_CLARIFYING,
                            TicketStatus.REQ_DOC_DRAFTED),
                    List.of("N01", "N02", "N03", "N04", "N05", "N06"),
                    List.of(FlowRoleSets.REPORTER)),

            new FlowStepCatalog("CONFIRM", "需求确认",
                    // 三个 legacy 码要一起收：一批历史单的三方确认是串行走的，
                    // 日志里的目标状态还是 CONF_MIT / CONF_PLATFORM 这些旧码。
                    // 只写 CONFIRMING 的话，这批单在这一步只剩最后一个确认人，前面的人全丢。
                    List.of(TicketStatus.CONFIRMING,
                            "CONF_MIT", "CONF_PLATFORM", "CONF_SUPERIOR"),
                    List.of("N07"),
                    List.of(FlowRoleSets.MIT_LEADER, FlowRoleSets.PLATFORM_LEADER,
                            FlowRoleSets.DEPT_MANAGER)),

            new FlowStepCatalog("ASSIGN", "派单",
                    // 需求评审没有独立一档，并进派单：评审通过紧接着就是派单，
                    // 拆开只会多一个只停几分钟的格子
                    List.of(TicketStatus.REQ_REVIEW, TicketStatus.PENDING_ASSIGN,
                            TicketStatus.ASSIGNED),
                    List.of("N10", "N11", "N12"),
                    List.of(FlowRoleSets.MIT_LEADER, FlowRoleSets.MIT_DEV)),

            new FlowStepCatalog("DESIGN", "设计",
                    // 同 CONFIRM：设计评审的旧码也要收，否则历史单的设计评审人凭空消失
                    List.of(TicketStatus.DESIGNING, TicketStatus.DSGN_REVIEWING,
                            "DSGN_REVIEW_IT", "DSGN_REVIEW_PLATFORM", "DSGN_REVIEW_USER"),
                    List.of("N13", "N14"),
                    List.of(FlowRoleSets.MIT_DEV, FlowRoleSets.IT_OPS,
                            FlowRoleSets.PLATFORM_LEADER, FlowRoleSets.REPORTER)),

            new FlowStepCatalog("DEV", "开发",
                    List.of(TicketStatus.DEVELOPING, TicketStatus.SELF_TEST),
                    List.of("N17", "N18"),
                    List.of(FlowRoleSets.MIT_DEV)),

            new FlowStepCatalog("UAT", "UAT 验收",
                    List.of(TicketStatus.DEPLOYING, TicketStatus.UAT),
                    List.of("N19", "N20"),
                    List.of(FlowRoleSets.REPORTER)),

            new FlowStepCatalog("RELEASE", "上线",
                    List.of(TicketStatus.RELEASE_PENDING, TicketStatus.RELEASED),
                    List.of("N21", "N22"),
                    List.of(FlowRoleSets.MIT_LEADER)),

            new FlowStepCatalog("RUN", "运行",
                    List.of(TicketStatus.MONITORING, TicketStatus.IT_INSPECTION),
                    List.of("N23", "N24", "N25"),
                    List.of(FlowRoleSets.MIT_DEV, FlowRoleSets.IT_OPS)),

            new FlowStepCatalog("EVAL", "评价",
                    List.of(TicketStatus.EVALUATING),
                    List.of("N27"),
                    List.of(FlowRoleSets.REPORTER)),

            // 最后一步没有「预计处理人」：关闭是终点，不再交给谁
            new FlowStepCatalog("CLOSE", "关闭",
                    List.of(TicketStatus.CLOSED),
                    List.of("N28"),
                    List.of()));

    /**
     * 状态码属于哪一步。
     * <p>
     * 挂起与挂起待评审不在这张表里：它们不是主链上的一步，而是任意一步的旁支。
     * 调用方拿到 -1 时自己决定怎么标（详情页把当前步标红并注明挂起）。
     *
     * @param status 状态码
     * @return 步骤下标，找不到返回 -1
     */
    public static int indexOfState(String status) {
        if (status == null) {
            return -1;
        }
        for (int i = 0; i < STEPS.size(); i++) {
            if (STEPS.get(i).states().contains(status)) {
                return i;
            }
        }
        return -1;
    }
}
