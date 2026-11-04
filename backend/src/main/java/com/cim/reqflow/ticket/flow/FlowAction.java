package com.cim.reqflow.ticket.flow;

import java.util.Set;

import static com.cim.reqflow.ticket.flow.CountScope.GROUP;
import static com.cim.reqflow.ticket.flow.CountScope.NONE;
import static com.cim.reqflow.ticket.flow.CountScope.PERSON;
import static com.cim.reqflow.ticket.flow.CountScope.TICKET;
import static com.cim.reqflow.ticket.flow.FlowRoleSets.ALL_STAFF;
import static com.cim.reqflow.ticket.flow.FlowRoleSets.MIT_CONF;
import static com.cim.reqflow.ticket.flow.FlowRoleSets.PLATFORM_CONF;
import static com.cim.reqflow.ticket.flow.FlowRoleSets.SUPERIOR_CONF;
import static com.cim.reqflow.ticket.flow.FlowRoleSets.DEVS;
import static com.cim.reqflow.ticket.flow.FlowRoleSets.IT_ROLES;
import static com.cim.reqflow.ticket.flow.FlowRoleSets.LEADERS;
import static com.cim.reqflow.ticket.flow.FlowRoleSets.REPORTERS;
import static com.cim.reqflow.ticket.flow.FlowRoleSets.REVIEWERS;
import static com.cim.reqflow.ticket.flow.FlowRoleSets.SYSTEM_ROLES;
import static com.cim.reqflow.ticket.flow.TicketStatus.ASSIGNED;
import static com.cim.reqflow.ticket.flow.TicketStatus.AI_ANALYZING;
import static com.cim.reqflow.ticket.flow.TicketStatus.AI_CLARIFYING;
import static com.cim.reqflow.ticket.flow.TicketStatus.CLOSED;
import static com.cim.reqflow.ticket.flow.TicketStatus.CONF_MIT;
import static com.cim.reqflow.ticket.flow.TicketStatus.CONF_PLATFORM;
import static com.cim.reqflow.ticket.flow.TicketStatus.CONF_SUPERIOR;
import static com.cim.reqflow.ticket.flow.TicketStatus.DEPLOYING;
import static com.cim.reqflow.ticket.flow.TicketStatus.DESIGNING;
import static com.cim.reqflow.ticket.flow.TicketStatus.DEVELOPING;
import static com.cim.reqflow.ticket.flow.TicketStatus.DRAFT;
import static com.cim.reqflow.ticket.flow.TicketStatus.DSGN_REVIEW_IT;
import static com.cim.reqflow.ticket.flow.TicketStatus.DSGN_REVIEW_PLATFORM;
import static com.cim.reqflow.ticket.flow.TicketStatus.DSGN_REVIEW_USER;
import static com.cim.reqflow.ticket.flow.TicketStatus.EVALUATING;
import static com.cim.reqflow.ticket.flow.TicketStatus.IT_INSPECTION;
import static com.cim.reqflow.ticket.flow.TicketStatus.MONITORING;
import static com.cim.reqflow.ticket.flow.TicketStatus.PENDING_ASSIGN;
import static com.cim.reqflow.ticket.flow.TicketStatus.RELEASED;
import static com.cim.reqflow.ticket.flow.TicketStatus.RELEASE_PENDING;
import static com.cim.reqflow.ticket.flow.TicketStatus.REQ_DOC_DRAFTED;
import static com.cim.reqflow.ticket.flow.TicketStatus.REQ_REVIEW;
import static com.cim.reqflow.ticket.flow.TicketStatus.SELF_TEST;
import static com.cim.reqflow.ticket.flow.TicketStatus.SUBMITTED;
import static com.cim.reqflow.ticket.flow.TicketStatus.SUSPENDED;
import static com.cim.reqflow.ticket.flow.TicketStatus.SUSPENDED_REVIEW;
import static com.cim.reqflow.ticket.flow.TicketStatus.UAT;

