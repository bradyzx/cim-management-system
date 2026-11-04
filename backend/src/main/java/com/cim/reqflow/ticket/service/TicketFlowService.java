package com.cim.reqflow.ticket.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.cim.reqflow.common.BizCodeException;
import com.cim.reqflow.common.constant.CommonConstants;
import com.cim.reqflow.common.util.IdUtils;
import com.cim.reqflow.system.entity.ReqSlaConfigDO;
import com.cim.reqflow.system.entity.SysDictDataDO;
import com.cim.reqflow.system.mapper.ReqSlaConfigMapper;
import com.cim.reqflow.system.mapper.SysDictDataMapper;
import com.cim.reqflow.system.service.UserAccessService;
import com.cim.reqflow.ticket.dto.TicketActionDTO;
import com.cim.reqflow.ticket.entity.ReqFlowLogDO;
import com.cim.reqflow.ticket.entity.ReqTicketDO;
import com.cim.reqflow.ticket.flow.Blocker;
import com.cim.reqflow.ticket.flow.ConfirmerResolver;
import com.cim.reqflow.ticket.flow.CountScope;
import com.cim.reqflow.ticket.flow.FlowAction;
import com.cim.reqflow.ticket.flow.FlowRoleSets;
import com.cim.reqflow.ticket.flow.FlowRegistry;
import com.cim.reqflow.ticket.flow.PreconditionChecker;
import com.cim.reqflow.ticket.flow.ReturnCounter;
import com.cim.reqflow.ticket.flow.TicketStatus;
import com.cim.reqflow.ticket.mapper.ReqFlowLogMapper;
import com.cim.reqflow.ticket.mapper.ReqTicketMapper;
import com.cim.reqflow.ticket.sla.SlaCalculator;
import com.cim.reqflow.ticket.vo.ActionResultVO;
import com.cim.reqflow.ticket.vo.ActionVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 需求流转引擎：把「谁在什么状态点哪个按钮」翻译成状态变更 + 时间线 + SLA。
 * <p>
 * 唯一入口。任何绕过本类直接 UPDATE req_ticket.status 的写法都不允许 ——
 * 一旦绕过，req_flow_log 时间线必然和状态对不上，报表与追溯全部失真。
 * <p>
 * 职责边界：本类只做编排，判定逻辑在五个协作类里
 * （FlowRegistry 查动作、ConfirmerResolver 算确认人、PreconditionChecker 校验、
 * ReturnCounter 计次、SlaCalculator 算到期）。判定不进 Service，否则测试写不出来。
 *
 * @author Haijie Xiao
 * @date 2026-11-04
 */
@Service
public class TicketFlowService {

    /** 业务码：单据不存在或不可见 */
    private static final String BIZ_NOT_FOUND = "NOT_FOUND";
    /** 业务码：动作码不存在或该状态下不可用 */
    private static final String BIZ_ACTION_INVALID = "ACTION_INVALID";
    /** 业务码：状态已被他人改变 */
    private static final String BIZ_STATE_CONFLICT = "STATE_CONFLICT";
    /** 业务码：无该动作权限 */
    private static final String BIZ_FORBIDDEN = "FORBIDDEN";

    /** 状态字典类型 */
    private static final String DICT_TICKET_STATUS = "ticket_status";

    /** 字典启用状态 */
    private static final String DICT_ENABLED = "ACTIVE";

    /** 附件规则与 SLA 配置的通配符 */
    private static final String WILDCARD = "*";

    /** 单据表 */
    private final ReqTicketMapper reqTicketMapper;

    /** 时间线表 */
    private final ReqFlowLogMapper reqFlowLogMapper;

    /** SLA 配置 */
    private final ReqSlaConfigMapper reqSlaConfigMapper;

    /** 字典，用于把状态码翻成中文 */
    private final SysDictDataMapper sysDictDataMapper;

    /** 用户角色与按钮权限 */
    private final UserAccessService userAccessService;

    /** 三方确认人解析 */
    private final ConfirmerResolver confirmerResolver;

    /** 前置校验 */
    private final PreconditionChecker preconditionChecker;

    /** 退回计次 */
    private final ReturnCounter returnCounter;

    /** SLA 到期计算 */
    private final SlaCalculator slaCalculator;

