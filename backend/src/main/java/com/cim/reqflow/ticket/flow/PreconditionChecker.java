package com.cim.reqflow.ticket.flow;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.cim.reqflow.system.entity.SysDictDataDO;
import com.cim.reqflow.system.entity.SysUserDO;
import com.cim.reqflow.system.mapper.SysDictDataMapper;
import com.cim.reqflow.system.mapper.SysUserMapper;
import com.cim.reqflow.ticket.doc.DocValidator;
import com.cim.reqflow.ticket.dto.TicketActionDTO;
import com.cim.reqflow.ticket.entity.ReqAcceptanceCriteriaDO;
import com.cim.reqflow.ticket.entity.ReqAttachRuleDO;
import com.cim.reqflow.ticket.entity.ReqAttachmentDO;
import com.cim.reqflow.ticket.entity.ReqDocDO;
import com.cim.reqflow.ticket.entity.ReqTestCaseDO;
import com.cim.reqflow.ticket.entity.ReqTicketDO;
import com.cim.reqflow.ticket.mapper.ReqAcceptanceCriteriaMapper;
import com.cim.reqflow.ticket.mapper.ReqAttachRuleMapper;
import com.cim.reqflow.ticket.mapper.ReqAttachmentMapper;
import com.cim.reqflow.ticket.mapper.ReqDocMapper;
import com.cim.reqflow.ticket.mapper.ReqTestCaseMapper;
import com.cim.reqflow.system.service.UserAccessService;
import com.cim.reqflow.ticket.service.CheckItemService;
import com.cim.reqflow.ticket.testcase.CoverageCalculator;
import com.cim.reqflow.ticket.vo.CheckItemListVO;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 动作前置校验。返回**全量**阻塞项，不是遇到第一个就返回。
 * <p>
 * 为什么必须全量：用户补完附件再点，又被「缺少指派对象」拦住，来回三次就没人愿意用这套系统了。
 * 一次把所有问题列出来，用户一轮补完。
 * <p>
 * 校验只做「这次动作能不能执行」，不做权限判断 —— 权限在 TicketFlowService 里先判完，
 * 能走到这一步说明角色与归属都过了。
 *
 * @author Haijie Xiao
 * @date 2026-11-04
 */
@Component
public class PreconditionChecker {

    /** 附件规则：启用 */
    private static final Integer FLAG_ENABLED = 1;

    /** 附件规则：必填 */
    private static final Integer FLAG_REQUIRED = 1;

    /** 通配符：单据类型与状态的兜底匹配 */
    private static final String WILDCARD = "*";

    /** 字典类型：附件类型。用于把 REQ_DOC 这类码翻成「需求文档」 */
    private static final String DICT_ATTACH_TYPE = "attach_type";

    /** 运行监控结论最小字数，防止只写「正常」两个字 */
    private static final int MIN_MONITOR_NOTE_LENGTH = 20;

    /** 实习生四禁令覆盖的动作：上线审批、生产部署、驳回、关闭 */
    private static final Set<FlowAction> INTERN_FORBIDDEN = Set.of(
            FlowAction.RELEASE_APPROVE,
            FlowAction.RELEASE_REJECT_TEST, FlowAction.RELEASE_REJECT_DESIGN,
            FlowAction.CONF_MIT_REJECT, FlowAction.CONF_PLATFORM_REJECT,
            FlowAction.CONF_SUPERIOR_REJECT, FlowAction.REVIEW_REJECT,
            FlowAction.ASSIGN_REJECT, FlowAction.DUP_CLOSE, FlowAction.REVIEW_CLOSE,
            FlowAction.EVAL_PASS, FlowAction.EVAL_FAIL);

    /** 用工类型：实习生 */
    private static final String EMPLOYMENT_INTERN = "INTERN";

    /** 附件规则表 */
    private final ReqAttachRuleMapper reqAttachRuleMapper;

