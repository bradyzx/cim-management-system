package com.cim.reqflow.ticket.flow;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.cim.reqflow.system.entity.SysUserDO;
import com.cim.reqflow.system.mapper.SysUserMapper;
import com.cim.reqflow.ticket.dto.TicketActionDTO;
import com.cim.reqflow.ticket.entity.ReqAttachRuleDO;
import com.cim.reqflow.ticket.entity.ReqAttachmentDO;
import com.cim.reqflow.ticket.entity.ReqTicketDO;
import com.cim.reqflow.ticket.mapper.ReqAttachRuleMapper;
import com.cim.reqflow.ticket.mapper.ReqAttachmentMapper;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
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

    /** 退回计次 */
    private final ReturnCounter returnCounter;

    public PreconditionChecker(ReqAttachRuleMapper reqAttachRuleMapper,
                               ReqAttachmentMapper reqAttachmentMapper,
                               SysUserMapper sysUserMapper,
                               ReturnCounter returnCounter) {
        this.reqAttachRuleMapper = reqAttachRuleMapper;
        this.reqAttachmentMapper = reqAttachmentMapper;
        this.sysUserMapper = sysUserMapper;
        this.returnCounter = returnCounter;
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
        blockers.addAll(checkReturnLimit(ticket, action, operatorId));
        blockers.addAll(checkInternBan(action, operatorId));
        return blockers;
    }

    /**
     * 退回类动作必须给原因码。不许只写「不行」—— 原因码是复盘与统计的输入。
     *
     * @param action  动作
     * @param request 请求
     * @return 阻塞项
     */
    private List<Blocker> checkReasonCode(FlowAction action, TicketActionDTO request) {
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
            if (isBlank(extra.get("assigneeId"))) {
                blockers.add(Blocker.of(Blocker.FIELD_REQUIRED, "派单必须指定承办人"));
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

        if (action == FlowAction.EVAL_FAIL && isBlank(ticket.getEvalComment())
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

        blockers.addAll(checkWaiver(ticket, action, extra));
        return blockers;
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
        for (ReqAttachRuleDO rule : rules) {
            int uploaded = countAttachments(ticket.getId(), rule.getAttachType());
            int required = rule.getMinCount() == null ? 1 : rule.getMinCount();
            if (uploaded >= required) {
                continue;
            }
            blockers.add(new Blocker(Blocker.ATTACH_REQUIRED,
                    "本环节需上传：" + rule.getAttachType(),
                    Map.of("attachType", rule.getAttachType(),
                            "minCount", required,
                            "uploaded", uploaded)));
        }
        return blockers;
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