/**
 * 需求流转动作（61 个语义动作，63 个枚举常量）。
 * <p>
 * 三条约定，破坏任意一条都会让状态机在某个角落出诡异 bug：
 * <p>
 * 1. **一个常量 = 一条 from → to 边**。不允许一码两目标：⑥ 上线驳回与 ⑮ 验收不通过
 *    各有两条去处，拆成 _TEST / _DESIGN 两个常量。唯一的多值 from 是跳过确认，
 *    已拆成 SKIP_CONF_MIT / SKIP_CONF_PLATFORM / SKIP_CONF_SUPERIOR 三个常量。
 * 2. **不改状态的写操作也是动作**（INSPECT_PASS 的 to = from，UAT_RAISE_BUG 只生成子单），
 *    统一走 /action 入口、统一写 req_flow_log，否则这部分操作没有留痕，审计断链。
 * 3. **系统动作同样有码**，roles 为空集。跳过确认、AI 出文档、定时器转评价都要留痕，
 *    否则「谁在什么时候把单推走的」查不出来。
 * <p>
 * 完整规则与判定矩阵见 12-动作码与权限矩阵.md，本枚举是它的代码投影。
 *
 * @author Haijie Xiao
 * @date 2026-11-04
 */
public enum FlowAction {

    // ================= A 提出与 AI 分析 =================

    /** 提交：提单校验挂在服务层，这里只描述迁移 */
    SUBMIT("SUBMIT", "提交", DRAFT, SUBMITTED, OwnerRule.REPORTER, ALL_STAFF, null, NONE, "ticket:create"),

    /** 系统取任务：SUBMITTED 进分析队列 */
    AI_PICK("AI_PICK", "系统取任务", SUBMITTED, AI_ANALYZING, OwnerRule.NONE, SYSTEM_ROLES, null, NONE, null),

    /** 发起澄清：完整度不足时反问，最多 3 轮 */
    AI_ASK("AI_ASK", "发起澄清", AI_ANALYZING, AI_CLARIFYING, OwnerRule.NONE, SYSTEM_ROLES, null, NONE, null),

    /** 出需求文档 */
    AI_DOC_READY("AI_DOC_READY", "生成需求文档", AI_ANALYZING, REQ_DOC_DRAFTED,
            OwnerRule.NONE, SYSTEM_ROLES, null, NONE, null),

    /** 超时降级：5 分钟未完成，出模板空壳并标 ai_degraded=1，不阻断提单 */
    AI_DEGRADE("AI_DEGRADE", "AI 超时降级", AI_ANALYZING, REQ_DOC_DRAFTED,
            OwnerRule.NONE, SYSTEM_ROLES, null, NONE, null),

    /** 提交澄清答复：答完回到分析态，重新判断信息是否足够 */
    CLARIFY_ANSWER("CLARIFY_ANSWER", "提交澄清答复", AI_CLARIFYING, AI_ANALYZING,
            OwnerRule.REPORTER, REPORTERS, null, NONE, "ticket:clarify"),

    /** 超轮出文档：第 3 轮结束仍缺信息，带「待补充项」直接出文档，不无限循环 */
    CLARIFY_FORCE_DOC("CLARIFY_FORCE_DOC", "超轮出文档", AI_CLARIFYING, REQ_DOC_DRAFTED,
            OwnerRule.NONE, SYSTEM_ROLES, null, NONE, null),

    /** 放弃：提出人不想提了 */
    CLARIFY_ABANDON("CLARIFY_ABANDON", "放弃提单", AI_CLARIFYING, DRAFT,
            OwnerRule.REPORTER, REPORTERS, null, NONE, "ticket:create"),

    /** 退回重生成（退回点 ①）：不计数，本人反复改不该被惩罚 */
    DOC_REGENERATE("DOC_REGENERATE", "退回重生成", REQ_DOC_DRAFTED, AI_CLARIFYING,
            OwnerRule.REPORTER, REPORTERS, ReturnPoint.DOC_REGEN, NONE, "ticket:doc-regenerate"),

    /** 确认需求文档：必传 REQ_DOC，没文档不许进三方确认 */
    DOC_CONFIRM("DOC_CONFIRM", "确认需求文档", REQ_DOC_DRAFTED, CONF_MIT,
            OwnerRule.REPORTER, REPORTERS, null, NONE, "ticket:doc-confirm"),

    /** 判定重复关闭：AI 建议不拦，人来定 */
    DUP_CLOSE("DUP_CLOSE", "判定重复关闭", REQ_DOC_DRAFTED, CLOSED,
            OwnerRule.REPORTER, REPORTERS, null, NONE, "ticket:close"),

    // ================= B 三方确认（固定串行） =================

    CONF_MIT_PASS("CONF_MIT_PASS", "MIT 确认通过", CONF_MIT, CONF_PLATFORM,
            OwnerRule.CONFIRMER, MIT_CONF, null, NONE, "ticket:conf-mit"),