    /** 附件表 */
    private final ReqAttachmentMapper reqAttachmentMapper;

    /** 用户表，用于判断实习生 */
    private final SysUserMapper sysUserMapper;

    /** 需求文档表，用于「确认文档」前的完整性校验 */
    private final ReqDocMapper reqDocMapper;

    /** 字典，用于把附件类型码翻成中文 */
    private final SysDictDataMapper sysDictDataMapper;

    /** 验收标准，覆盖率的分母 */
    private final ReqAcceptanceCriteriaMapper reqAcceptanceCriteriaMapper;

    /** 测试用例，覆盖率的分子 */
    private final ReqTestCaseMapper reqTestCaseMapper;

    /** 点检清单，判断重点功能是否都已点检 */
    private final CheckItemService checkItemService;

    /** 退回计次 */
    private final ReturnCounter returnCounter;

    /** 用户角色查询，派单时用来校验承办人是不是开发角色 */
    private final UserAccessService userAccessService;

    public PreconditionChecker(ReqAttachRuleMapper reqAttachRuleMapper,
                               ReqAttachmentMapper reqAttachmentMapper,
                               SysUserMapper sysUserMapper,
                               ReqDocMapper reqDocMapper,
                               SysDictDataMapper sysDictDataMapper,
                               ReqAcceptanceCriteriaMapper reqAcceptanceCriteriaMapper,
                               ReqTestCaseMapper reqTestCaseMapper,
                               CheckItemService checkItemService,
                               ReturnCounter returnCounter,
                               UserAccessService userAccessService) {
        this.reqAttachRuleMapper = reqAttachRuleMapper;
        this.reqAttachmentMapper = reqAttachmentMapper;
        this.sysUserMapper = sysUserMapper;
        this.reqDocMapper = reqDocMapper;
        this.sysDictDataMapper = sysDictDataMapper;
        this.reqAcceptanceCriteriaMapper = reqAcceptanceCriteriaMapper;
        this.reqTestCaseMapper = reqTestCaseMapper;
        this.checkItemService = checkItemService;
        this.returnCounter = returnCounter;
        this.userAccessService = userAccessService;
    }

    /**
     * 渲染按钮时的校验：只判「已经能知道的事」——附件、退回次数、禁令、单据自身字段。
     * <p>
     * 不查入参字段（派单人是谁、工时多少）：那些要等用户点了按钮、在弹窗里填完才知道。
     * 如果这里也查，派单按钮会永远是灰的。
     *
     * @param ticket     单据
     * @param action     动作
     * @param operatorId 操作人
     * @return 阻塞项列表，空表示按钮可点
     */
    public List<Blocker> checkPreconditions(ReqTicketDO ticket, FlowAction action,
                                            String operatorId) {
        return checkInternal(ticket, action, operatorId, null);
    }

    /**
     * 执行动作前的全量校验，含入参字段。
     *
     * @param ticket     单据
     * @param action     动作
     * @param operatorId 操作人
     * @param request    请求参数
     * @return 阻塞项列表，空表示可以执行
     */
    public List<Blocker> checkBeforeExecute(ReqTicketDO ticket, FlowAction action,
                                            String operatorId, TicketActionDTO request) {
        return checkInternal(ticket, action, operatorId, request);
    }

    /**
     * 校验主体。request 为 null 表示只做按钮渲染阶段的校验。
     *
     * @param ticket     单据
     * @param action     动作
     * @param operatorId 操作人
     * @param request    请求参数，可为 null
     * @return 阻塞项列表
     */
    private List<Blocker> checkInternal(ReqTicketDO ticket, FlowAction action,
                                        String operatorId, TicketActionDTO request) {
        List<Blocker> blockers = new ArrayList<>();
        blockers.addAll(checkReasonCode(action, request));
        blockers.addAll(checkRequiredFields(ticket, action, request));
        blockers.addAll(checkAttachments(ticket, action));
        blockers.addAll(checkDocComplete(ticket, action));
        blockers.addAll(checkCriteriaBeforeLock(ticket, action));
        blockers.addAll(checkReturnLimit(ticket, action, operatorId));
        blockers.addAll(checkInternBan(action, operatorId));
        return blockers;
    }