    public TicketFlowService(ReqTicketMapper reqTicketMapper,
                             ReqFlowLogMapper reqFlowLogMapper,
                             ReqSlaConfigMapper reqSlaConfigMapper,
                             SysDictDataMapper sysDictDataMapper,
                             UserAccessService userAccessService,
                             ConfirmerResolver confirmerResolver,
                             PreconditionChecker preconditionChecker,
                             ReturnCounter returnCounter,
                             SlaCalculator slaCalculator) {
        this.reqTicketMapper = reqTicketMapper;
        this.reqFlowLogMapper = reqFlowLogMapper;
        this.reqSlaConfigMapper = reqSlaConfigMapper;
        this.sysDictDataMapper = sysDictDataMapper;
        this.userAccessService = userAccessService;
        this.confirmerResolver = confirmerResolver;
        this.preconditionChecker = preconditionChecker;
        this.returnCounter = returnCounter;
        this.slaCalculator = slaCalculator;
    }

    /**
     * 列出当前用户在某张单上可执行的动作。前端按钮渲染的唯一来源。
     * <p>
     * 三轮过滤：角色白名单不过 → 不返回；按钮权限没授 → 不返回；
     * 归属不符或前置校验不过 → 返回但 enabled=false，并带上全部 blockers。
     *
     * @param ticketId 单据 ID
     * @param userId   当前用户
     * @return 动作列表
     */
    public List<ActionVO> listActions(String ticketId, String userId) {
        ReqTicketDO ticket = requireTicket(ticketId);
        List<String> roleCodes = userAccessService.listRoleCodes(userId);
        List<String> authCodes = userAccessService.listAuthCodes(userId);
        ConfirmerResolver.ConfirmerSet confirmers =
                confirmerResolver.resolve(ticket.getReporterId());

        List<ActionVO> actions = new ArrayList<>();
        for (FlowAction action : FlowRegistry.availableFrom(ticket.getStatus())) {
            if (!roleCodes.contains(FlowRoleSets.ADMIN)
                    && !hasAnyRole(roleCodes, action.getRoles())) {
                continue;
            }
            if (action.getAuthCode() != null && !authCodes.contains(action.getAuthCode())) {
                continue;
            }

            ActionVO vo = new ActionVO();
            vo.setAction(action.getCode());
            vo.setLabel(action.getLabel());
            vo.setAuthCode(action.getAuthCode());
            vo.setReturnPoint(action.getReturnPoint());

            List<Blocker> blockers = new ArrayList<>();
            if (!canOperate(ticket, action, userId, roleCodes, confirmers)) {
                blockers.add(Blocker.of(BIZ_FORBIDDEN, "该动作不属于你处理"));
            } else {
                blockers.addAll(preconditionChecker.checkPreconditions(
                        ticket, action, userId));
            }
            vo.setBlockers(blockers);
            vo.setEnabled(blockers.isEmpty());
            actions.add(vo);
        }
        return actions;
    }

    /**
     * 执行一个流转动作。
     * <p>
     * 并发保护用「条件更新」而不是先查后改：两个人同时点确认时，两条请求都会读到
     * 同一个旧状态，先查后改会让两条都通过。UPDATE ... WHERE status = 旧状态
     * 只有一个能成功，另一个影响行数为 0，转成 STATE_CONFLICT 让前端刷新。
     *
     * @param ticketId 单据 ID
     * @param request  动作请求
     * @param userId   操作人
     * @return 执行结果
     */
    @Transactional(rollbackFor = Exception.class)
    public ActionResultVO execute(String ticketId, TicketActionDTO request, String userId) {
        ReqTicketDO ticket = requireTicket(ticketId);
        FlowAction action = requireAction(request.getAction());
        checkSystemOnly(action);
        checkStatus(ticket, action, request.getExpectStatus());

        List<String> roleCodes = userAccessService.listRoleCodes(userId);
        checkRole(action, roleCodes);
        ConfirmerResolver.ConfirmerSet confirmers =
                confirmerResolver.resolve(ticket.getReporterId());
        checkOwner(ticket, action, userId, roleCodes, confirmers);

        List<Blocker> blockers = preconditionChecker.checkBeforeExecute(
                ticket, action, userId, request);
        if (!blockers.isEmpty()) {
            throw new BizCodeException(blockers.get(0).bizCode(),
                    blockers.get(0).message(), blockers);
        }

        String fromStatus = ticket.getStatus();
        String toStatus = resolveToStatus(action, ticket, request);
        String now = LocalDateTime.now().format(CommonConstants.DATE_TIME_FORMATTER);

        applyTransition(ticket, action, fromStatus, toStatus, request, userId, now);
        updateWithStatusGuard(ticket, fromStatus);

        int returnSeq = writeFlowLog(ticket, action, fromStatus, toStatus, request, userId, now);
        String slaDueAt = refreshSla(ticket, toStatus, now);

        ActionResultVO result = new ActionResultVO();
        result.setTicketId(ticketId);
        result.setAction(action.getCode());
        result.setFromStatus(fromStatus);
        result.setToStatus(toStatus);
        result.setStatusLabel(statusLabel(toStatus));
        result.setEscalated(Boolean.FALSE);
        result.setReturnSeq(returnSeq > 0 ? returnSeq : null);
        result.setSlaDueAt(slaDueAt);
        return result;
    }