    CONF_MIT_REJECT("CONF_MIT_REJECT", "MIT 驳回", CONF_MIT, DRAFT,
            OwnerRule.CONFIRMER, MIT_CONF, ReturnPoint.CONF_MIT, TICKET, "ticket:conf-mit"),

    CONF_PLATFORM_PASS("CONF_PLATFORM_PASS", "平台确认通过", CONF_PLATFORM, CONF_SUPERIOR,
            OwnerRule.CONFIRMER, PLATFORM_CONF, null, NONE, "ticket:conf-platform"),

    CONF_PLATFORM_REJECT("CONF_PLATFORM_REJECT", "平台驳回", CONF_PLATFORM, DRAFT,
            OwnerRule.CONFIRMER, PLATFORM_CONF, ReturnPoint.CONF_PLATFORM, TICKET, "ticket:conf-platform"),

    CONF_SUPERIOR_PASS("CONF_SUPERIOR_PASS", "上级确认通过", CONF_SUPERIOR, REQ_REVIEW,
            OwnerRule.CONFIRMER, SUPERIOR_CONF, null, NONE, "ticket:leader-confirm"),

    CONF_SUPERIOR_REJECT("CONF_SUPERIOR_REJECT", "上级驳回", CONF_SUPERIOR, DRAFT,
            OwnerRule.CONFIRMER, SUPERIOR_CONF, ReturnPoint.CONF_SUPERIOR, TICKET, "ticket:leader-confirm"),

    /** 跳过 MIT 确认：提出人本人就是 MIT Leader，只免「人点一下」，不免 SLA 计时口径 */
    SKIP_CONF_MIT("SKIP_CONF_MIT", "跳过 MIT 确认", CONF_MIT, CONF_PLATFORM,
            OwnerRule.NONE, SYSTEM_ROLES, null, NONE, null),

    /** 跳过平台确认 */
    SKIP_CONF_PLATFORM("SKIP_CONF_PLATFORM", "跳过平台确认", CONF_PLATFORM, CONF_SUPERIOR,
            OwnerRule.NONE, SYSTEM_ROLES, null, NONE, null),

    /** 跳过上级确认 */
    SKIP_CONF_SUPERIOR("SKIP_CONF_SUPERIOR", "跳过上级确认", CONF_SUPERIOR, REQ_REVIEW,
            OwnerRule.NONE, SYSTEM_ROLES, null, NONE, null),

    // ================= C 评审立项 =================

    /** 评审通过：必传评审纪要，它是立项依据 */
    REVIEW_PASS("REVIEW_PASS", "评审通过", REQ_REVIEW, PENDING_ASSIGN,
            OwnerRule.LEADER, REVIEWERS, null, NONE, "ticket:req-review"),

    /** 评审驳回：回 DRAFT 后不自动重提也不自动关单，由提出人决定（02 6.2 ⑧） */
    REVIEW_REJECT("REVIEW_REJECT", "评审驳回", REQ_REVIEW, DRAFT,
            OwnerRule.LEADER, REVIEWERS, ReturnPoint.REQ_REVIEW, TICKET, "ticket:req-review"),

    /** 派单：指派对象与预估工时必填 */
    ASSIGN("ASSIGN", "派单", PENDING_ASSIGN, ASSIGNED,
            OwnerRule.LEADER, LEADERS, null, NONE, "ticket:assign"),

    /** 不予立项 */
    ASSIGN_REJECT("ASSIGN_REJECT", "不予立项", PENDING_ASSIGN, CLOSED,
            OwnerRule.LEADER, LEADERS, ReturnPoint.ASSIGN_REJECT, TICKET, "ticket:reject"),

    /** 退回补充 */
    ASSIGN_RETURN("ASSIGN_RETURN", "退回补充", PENDING_ASSIGN, DRAFT,
            OwnerRule.LEADER, LEADERS, ReturnPoint.ASSIGN_REJECT, TICKET, "ticket:reject"),

    // ================= D 派单与设计 =================

    ACCEPT("ACCEPT", "接收", ASSIGNED, DESIGNING,
            OwnerRule.ASSIGNEE, DEVS, null, NONE, "ticket:accept"),

    /** 拒接（退回点 ③）：单人 2 次禁用该人拒接，同单累计 3 次进评审 */
    REFUSE("REFUSE", "拒接", ASSIGNED, PENDING_ASSIGN,
            OwnerRule.ASSIGNEE, DEVS, ReturnPoint.ASSIGN_REFUSE, PERSON, "ticket:refuse"),