    /**
     * 确认需求文档前，文档必须达到契约 §8.1.7 的四条要求。
     * <p>
     * 这条放在渲染阶段也判（不区分 request 是否为空）：文档内容就在库里，
     * 是「已经能知道的事」。按钮亮着、点下去才说「功能清单还没填」，
     * 用户得来回两趟；按钮直接置灰并在原因里说明，一次就能看懂要补什么。
     *
     * @param ticket 单据
     * @param action 动作
     * @return 阻塞项，空表示文档达标
     */
    private List<Blocker> checkDocComplete(ReqTicketDO ticket, FlowAction action) {
        if (action != FlowAction.DOC_CONFIRM) {
            return List.of();
        }

        ReqDocDO doc = reqDocMapper.selectOne(Wrappers.<ReqDocDO>lambdaQuery()
                .eq(ReqDocDO::getTicketId, ticket.getId())
                .orderByDesc(ReqDocDO::getVersion)
                .last("limit 1"));
        if (doc == null) {
            Map<String, Object> 空 = Map.of("missingSections", List.of(), "messages", List.of());
            return List.of(new Blocker(Blocker.DOC_INCOMPLETE, "需求文档尚未生成，无法确认", 空));
        }

        DocValidator.Result 校验 = DocValidator.check(doc.getDocJson());
        if (校验.passed()) {
            return List.of();
        }

        Map<String, Object> details = Map.of(
                "missingSections", 校验.missingSections(),
                "messages", 校验.messages());
        return List.of(new Blocker(Blocker.DOC_INCOMPLETE,
                "需求文档还差 " + 校验.missingSections().size() + " 处未补全：" + String.join("；", 校验.messages()),
                details));
    }

    /**
     * 设计评审通过前，验收标准必须至少有一条。
     * <p>
     * 设计评审三方全过即锁定验收标准（applyParallelPass 里随升级一并置位）。
     * 锁定时一条标准都没有，会造出「空壳锁定」：之后既不能补标准（已锁死），
     * 到 UAT 又因覆盖率分母为 0 被拦（COVERAGE_INSUFFICIENT），单据两头堵死。
     * 踩过的坑：REQ-2026-000009 就锁出了 0 条标准的空壳。
     * <p>
     * 渲染阶段也判：标准在库里，是「已经能知道的事」，按钮直接置灰并说明原因，
     * 不要等评审人点下去才失败。
     *
     * @param ticket 单据
     * @param action 动作
     * @return 阻塞项，空表示验收标准达标
     */
    private List<Blocker> checkCriteriaBeforeLock(ReqTicketDO ticket, FlowAction action) {
        if (action != FlowAction.DSGN_IT_PASS
                && action != FlowAction.DSGN_PLATFORM_PASS
                && action != FlowAction.DSGN_USER_PASS) {
            return List.of();
        }
        Long count = reqAcceptanceCriteriaMapper.selectCount(
                Wrappers.<ReqAcceptanceCriteriaDO>lambdaQuery()
                        .eq(ReqAcceptanceCriteriaDO::getTicketId, ticket.getId()));
        if (count != null && count > 0) {
            return List.of();
        }
        return List.of(new Blocker(Blocker.CRITERIA_EMPTY,
                "验收标准一条都没有，不能通过设计评审：设计评审通过即锁定验收标准，请先由提出人补充至少一条",
                Map.of("criteriaCount", 0)));
    }