    /**
     * 查单据，不存在时按「找不到」处理。不区分「不存在」与「无权限」，
     * 避免通过错误信息探测单据是否存在。
     *
     * @param ticketId 单据 ID
     * @return 单据
     */
    private ReqTicketDO requireTicket(String ticketId) {
        ReqTicketDO ticket = reqTicketMapper.selectById(ticketId);
        if (ticket == null) {
            throw new BizCodeException(BIZ_NOT_FOUND, "单据不存在或已被删除", null);
        }
        return ticket;
    }

    /**
     * 解析动作码。
     *
     * @param actionCode 动作码
     * @return 动作
     */
    private FlowAction requireAction(String actionCode) {
        if (actionCode == null || actionCode.trim().isEmpty()) {
            throw new BizCodeException(BIZ_ACTION_INVALID, "缺少动作码", null);
        }
        FlowAction action = FlowRegistry.byCode(actionCode);
        if (action == null) {
            throw new BizCodeException(BIZ_ACTION_INVALID, "未知的动作码：" + actionCode, null);
        }
        return action;
    }

    /**
     * 系统动作不允许人工触发。AI 取任务、定时器转评价这些没有「人去点」的语义。
     *
     * @param action 动作
     */
    private void checkSystemOnly(FlowAction action) {
        if (action.isSystemOnly()) {
            throw new BizCodeException(BIZ_FORBIDDEN, "该动作只能由系统触发", null);
        }
    }

    /**
     * 校验前置状态：expectStatus 与当前状态、动作是否允许从该状态出发。
     *
     * @param ticket       单据
     * @param action       动作
     * @param expectStatus 前端认为的当前状态
     */
    private void checkStatus(ReqTicketDO ticket, FlowAction action, String expectStatus) {
        String currentStatus = ticket.getStatus();
        if (expectStatus != null && !expectStatus.equals(currentStatus)) {
            throw new BizCodeException(BIZ_STATE_CONFLICT,
                    "单据已被他人处理，当前状态：" + statusLabel(currentStatus),
                    Map.of("currentStatus", currentStatus));
        }
        if (!action.matchesFrom(currentStatus)) {
            throw new BizCodeException(BIZ_ACTION_INVALID,
                    "当前状态「" + statusLabel(currentStatus) + "」下不能执行该动作",
                    Map.of("currentStatus", currentStatus, "action", action.getCode()));
        }
    }

    /**
     * 校验角色白名单。
     *
     * @param action    动作
     * @param roleCodes 当前用户角色
     */
    private void checkRole(FlowAction action, List<String> roleCodes) {
        if (roleCodes.contains(FlowRoleSets.ADMIN)) {
            return;
        }
        if (!hasAnyRole(roleCodes, action.getRoles())) {
            throw new BizCodeException(BIZ_FORBIDDEN,
                    "当前角色不能执行「" + action.getLabel() + "」", null);
        }
    }

    /**
     * 校验归属。管理员在 canOperate 内直接放行。
     *
     * @param ticket     单据
     * @param action     动作
     * @param userId     当前用户
     * @param roleCodes  当前用户角色
     * @param confirmers 三方确认人
     */
    private void checkOwner(ReqTicketDO ticket, FlowAction action, String userId,
                            List<String> roleCodes,
                            ConfirmerResolver.ConfirmerSet confirmers) {
        if (!canOperate(ticket, action, userId, roleCodes, confirmers)) {
            throw new BizCodeException(BIZ_FORBIDDEN,
                    "该动作不属于你处理，请确认单据当前停留在哪个环节", null);
        }
    }