    /** 提交设计：必传 FDS */
    SUBMIT_DESIGN("SUBMIT_DESIGN", "提交设计", DESIGNING, DSGN_REVIEW_IT,
            OwnerRule.ASSIGNEE, DEVS, null, NONE, "ticket:design"),

    DSGN_IT_PASS("DSGN_IT_PASS", "IT 评审通过", DSGN_REVIEW_IT, DSGN_REVIEW_PLATFORM,
            OwnerRule.LEADER, IT_ROLES, null, NONE, "ticket:dsgn-review-it"),

    /** IT 评审退回（退回点 ⑫）：三步合计 3 次进评审，不是单点 3 次 */
    DSGN_IT_RETURN("DSGN_IT_RETURN", "IT 评审退回", DSGN_REVIEW_IT, DESIGNING,
            OwnerRule.LEADER, IT_ROLES, ReturnPoint.DSGN_IT, GROUP, "ticket:dsgn-review-it"),

    DSGN_PLATFORM_PASS("DSGN_PLATFORM_PASS", "平台评审通过", DSGN_REVIEW_PLATFORM, DSGN_REVIEW_USER,
            OwnerRule.CONFIRMER, PLATFORM_CONF, null, NONE, "ticket:dsgn-review-platform"),

    DSGN_PLATFORM_RETURN("DSGN_PLATFORM_RETURN", "平台评审退回", DSGN_REVIEW_PLATFORM, DESIGNING,
            OwnerRule.CONFIRMER, PLATFORM_CONF, ReturnPoint.DSGN_PLATFORM, GROUP,
            "ticket:dsgn-review-platform"),

    /** 用户评审通过：验收标准在这一步锁定 */
    DSGN_USER_PASS("DSGN_USER_PASS", "用户评审通过", DSGN_REVIEW_USER, DEVELOPING,
            OwnerRule.REPORTER, REPORTERS, null, NONE, "ticket:dsgn-review-user"),

    DSGN_USER_RETURN("DSGN_USER_RETURN", "用户评审退回", DSGN_REVIEW_USER, DESIGNING,
            OwnerRule.REPORTER, REPORTERS, ReturnPoint.DSGN_USER, GROUP, "ticket:dsgn-review-user"),

    // ================= F 开发测试 =================

    SUBMIT_SELF_TEST("SUBMIT_SELF_TEST", "提交自测", DEVELOPING, SELF_TEST,
            OwnerRule.ASSIGNEE, DEVS, null, NONE, "ticket:self-test"),

    /**
     * 自测通过：一期手工部署，直跳 UAT，不经 DEPLOYING。
     * 二期接 Jenkins 后改成按配置项切换目标状态（SELF_TEST → DEPLOYING）。
     */
    SELF_TEST_PASS("SELF_TEST_PASS", "自测通过", SELF_TEST, UAT,
            OwnerRule.ASSIGNEE, DEVS, null, NONE, "ticket:self-test"),

    /** 自测不通过：内部循环，不是退回点，不计数 */
    SELF_TEST_FAIL("SELF_TEST_FAIL", "自测不通过", SELF_TEST, DEVELOPING,
            OwnerRule.ASSIGNEE, DEVS, null, NONE, "ticket:self-test"),

    DEPLOY_OK("DEPLOY_OK", "部署成功", DEPLOYING, UAT,
            OwnerRule.NONE, SYSTEM_ROLES, null, NONE, null),

    DEPLOY_FAIL("DEPLOY_FAIL", "部署失败", DEPLOYING, SELF_TEST,
            OwnerRule.NONE, SYSTEM_ROLES, null, NONE, null),

    /** UAT 通过：覆盖率必须 100%，或由 Leader 带理由豁免（服务层校验） */
    UAT_PASS("UAT_PASS", "UAT 通过", UAT, RELEASE_PENDING,
            OwnerRule.REPORTER, REPORTERS, null, NONE, "ticket:uat-pass"),

    UAT_FAIL("UAT_FAIL", "UAT 整体退回", UAT, DEVELOPING,
            OwnerRule.REPORTER, REPORTERS, ReturnPoint.UAT_FAIL, TICKET, "ticket:uat-fail"),