    /**
     * 退回类动作必须给原因码。不许只写「不行」—— 原因码是复盘与统计的输入。
     *
     * @param action  动作
     * @param request 请求
     * @return 阻塞项
     */
    private List<Blocker> checkReasonCode(FlowAction action, TicketActionDTO request) {
        // EVAL_FAIL 挂着一个 return_point，但它不是退回：单据直接关闭、只打负面标记，
        // 不产生回退边（02 第九节，退回点 ⑨ 不计数）。按「有退回点就要原因分类」去要求，
        // 「评价不符合」这条路会永远走不通 —— 它的原因由 evalComment 承担。
        if (action == FlowAction.EVAL_FAIL) {
            return List.of();
        }
        if (action.getReturnPoint() == null || request == null) {
            return List.of();
        }
        if (isNotBlank(request.getReasonCode())) {
            return List.of();
        }
        return List.of(Blocker.of(Blocker.FIELD_REQUIRED, "退回必须选择原因分类"));
    }

    /**
     * 按动作检查必填字段。
     *
     * @param ticket  单据
     * @param action  动作
     * @param request 请求
     * @return 阻塞项
     */
    private List<Blocker> checkRequiredFields(ReqTicketDO ticket, FlowAction action,
                                              TicketActionDTO request) {
        List<Blocker> blockers = new ArrayList<>();
        Map<String, Object> extra = request == null || request.getExtra() == null
                ? Map.of() : request.getExtra();
        boolean checkInput = request != null;

        if (checkInput && action == FlowAction.ASSIGN) {
            Object assigneeValue = extra.get("assigneeId");
            if (isBlank(assigneeValue)) {
                blockers.add(Blocker.of(Blocker.FIELD_REQUIRED, "派单必须指定承办人"));
            } else if (!isDevAssignee(assigneeValue.toString())) {
                // 只认开发角色。接收动作只对 MIT 开发人员开放，
                // 派给别的角色会让单据卡死在「待接收」：承办人自己没按钮，
                // 管理员过不了「是不是承办人」这关，谁都无法推进
                blockers.add(Blocker.of(Blocker.FIELD_REQUIRED,
                        "承办人必须是 MIT 开发人员，所选人员没有开发角色"));
            }
            if (toDouble(extra.get("estimatedHours")) <= 0) {
                blockers.add(Blocker.of(Blocker.FIELD_REQUIRED, "派单必须填写预估工时"));
            }
        }

        if (checkInput && action == FlowAction.MONITOR_PASS) {
            // 单据上可能已经存过结论（二期由采集任务写入），没有才取本次入参。
            // 这一项必须放在 checkInput 分支里：渲染按钮时还没填，放外面会让按钮永远是灰的。
            String note = ticket.getMonitorNote();
            if (isBlank(note) && !isBlank(extra.get("monitorNote"))) {
                note = String.valueOf(extra.get("monitorNote"));
            }
            if (note == null || note.trim().length() < MIN_MONITOR_NOTE_LENGTH) {
                blockers.add(Blocker.of(Blocker.FIELD_REQUIRED,
                        "监控结论不少于 " + MIN_MONITOR_NOTE_LENGTH + " 字，请写清看了哪些指标"));
            }
        }

        if (checkInput && action == FlowAction.EVAL_PASS) {
            double score = toDouble(extra.get("satisfactionScore"));
            if (score < 1 || score > 5) {
                blockers.add(Blocker.of(Blocker.FIELD_REQUIRED, "满意度必须是 1~5 分"));
            }
        }

        // 必须放在 checkInput 分支里：渲染按钮时还没填原因，
        // 放外面会让「评价不符合」的按钮永远是灰的 —— 这条评价路径直接走不通
        if (checkInput && action == FlowAction.EVAL_FAIL && isBlank(ticket.getEvalComment())
                && isBlank(extra.get("evalComment"))) {
            blockers.add(Blocker.of(Blocker.FIELD_REQUIRED, "评价不符合必须填写原因"));
        }

        if (checkInput && action == FlowAction.REVIEW_CONTINUE
                && isBlank(extra.get("targetStatus"))) {
            blockers.add(Blocker.of(Blocker.FIELD_REQUIRED,
                    "评审结论「继续」必须指定回到哪一步，不允许直接回原状态"));
        }

        if (checkInput && action == FlowAction.SUSPEND) {
            if (isBlank(ticket.getSuspendReason()) && isBlank(extra.get("suspendReason"))) {
                blockers.add(Blocker.of(Blocker.FIELD_REQUIRED, "挂起必须填写原因"));
            }
            if (isBlank(extra.get("expectResumeAt"))) {
                blockers.add(Blocker.of(Blocker.FIELD_REQUIRED, "挂起必须填写预计恢复时间"));
            }
        }

        if (checkInput && action == FlowAction.UAT_RAISE_BUG
                && isBlank(extra.get("bugTitle"))) {
            blockers.add(Blocker.of(Blocker.FIELD_REQUIRED, "提 BUG 必须填写标题"));
        }

        blockers.addAll(checkCoverage(ticket, action));
        blockers.addAll(checkCheckItems(ticket, action));
        blockers.addAll(checkWaiver(ticket, action, extra));
        return blockers;
    }