    /**
     * 归属判定，五种规则见 OwnerRule。
     *
     * @param ticket     单据
     * @param action     动作
     * @param userId     当前用户
     * @param roleCodes  当前用户角色
     * @param confirmers 三方确认人
     * @return true 表示有归属
     */
    private boolean canOperate(ReqTicketDO ticket, FlowAction action, String userId,
                               List<String> roleCodes,
                               ConfirmerResolver.ConfirmerSet confirmers) {
        // 管理员跳过归属判定，否则异常时没人能救单。
        // 这里和 renderActions 用的是同一个方法，必须在同一处放行 ——
        // 之前只在 execute 里放行，导致管理员看到满屏灰色按钮却能直接调接口成功。
        if (roleCodes.contains(FlowRoleSets.ADMIN)) {
            return true;
        }
        switch (action.getOwnerRule()) {
            case NONE:
                return true;
            case REPORTER:
                return userId.equals(ticket.getReporterId())
                        || userId.equals(ticket.getOwnerId());
            case ASSIGNEE:
                return userId.equals(ticket.getAssigneeId());
            case CONFIRMER:
                return isConfirmer(ticket, userId, confirmers);
            case LEADER:
                return roleCodes.contains(FlowRoleSets.MIT_LEADER);
            default:
                return false;
        }
    }

    /**
     * 当前用户是否是该状态对应的确认人。
     * 状态决定查哪一类：MIT 环节查 MIT 确认人，平台评审查平台负责人，上级确认查部门负责人。
     *
     * @param ticket     单据
     * @param userId     当前用户
     * @param confirmers 三方确认人
     * @return true 表示是确认人
     */
    private boolean isConfirmer(ReqTicketDO ticket, String userId,
                                ConfirmerResolver.ConfirmerSet confirmers) {
        String status = ticket.getStatus();
        if (TicketStatus.CONF_MIT.equals(status)) {
            return userId.equals(confirmers.mitUserId());
        }
        if (TicketStatus.CONF_PLATFORM.equals(status)
                || TicketStatus.DSGN_REVIEW_PLATFORM.equals(status)) {
            return userId.equals(confirmers.platformUserId());
        }
        if (TicketStatus.CONF_SUPERIOR.equals(status)) {
            return userId.equals(confirmers.superiorUserId());
        }
        return false;
    }

    /**
     * 计算目标状态。两个动态目标走特殊逻辑，其余直接用枚举里的定义。
     *
     * @param action  动作
     * @param ticket  单据
     * @param request 请求
     * @return 目标状态
     */
    private String resolveToStatus(FlowAction action, ReqTicketDO ticket,
                                   TicketActionDTO request) {
        if ("@suspended_from".equals(action.getToStatus())) {
            if (ticket.getSuspendedFrom() == null) {
                throw new BizCodeException(BIZ_ACTION_INVALID,
                        "单据缺少挂起前状态，无法恢复，请联系管理员", null);
            }
            return ticket.getSuspendedFrom();
        }
        if ("@target_status".equals(action.getToStatus())) {
            Object target = request.getExtra() == null ? null : request.getExtra().get("targetStatus");
            if (target == null || target.toString().trim().isEmpty()) {
                throw new BizCodeException(BIZ_ACTION_INVALID,
                        "评审结论「继续」必须指定回到哪一步", null);
            }
            return target.toString();
        }
        return action.getToStatus();
    }

    /**
     * 更新单据基础字段与动作副作用。
     *
     * @param ticket     单据
     * @param action     动作
     * @param fromStatus 动作前状态
     * @param toStatus   动作后状态
     * @param request    请求
     * @param userId     操作人
     * @param now        当前时间
     */
    private void applyTransition(ReqTicketDO ticket, FlowAction action, String fromStatus,
                                 String toStatus, TicketActionDTO request, String userId,
                                 String now) {
        Map<String, Object> extra = request.getExtra() == null ? Map.of() : request.getExtra();

        // 挂起前状态必须在改 status 之前保存，否则 next time 恢复时找不到原状态
        if (action == FlowAction.SUSPEND) {
            ticket.setSuspendedFrom(fromStatus);
            ticket.setSuspendReason(text(extra.get("suspendReason")));
            ticket.setExpectResumeAt(text(extra.get("expectResumeAt")));
        }
        if (action == FlowAction.RESUME) {
            ticket.setSuspendedFrom(null);
        }

        ticket.setStatus(toStatus);
        ticket.setUpdatedAt(now);
        ticket.setUpdatedBy(userId);

        if (action.getReturnPoint() != null) {
            int current = ticket.getReturnCount() == null ? 0 : ticket.getReturnCount();
            ticket.setReturnCount(current + 1);
        }
        applyActionFields(ticket, action, request, userId, now, extra);
    }