    /** 提 BUG 单：状态不变，生成子单并回填关联（退回点 ⑤ 的 BUG 分支不计数） */
    UAT_RAISE_BUG("UAT_RAISE_BUG", "提 BUG 单", UAT, UAT,
            OwnerRule.REPORTER, REPORTERS, null, NONE, "ticket:bug-create"),

    // ================= G 上线 =================

    /** 上线审批通过：上线申请单 + 回滚方案缺一不批；实习生禁止 */
    RELEASE_APPROVE("RELEASE_APPROVE", "上线审批通过", RELEASE_PENDING, RELEASED,
            OwnerRule.LEADER, LEADERS, null, NONE, "ticket:release-approve"),

    RELEASE_REJECT_TEST("RELEASE_REJECT_TEST", "驳回（测试问题）", RELEASE_PENDING, UAT,
            OwnerRule.LEADER, LEADERS, ReturnPoint.RELEASE_REJECT, TICKET, "ticket:reject"),

    RELEASE_REJECT_DESIGN("RELEASE_REJECT_DESIGN", "驳回（方案问题）",
            RELEASE_PENDING, DEVELOPING,
            OwnerRule.LEADER, LEADERS, ReturnPoint.RELEASE_REJECT, TICKET, "ticket:reject"),

    // ================= H 上线后验证 =================

    /** 验收确认：在 RELEASED 上执行，不单独占状态 */
    PROD_ACCEPT("PROD_ACCEPT", "上线验收确认", RELEASED, MONITORING,
            OwnerRule.REPORTER, REPORTERS, null, NONE, "ticket:prod-accept"),

    PROD_REJECT_FUNC("PROD_REJECT_FUNC", "验收不通过（功能）", RELEASED, UAT,
            OwnerRule.REPORTER, REPORTERS, ReturnPoint.PROD_ACCEPT, TICKET, "ticket:prod-reject"),

    PROD_REJECT_DESIGN("PROD_REJECT_DESIGN", "验收不通过（方案）", RELEASED, DEVELOPING,
            OwnerRule.REPORTER, REPORTERS, ReturnPoint.PROD_ACCEPT, TICKET, "ticket:prod-reject"),

    /** 监控通过：monitor_note 至少 20 字，防「正常」两个字糊弄 */
    MONITOR_PASS("MONITOR_PASS", "监控通过", MONITORING, IT_INSPECTION,
            OwnerRule.ASSIGNEE, DEVS, null, NONE, "ticket:monitor-submit"),

    MONITOR_RETURN("MONITOR_RETURN", "监控异常退回", MONITORING, DEVELOPING,
            OwnerRule.ASSIGNEE, DEVS, ReturnPoint.MONITOR, TICKET, "ticket:monitor-return"),

    /** 点检通过：状态不变，等满 7 天的定时器推进；清单为空时放行并标 check_items_empty */
    INSPECT_PASS("INSPECT_PASS", "点检通过", IT_INSPECTION, IT_INSPECTION,
            OwnerRule.LEADER, IT_ROLES, null, NONE, "check-item:edit"),

    INSPECT_FAIL("INSPECT_FAIL", "点检不通过", IT_INSPECTION, DEVELOPING,
            OwnerRule.LEADER, IT_ROLES, ReturnPoint.IT_CHECK, TICKET, "check-item:edit"),

    /** 满 7 天转评价：定时器触发，起算点 release_at，扣除 INC 占位时长（02 7.1） */
    STABLE_7D("STABLE_7D", "满 7 天转评价", IT_INSPECTION, EVALUATING,
            OwnerRule.NONE, SYSTEM_ROLES, null, NONE, null),

    EVAL_PASS("EVAL_PASS", "评价符合", EVALUATING, CLOSED,
            OwnerRule.REPORTER, REPORTERS, null, NONE, "ticket:evaluate"),

    /** 评价不符合：不回退，只给已闭环单据打负面标记（退回点 ⑨ 不计数） */
    EVAL_FAIL("EVAL_FAIL", "评价不符合", EVALUATING, CLOSED,
            OwnerRule.REPORTER, REPORTERS, ReturnPoint.EVAL_FAIL, NONE, "ticket:evaluate"),

    EVAL_TIMEOUT("EVAL_TIMEOUT", "逾期兜底关闭", EVALUATING, CLOSED,
            OwnerRule.NONE, SYSTEM_ROLES, null, NONE, null),

    /** 重开：仅超时关闭（close_reason=TIMEOUT）且 30 天内，服务层校验 */
    REOPEN("REOPEN", "重新打开", CLOSED, DRAFT,
            OwnerRule.REPORTER, REPORTERS, null, NONE, "ticket:close"),