    /**
     * 点检通过前，重点功能必须逐项有结果。
     * <p>
     * 清单为空时直接放行：新建系统在清单维护好之前，所有单子都得能走完点检，
     * 否则上线流程死锁（`02` 4.5 第一条）。空清单这件事由流转层在日志里标
     * `check_items_empty=1`，留痕但不拦。
     *
     * @param ticket 单据
     * @param action 动作
     * @return 阻塞项
     */
    private List<Blocker> checkCheckItems(ReqTicketDO ticket, FlowAction action) {
        if (action != FlowAction.INSPECT_PASS) {
            return List.of();
        }
        CheckItemListVO 清单 = checkItemService.list(ticket.getId());
        if (清单.getEmpty() != null && 清单.getEmpty() == 1) {
            return List.of();
        }
        int 重点数 = 清单.getKeyTotal() == null ? 0 : 清单.getKeyTotal();
        int 已检数 = 清单.getKeyChecked() == null ? 0 : 清单.getKeyChecked();
        if (已检数 >= 重点数) {
            return List.of();
        }
        return List.of(new Blocker(Blocker.CHECK_ITEMS_PENDING,
                "还有 " + (重点数 - 已检数) + " 项重点功能没点检（已检 " + 已检数 + "/" + 重点数 + "）",
                Map.of("keyTotal", 重点数, "keyChecked", 已检数)));
    }