    /**
     * 各动作的业务字段副作用。只写与动作直接相关的字段，
     * 时间戳只在首次到达该环节时写，重复进入不覆盖。
     *
     * @param ticket 单据
     * @param action 动作
     * @param request 请求
     * @param userId 操作人
     * @param now    当前时间
     * @param extra  动作参数
     */
    private void applyActionFields(ReqTicketDO ticket, FlowAction action,
                                   TicketActionDTO request, String userId,
                                   String now, Map<String, Object> extra) {
        switch (action) {
            case ASSIGN:
                ticket.setAssigneeId(text(extra.get("assigneeId")));
                ticket.setEstimatedHours(number(extra.get("estimatedHours")));
                ticket.setAssignedAt(now);
                break;
            case SUBMIT_DESIGN:
                ticket.setDesignAt(now);
                break;
            case DSGN_USER_PASS:
                // 用户评审通过是验收标准锁定的时点，之后不允许再改
                ticket.setAcceptanceLocked(1);
                ticket.setAcceptanceLockedAt(now);
                ticket.setAcceptanceLockedBy(userId);
                break;
            case SUBMIT_SELF_TEST:
                ticket.setDevAt(now);
                break;
            case UAT_PASS:
                ticket.setUatAt(now);
                break;
            case DOC_CONFIRM:
                ticket.setDocConfirmedAt(now);
                break;
            case MONITOR_PASS:
                // 监控期没有独立单据，结论只能挂在需求单上。
                // 不写这一段的话，前置校验读了半天 monitor_note，库里永远是空 —— 按钮永远点不动。
                ticket.setMonitorNote(text(extra.get("monitorNote")));
                break;
            case RELEASE_APPROVE:
                ticket.setReleaseAt(now);
                break;
            case EVAL_PASS:
            case EVAL_FAIL:
                ticket.setEvalResult(action == FlowAction.EVAL_PASS ? "PASS" : "FAIL");
                ticket.setSatisfactionScore(number(extra.get("satisfactionScore")));
                ticket.setEvalComment(text(extra.get("evalComment")));
                ticket.setEvaluatedAt(now);
                ticket.setEvaluatedBy(userId);
                break;
            default:
                break;
        }
        applyCloseFields(ticket, action, request, now);
    }

    /**
     * 关闭类动作写关闭原因与时间。关闭原因是报表分组依据，必须落库不能只留在备注里。
     *
     * @param ticket  单据
     * @param action  动作
     * @param request 请求
     * @param now     当前时间
     */
    private void applyCloseFields(ReqTicketDO ticket, FlowAction action,
                                  TicketActionDTO request, String now) {
        if (!TicketStatus.CLOSED.equals(ticket.getStatus())) {
            return;
        }
        ticket.setClosedAt(now);
        switch (action) {
            case DUP_CLOSE:
                ticket.setCloseReason("DUP");
                break;
            case ASSIGN_REJECT:
            case REVIEW_REJECT:
                ticket.setCloseReason("REJECT");
                break;
            case EVAL_TIMEOUT:
                ticket.setCloseReason("TIMEOUT");
                break;
            case EVAL_PASS:
            case EVAL_FAIL:
                ticket.setCloseReason("DONE");
                break;
            default:
                break;
        }
    }

    /**
     * 带状态条件的更新：WHERE status = 旧状态，影响行数为 0 说明被别人改过。
     *
     * @param ticket     待更新的单据
     * @param fromStatus 更新前的状态
     */
    private void updateWithStatusGuard(ReqTicketDO ticket, String fromStatus) {
        int rows = reqTicketMapper.update(ticket,
                Wrappers.<ReqTicketDO>lambdaUpdate()
                        .eq(ReqTicketDO::getId, ticket.getId())
                        .eq(ReqTicketDO::getStatus, fromStatus));
        if (rows == 0) {
            throw new BizCodeException(BIZ_STATE_CONFLICT,
                    "单据已被他人处理，请刷新后重试", null);
        }
    }