    // ================= 全局动作 =================

    /** 挂起：任意在途状态可挂，终态不可挂 */
    SUSPEND("SUSPEND", "挂起", "*", SUSPENDED,
            OwnerRule.LEADER, LEADERS, null, NONE, "ticket:suspend"),

    /** 恢复：回到挂起前状态，SLA 按 02 7.2 顺延 */
    RESUME("RESUME", "恢复", SUSPENDED, FlowTarget.SUSPENDED_FROM,
            OwnerRule.LEADER, LEADERS, null, NONE, "ticket:suspend"),

    /** 置入待评审：同一退回点第 3 次由系统自动置入 */
    ESCALATE("ESCALATE", "置入待评审", "*", SUSPENDED_REVIEW,
            OwnerRule.NONE, SYSTEM_ROLES, null, NONE, null),

    /** 评审结论 · 继续：必须指定回到哪一步，不许回原状态 */
    REVIEW_CONTINUE("REVIEW_CONTINUE", "评审结论·继续", SUSPENDED_REVIEW, FlowTarget.TARGET_STATUS,
            OwnerRule.LEADER, LEADERS, null, NONE, "ticket:req-review"),

    REVIEW_REASSIGN("REVIEW_REASSIGN", "评审结论·重派", SUSPENDED_REVIEW, PENDING_ASSIGN,
            OwnerRule.LEADER, LEADERS, null, NONE, "ticket:req-review"),

    REVIEW_CLOSE("REVIEW_CLOSE", "评审结论·关闭", SUSPENDED_REVIEW, CLOSED,
            OwnerRule.LEADER, LEADERS, null, NONE, "ticket:req-review");

    /** 动作码，与枚举名一致，显式存一份便于日志与查表 */
    private final String code;

    /** 按钮文案 */
    private final String label;

    /** 前置状态；* 表示任意在途状态（仅全局动作使用） */
    private final String fromStatus;

    /** 目标状态；或 TO_SUSPENDED_FROM / TO_TARGET_STATUS 两个占位符 */
    private final String toStatus;

    /** 归属判定规则 */
    private final OwnerRule ownerRule;

    /** 角色白名单，空集代表只允许系统触发 */
    private final Set<String> roles;

    /** 退回点码，非退回动作为 null */
    private final String returnPoint;

    /** 计次维度 */
    private final CountScope countScope;

    /** 按钮权限码，系统动作为 null */
    private final String authCode;

    FlowAction(String code, String label, String fromStatus, String toStatus,
               OwnerRule ownerRule, Set<String> roles, String returnPoint,
               CountScope countScope, String authCode) {
        this.code = code;
        this.label = label;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.ownerRule = ownerRule;
        this.roles = roles;
        this.returnPoint = returnPoint;
        this.countScope = countScope;
        this.authCode = authCode;
    }

    public String getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    public String getFromStatus() {
        return fromStatus;
    }

    public String getToStatus() {
        return toStatus;
    }

    public OwnerRule getOwnerRule() {
        return ownerRule;
    }

    public Set<String> getRoles() {
        return roles;
    }

    public String getReturnPoint() {
        return returnPoint;
    }

    public CountScope getCountScope() {
        return countScope;
    }

    public String getAuthCode() {
        return authCode;
    }

    /**
     * 是否系统动作。空角色集即「不授任何人」，只能由后端以 SYS 身份调用。
     *
     * @return true 表示系统动作
     */
    public boolean isSystemOnly() {
        return roles.isEmpty();
    }

    /**
     * 是否会让状态发生变化的动作。
     * 点检通过（to = from）与提 BUG 单（只生成子单）都返回 false，
     * 用于决定要不要写 from_status / to_status 相同的流转记录、要不要重算 SLA。
     *
     * @return true 表示状态会变
     */
    public boolean changesStatus() {
        return !fromStatus.equals(toStatus)
                && !FlowTarget.SUSPENDED_FROM.equals(toStatus)
                && !FlowTarget.TARGET_STATUS.equals(toStatus);
    }

    /**
     * 判断该动作是否允许从指定状态出发。
     *
     * @param status 当前状态
     * @return true 表示可以
     */
    public boolean matchesFrom(String status) {
        if ("*".equals(fromStatus)) {
            return TicketStatus.SUSPENDABLE.contains(status);
        }
        return fromStatus.equals(status);
    }
}