    /**
     * 用例覆盖率硬拦截：不足 100% 不许 UAT 通过（`06` 第五节）。
     * <p>
     * 这是本系统唯一一处「AI 环节的结论能卡住流程」的地方，其余 AI 结论都只是建议。
     * 理由是：验收标准写了却没人拿它逐条测，写多少条都是摆设。
     * <p>
     * 已豁免（`uat_waiver=1`）时放行，但豁免理由由 {@link #checkWaiver} 另行校验 ——
     * 两条规则分开，用户能一次看到「覆盖率不够」和「豁免没写理由」两件事，
     * 而不是补完理由再被覆盖率拦一次。
     *
     * @param ticket 单据
     * @param action 动作
     * @return 阻塞项
     */
    private List<Blocker> checkCoverage(ReqTicketDO ticket, FlowAction action) {
        if (action != FlowAction.UAT_PASS) {
            return List.of();
        }
        // 与 int 字面量比较而不是 FLAG_ENABLED：那里是 Integer，两个 Integer 用 == 比的是引用，
        // 小整数靠缓存碰巧成立，换成大值就是另一个故事
        if (ticket.getUatWaiver() != null && ticket.getUatWaiver() == 1) {
            return List.of();
        }

        List<ReqAcceptanceCriteriaDO> criteria = reqAcceptanceCriteriaMapper.selectList(
                Wrappers.<ReqAcceptanceCriteriaDO>lambdaQuery()
                        .eq(ReqAcceptanceCriteriaDO::getTicketId, ticket.getId())
                        .orderByAsc(ReqAcceptanceCriteriaDO::getSeq));
        if (criteria.isEmpty()) {
            return List.of();
        }

        // 只认最新一轮：旧轮的用例已经过时，拿它算覆盖率会掩盖「标准改了但用例没刷」
        List<Integer> 轮次表 = 最新轮次(ticket.getId());
        List<ReqTestCaseDO> 用例 = 轮次表.isEmpty()
                ? List.of() : reqTestCaseMapper.selectList(
                        Wrappers.<ReqTestCaseDO>lambdaQuery()
                                .eq(ReqTestCaseDO::getTicketId, ticket.getId())
                                .eq(ReqTestCaseDO::getRoundNo, 轮次表.get(0)));

        CoverageCalculator.Result 结果 = CoverageCalculator.calculate(criteria, 用例);
        if (结果.full()) {
            return List.of();
        }

        String 提示 = "用例覆盖率 " + Math.round(结果.rate() * 100) + "%（"
                + 结果.covered() + "/" + 结果.criteriaTotal() + "）";
        if (!结果.missingSeqs().isEmpty()) {
            提示 += "，未覆盖第 " + 结果.missingSeqs().get(0) + " 条：" + 结果.missingContents().get(0);
        } else {
            提示 += "，请先生成测试用例";
        }
        return List.of(new Blocker(Blocker.COVERAGE_INSUFFICIENT, 提示,
                Map.of("covered", 结果.covered(),
                        "total", 结果.criteriaTotal(),
                        "rate", 结果.rate(),
                        "missingSeqs", 结果.missingSeqs())));
    }

    /**
     * 本单的用例轮次，倒序。
     *
     * @param ticketId 单据 ID
     * @return 轮次列表
     */
    private List<Integer> 最新轮次(String ticketId) {
        List<ReqTestCaseDO> 全部 = reqTestCaseMapper.selectList(
                Wrappers.<ReqTestCaseDO>lambdaQuery()
                        .select(ReqTestCaseDO::getRoundNo)
                        .eq(ReqTestCaseDO::getTicketId, ticketId));
        List<Integer> 结果 = new ArrayList<>();
        for (ReqTestCaseDO 一条 : 全部) {
            if (一条.getRoundNo() != null && !结果.contains(一条.getRoundNo())) {
                结果.add(一条.getRoundNo());
            }
        }
        结果.sort((甲, 乙) -> 乙 - 甲);
        return 结果;
    }

    /**
     * 覆盖率豁免：覆盖率不足 100% 时，只有 Leader / 管理员能带理由豁免。
     * <p>
     * 用例覆盖率的实际计算依赖 req_test_case，一期先把豁免规则挡住；
     * 覆盖率数字本身在用例模块落地后接进来（TODO 见 11 号契约 §9.4）。
     *
     * @param ticket 单据
     * @param action 动作
     * @param extra  请求参数
     * @return 阻塞项
     */
    private List<Blocker> checkWaiver(ReqTicketDO ticket, FlowAction action,
                                      Map<String, Object> extra) {
        if (action != FlowAction.UAT_PASS) {
            return List.of();
        }
        Integer waiver = ticket.getUatWaiver();
        if (waiver == null || waiver != 1) {
            return List.of();
        }
        if (isBlank(ticket.getUatWaiverReason()) && isBlank(extra.get("waiverReason"))) {
            return List.of(Blocker.of(Blocker.WAIVER_FORBIDDEN, "覆盖率豁免必须写明理由"));
        }
        return List.of();
    }