    /**
     * 写时间线。只追加不修改，是审计与退回计次的唯一依据。
     *
     * @param ticket     单据
     * @param action     动作
     * @param fromStatus 动作前状态
     * @param toStatus   动作后状态
     * @param request    请求
     * @param userId     操作人
     * @param now        当前时间
     * @return 退回序号；非退回动作为 0
     */
    private int writeFlowLog(ReqTicketDO ticket, FlowAction action, String fromStatus,
                             String toStatus, TicketActionDTO request, String userId,
                             String now) {
        int returnSeq = 0;
        if (action.getReturnPoint() != null
                && action.getCountScope() != CountScope.NONE) {
            returnSeq = returnCounter.nextSeq(ticket.getId(), action.getReturnPoint());
        }

        ReqFlowLogDO log = new ReqFlowLogDO();
        log.setId(IdUtils.nextId());
        log.setTicketId(ticket.getId());
        log.setFromStatus(fromStatus);
        log.setToStatus(toStatus);
        log.setAction(action.getCode());
        log.setOperatorId(userId);
        log.setReasonCode(request.getReasonCode());
        log.setRemark(request.getRemark());
        log.setReturnPoint(action.getReturnPoint());
        log.setReturnSeq(returnSeq > 0 ? returnSeq : null);
        log.setCreatedAt(now);
        reqFlowLogMapper.insert(log);
        return returnSeq;
    }

    /**
     * 重算并落库 SLA 到期时间。limit_hours 为 0 的状态不计时，直接清空。
     *
     * @param ticket   单据
     * @param toStatus 目标状态
     * @param now      起算时间
     * @return 到期时间，不计时为 null
     */
    private String refreshSla(ReqTicketDO ticket, String toStatus, String now) {
        double limitHours = findLimitHours(ticket.getTicketType(), ticket.getPriority(), toStatus);
        String dueAt = slaCalculator.calculateDueAt(now, limitHours);

        ReqTicketDO patch = new ReqTicketDO();
        patch.setId(ticket.getId());
        patch.setSlaDueAt(dueAt);
        patch.setUpdatedAt(now);
        reqTicketMapper.updateById(patch);

        ticket.setSlaDueAt(dueAt);
        return dueAt;
    }

    /**
     * 查该状态的时限。先按「单据类型 + 优先级」精确匹配，再退到通配。
     *
     * @param ticketType 单据类型
     * @param priority   优先级
     * @param status     状态
     * @return 时限，无配置为 0（表示不计时）
     */
    private double findLimitHours(String ticketType, String priority, String status) {
        List<ReqSlaConfigDO> configs = reqSlaConfigMapper.selectList(
                Wrappers.<ReqSlaConfigDO>lambdaQuery()
                        .eq(ReqSlaConfigDO::getEnabled, 1)
                        .eq(ReqSlaConfigDO::getStatus, status)
                        .in(ReqSlaConfigDO::getTicketType, ticketType, WILDCARD)
                        .in(ReqSlaConfigDO::getPriority,
                                priority == null ? WILDCARD : priority, WILDCARD));
        if (configs.isEmpty()) {
            return 0;
        }
        for (ReqSlaConfigDO config : configs) {
            if (!WILDCARD.equals(config.getPriority())) {
                return config.getLimitHours() == null ? 0 : config.getLimitHours();
            }
        }
        Double limit = configs.get(0).getLimitHours();
        return limit == null ? 0 : limit;
    }

    /**
     * 状态码翻译成中文。查字典而不是在前端再维护一份映射：
     * 字典改一次，前后端同时生效；前端硬编码就要改两处。
     *
     * @param status 状态码
     * @return 中文名；查不到时原样返回状态码
     */
    private String statusLabel(String status) {
        List<SysDictDataDO> rows = sysDictDataMapper.selectList(
                Wrappers.<SysDictDataDO>lambdaQuery()
                        .eq(SysDictDataDO::getDictType, DICT_TICKET_STATUS)
                        .eq(SysDictDataDO::getDictValue, status)
                        .eq(SysDictDataDO::getStatus, DICT_ENABLED));
        if (rows.isEmpty()) {
            return status;
        }
        return rows.get(0).getDictLabel();
    }

    private boolean hasAnyRole(List<String> roleCodes, Set<String> allowed) {
        for (String roleCode : roleCodes) {
            if (allowed.contains(roleCode)) {
                return true;
            }
        }
        return false;
    }

    private String text(Object value) {
        return value == null ? null : value.toString();
    }

    private Double number(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return Double.parseDouble(value.toString());
        } catch (NumberFormatException exception) {
            return null;
        }
    }
}