    /**
     * 检查必传附件。规则按「到达状态」匹配，所以用动作的 to_status 查，不用当前状态。
     *
     * @param ticket 单据
     * @param action 动作
     * @return 阻塞项
     */
    private List<Blocker> checkAttachments(ReqTicketDO ticket, FlowAction action) {
        String targetStatus = resolveTargetStatus(action);
        if (targetStatus == null) {
            return List.of();
        }

        List<ReqAttachRuleDO> rules = reqAttachRuleMapper.selectList(
                Wrappers.<ReqAttachRuleDO>lambdaQuery()
                        .eq(ReqAttachRuleDO::getEnabled, FLAG_ENABLED)
                        .eq(ReqAttachRuleDO::getRequired, FLAG_REQUIRED)
                        .and(wrapper -> wrapper
                                .eq(ReqAttachRuleDO::getStatus, targetStatus)
                                .or()
                                .eq(ReqAttachRuleDO::getStatus, WILDCARD))
                        .and(wrapper -> wrapper
                                .eq(ReqAttachRuleDO::getTicketType, ticket.getTicketType())
                                .or()
                                .eq(ReqAttachRuleDO::getTicketType, WILDCARD)));

        List<Blocker> blockers = new ArrayList<>();
        // 字典用到才查：多数动作的附件是齐的，为了一句提示每次都查一遍字典不值当
        Map<String, String> 类型名 = null;
        for (ReqAttachRuleDO rule : rules) {
            int uploaded = countAttachments(ticket.getId(), rule.getAttachType());
            int required = rule.getMinCount() == null ? 1 : rule.getMinCount();
            if (uploaded >= required) {
                continue;
            }
            if (类型名 == null) {
                类型名 = 附件类型名();
            }
            String 显示名 = 类型名.getOrDefault(rule.getAttachType(), rule.getAttachType());
            blockers.add(new Blocker(Blocker.ATTACH_REQUIRED,
                    "本环节需上传：" + 显示名,
                    Map.of("attachType", rule.getAttachType(),
                            "attachTypeLabel", 显示名,
                            "minCount", required,
                            "uploaded", uploaded)));
        }
        return blockers;
    }

    /**
     * 附件类型码 → 中文名。
     * <p>
     * 提示语直接拼码值的话，用户看到的是「本环节需上传：REQ_DOC」，
     * 得去猜 REQ_DOC 是什么材料 —— 码值是给程序和日志看的，给人看的要有中文。
     * 查不到字典时回退码值，不因为字典缺一条就报错。
     *
     * @return 码值到中文名
     */
    private Map<String, String> 附件类型名() {
        List<SysDictDataDO> 字典 = sysDictDataMapper.selectList(
                Wrappers.<SysDictDataDO>lambdaQuery()
                        .eq(SysDictDataDO::getDictType, DICT_ATTACH_TYPE));
        Map<String, String> 结果 = new HashMap<>();
        for (SysDictDataDO 项 : 字典) {
            结果.put(项.getDictValue(), 项.getDictLabel());
        }
        return 结果;
    }

    /**
     * 附件规则里状态是「到达态」，动态目标（恢复、评审继续）算不出来，跳过附件校验。
     *
     * @param action 动作
     * @return 目标状态；动态目标返回 null
     */
    private String resolveTargetStatus(FlowAction action) {
        String toStatus = action.getToStatus();
        if (toStatus == null || toStatus.startsWith("@")) {
            return null;
        }
        return toStatus;
    }

    /**
     * 统计某类附件的有效数量（未软删除）。
     *
     * @param ticketId   单据 ID
     * @param attachType 附件类型
     * @return 数量
     */
    private int countAttachments(String ticketId, String attachType) {
        Long count = reqAttachmentMapper.selectCount(
                Wrappers.<ReqAttachmentDO>lambdaQuery()
                        .eq(ReqAttachmentDO::getTicketId, ticketId)
                        .eq(ReqAttachmentDO::getAttachType, attachType)
                        .eq(ReqAttachmentDO::getDeleted, 0));
        return count == null ? 0 : count.intValue();
    }

    /**
     * 检查退回次数上限。不同退回点维度不同，见 02 6.4。
     * v0.7 并行化后设计评审退回合并为单点，GROUP 维度不再使用。
     *
     * @param ticket     单据
     * @param action     动作
     * @param operatorId 操作人
     * @return 阻塞项
     */
    private List<Blocker> checkReturnLimit(ReqTicketDO ticket, FlowAction action,
                                           String operatorId) {
        CountScope scope = action.getCountScope();
        String point = action.getReturnPoint();
        if (scope == null || scope == CountScope.NONE || point == null) {
            return List.of();
        }

        if (scope == CountScope.PERSON) {
            return checkPersonLimit(ticket, point, operatorId);
        }
        return checkTicketLimit(ticket, point);
    }

    /**
     * 按人计数上限：③ 拒接单人 2 次，同时同单累计 3 次也要挡住。
     *
     * @param ticket     单据
     * @param point      退回点
     * @param operatorId 操作人
     * @return 阻塞项
     */
    private List<Blocker> checkPersonLimit(ReqTicketDO ticket, String point, String operatorId) {
        int byPerson = returnCounter.countByOperator(ticket.getId(), point, operatorId);
        int byTicket = returnCounter.countOnTicket(ticket.getId(), point);
        if (byPerson >= 2 || byTicket >= 3) {
            return List.of(new Blocker(Blocker.RETURN_LIMIT,
                    "该退回点已退 " + byTicket + " 次，请与 Leader 沟通后再处理",
                    Map.of("used", byTicket, "max", 3)));
        }
        return List.of();
    }

    /**
     * 按单计数上限：多数退回点 2 次。
     *
     * @param ticket 单据
     * @param point  退回点
     * @return 阻塞项
     */
    private List<Blocker> checkTicketLimit(ReqTicketDO ticket, String point) {
        int used = returnCounter.countOnTicket(ticket.getId(), point);
        if (used >= 2) {
            return List.of(new Blocker(Blocker.RETURN_LIMIT,
                    "该退回点已退 " + used + " 次，再退一次应转挂起评审",
                    Map.of("used", used, "max", 2)));
        }
        return List.of();
    }

    /**
     * 实习生四禁令：上线审批、生产部署、驳回、关闭。
     *
     * @param action     动作
     * @param operatorId 操作人
     * @return 阻塞项
     */
    private List<Blocker> checkInternBan(FlowAction action, String operatorId) {
        if (!INTERN_FORBIDDEN.contains(action)) {
            return List.of();
        }
        SysUserDO user = sysUserMapper.selectById(operatorId);
        if (user == null || !EMPLOYMENT_INTERN.equals(user.getEmploymentType())) {
            return List.of();
        }
        return List.of(Blocker.of(Blocker.BAN_INTERN, "实习期内不可执行该操作"));
    }

    /**
     * 判断候选人是否持有开发角色，能不能承办这张单。
     * <p>
     * 查不到角色时按「不合格」处理：ID 不存在、账号刚被清掉、角色被撤，
     * 都应该在派单这一步拦住让人重选，而不是放行后卡在待接收。
     *
     * @param userId 候选人用户 ID
     * @return true 表示可以承办
     */
    private boolean isDevAssignee(String userId) {
        if (userId == null || userId.isBlank()) {
            return false;
        }
        return userAccessService.listRoleCodes(userId).contains(FlowRoleSets.MIT_DEV);
    }

    private boolean isBlank(Object value) {
        if (value == null) {
            return true;
        }
        return value.toString().trim().isEmpty();
    }

    private boolean isNotBlank(String text) {
        return text != null && !text.trim().isEmpty();
    }

    private double toDouble(Object value) {
        if (value == null) {
            return 0;
        }
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return Double.parseDouble(value.toString());
        } catch (NumberFormatException exception) {
            return 0;
        }
    }
}
