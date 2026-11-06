package com.cim.reqflow.ticket.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.cim.reqflow.common.BizCodeException;
import com.cim.reqflow.common.BizException;
import com.cim.reqflow.common.constant.CommonConstants;
import com.cim.reqflow.common.util.IdUtils;
import com.cim.reqflow.system.entity.ReqSlaConfigDO;
import com.cim.reqflow.system.entity.SysDictDataDO;
import com.cim.reqflow.system.mapper.ReqSlaConfigMapper;
import com.cim.reqflow.system.mapper.SysDictDataMapper;
import com.cim.reqflow.system.service.UserAccessService;
import com.cim.reqflow.ticket.dto.TicketActionDTO;
import com.cim.reqflow.ticket.entity.ReqCheckItemDO;
import com.cim.reqflow.ticket.entity.ReqDocDO;
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
import com.cim.reqflow.ticket.mapper.ReqCheckItemMapper;
import com.cim.reqflow.ticket.mapper.ReqDocMapper;
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

    /** 需求文档表，确认文档时回填确认人与时间 */
    private final ReqDocMapper reqDocMapper;

    /** 点检清单，点检通过时判断是否需要打「清单为空」标记 */
    private final ReqCheckItemMapper reqCheckItemMapper;

    public TicketFlowService(ReqTicketMapper reqTicketMapper,
                             ReqFlowLogMapper reqFlowLogMapper,
                             ReqSlaConfigMapper reqSlaConfigMapper,
                             SysDictDataMapper sysDictDataMapper,
                             UserAccessService userAccessService,
                             ConfirmerResolver confirmerResolver,
                             PreconditionChecker preconditionChecker,
                             ReturnCounter returnCounter,
                             SlaCalculator slaCalculator,
                             ReqDocMapper reqDocMapper,
                             ReqCheckItemMapper reqCheckItemMapper) {
        this.reqTicketMapper = reqTicketMapper;
        this.reqFlowLogMapper = reqFlowLogMapper;
        this.reqSlaConfigMapper = reqSlaConfigMapper;
        this.sysDictDataMapper = sysDictDataMapper;
        this.userAccessService = userAccessService;
        this.confirmerResolver = confirmerResolver;
        this.preconditionChecker = preconditionChecker;
        this.returnCounter = returnCounter;
        this.slaCalculator = slaCalculator;
        this.reqDocMapper = reqDocMapper;
        this.reqCheckItemMapper = reqCheckItemMapper;
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
        return listActions(ticket, userId,
                userAccessService.listRoleCodes(userId),
                userAccessService.listAuthCodes(userId));
    }

    /**
     * 已持有单据与权限缓存时的动作判定。
     * <p>
     * 待办要一次判几十张单，角色与按钮权限每张单各查一次的话，光这两项就是两倍查询量。
     * 由调用方查一次传进来，判定逻辑仍是同一份 —— 两处各写一套的话，
     * 「待办里有单、点进去没按钮」这类问题迟早出现。
     *
     * @param ticket    单据
     * @param userId    当前用户
     * @param roleCodes 用户角色码，调用方一次查好
     * @param authCodes 用户按钮权限码，调用方一次查好
     * @return 动作列表
     */
    public List<ActionVO> listActions(ReqTicketDO ticket, String userId,
                                      List<String> roleCodes, List<String> authCodes) {
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
            // 并行确认后，已通过一方的通过/驳回按钮直接隐藏：意见已落定，
            // 挂个灰按钮反而让人以为还能改
            if (isSettledParty(action, ticket)) {
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
     * 系统动作执行入口：定时器用这条路径推进状态（目前只有「满 7 天转评价」）。
     * <p>
     * 与 {@link #execute} 的唯一区别是跳过「该动作只能由系统触发」的反向校验，
     * 以及角色与归属判定 —— 系统没有角色。状态迁移、时间线、SLA 全部复用同一套代码：
     * 如果为定时器另写一份 UPDATE，日志形状会和人工操作不一样，报表统计时才发现对不上。
     * <p>
     * 不做幂等键：条件更新 {@code WHERE status = 旧状态} 本身就是幂等的，
     * 同一条单被扫两次，第二次影响行数为 0 会抛 STATE_CONFLICT，由调用方吞掉。
     *
     * @param ticketId 单据 ID
     * @param action   系统动作
     * @param remark   写进时间线的说明
     * @return 执行结果
     */
    @Transactional(rollbackFor = Exception.class)
    public ActionResultVO executeSystem(String ticketId, FlowAction action, String remark) {
        ReqTicketDO ticket = requireTicket(ticketId);
        if (!action.isSystemOnly()) {
            throw new BizException("动作 " + action.getCode() + " 不是系统动作，不能由定时器触发");
        }
        checkStatus(ticket, action, null);

        TicketActionDTO request = new TicketActionDTO();
        request.setRemark(remark);

        String fromStatus = ticket.getStatus();
        String toStatus = action.getToStatus();
        String now = LocalDateTime.now().format(CommonConstants.DATE_TIME_FORMATTER);

        applyTransition(ticket, action, fromStatus, toStatus, request, null, now);
        updateWithStatusGuard(ticket, fromStatus);
        writeFlowLog(ticket, action, fromStatus, toStatus, request, null, now);
        String slaDueAt = refreshSla(ticket, toStatus, now);

        ActionResultVO result = new ActionResultVO();
        result.setTicketId(ticketId);
        result.setAction(action.getCode());
        result.setFromStatus(fromStatus);
        result.setToStatus(toStatus);
        result.setStatusLabel(statusLabel(toStatus));
        result.setEscalated(Boolean.FALSE);
        result.setSlaDueAt(slaDueAt);
        return result;
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

        // 并行确认的 PASS 动作走专用通道：置位自己那票 + 原子判定三方全过，
        // 不能走通用「读实体 → 整实体覆盖」的路径——两个人同时点确认时，
        // 后提交者的实体里是旧标志，会把先提交者刚写的标志覆盖回 0。
        String effectiveTo;
        if (isParallelPass(action)) {
            effectiveTo = applyParallelPass(ticket, action, fromStatus, userId, now);
        } else {
            applyTransition(ticket, action, fromStatus, toStatus, request, userId, now);
            updateWithStatusGuard(ticket, fromStatus);
            effectiveTo = toStatus;
        }

        int returnSeq = writeFlowLog(ticket, action, fromStatus, effectiveTo, request, userId, now);
        String slaDueAt = refreshSla(ticket, effectiveTo, now);

        ActionResultVO result = new ActionResultVO();
        result.setTicketId(ticketId);
        result.setAction(action.getCode());
        result.setFromStatus(fromStatus);
        result.setToStatus(effectiveTo);
        result.setStatusLabel(statusLabel(effectiveTo));
        result.setHint(buildHint(action, effectiveTo, ticket));
        result.setEscalated(Boolean.FALSE);
        result.setReturnSeq(returnSeq > 0 ? returnSeq : null);
        result.setSlaDueAt(slaDueAt);
        return result;
    }

    /**
     * 是否并行置位类动作：三方确认三个 PASS 与设计评审三个 PASS。
     * 这六个动作不改状态（或条件升级），只置「我方已通过」标志。
     *
     * @param action 动作
     * @return true 表示走并行置位通道
     */
    private boolean isParallelPass(FlowAction action) {
        return action == FlowAction.CONF_MIT_PASS || action == FlowAction.CONF_PLATFORM_PASS
                || action == FlowAction.CONF_SUPERIOR_PASS
                || action == FlowAction.DSGN_IT_PASS || action == FlowAction.DSGN_PLATFORM_PASS
                || action == FlowAction.DSGN_USER_PASS;
    }

    /**
     * 执行并行确认的一方置位，并在三方全部通过时原子升级状态。
     * <p>
     * 两条条件 UPDATE 的分工：
     * 第一条「置位」带 own_flag = 0 条件，防止同一方重复确认（影响行数 0 → 状态冲突）；
     * 第二条「升级」带三方标志全部 = 1 条件，判定与升级在同一条 UPDATE 里完成，
     * 两个确认人并发提交时，谁的后写谁触发升级，不存在「都置了位却没人升级」的窗口。
     *
     * @param ticket     单据（内存对象，方法内同步状态供后续使用）
     * @param action     动作
     * @param fromStatus 当前状态（CONFIRMING 或 DSGN_REVIEWING）
     * @param userId     操作人
     * @param now        当前时间
     * @return 实际到达状态：未全过为原状态，全过为目标状态
     */
    private String applyParallelPass(ReqTicketDO ticket, FlowAction action, String fromStatus,
                                     String userId, String now) {
        boolean confStage = TicketStatus.CONFIRMING.equals(fromStatus);
        String target = confStage ? TicketStatus.REQ_REVIEW : TicketStatus.DEVELOPING;
        SFunction<ReqTicketDO, ?> ownFlag = flagOf(action);

        // 第一条：置位自己那票。own_flag = 0 是防重条件，重复点击在这里被拦下
        int flagged = reqTicketMapper.update(null,
                Wrappers.<ReqTicketDO>lambdaUpdate()
                        .set(ownFlag, 1)
                        .set(ReqTicketDO::getUpdatedAt, now)
                        .set(ReqTicketDO::getUpdatedBy, userId)
                        .eq(ReqTicketDO::getId, ticket.getId())
                        .eq(ReqTicketDO::getStatus, fromStatus)
                        .eq(ownFlag, 0));
        if (flagged == 0) {
            throw new BizCodeException(BIZ_STATE_CONFLICT,
                    "该环节已确认或单据已被他人处理，请刷新后查看", null);
        }
        // 内存对象同步置位：buildHint 读的是这个对象，不同步会把刚确认的一方
        // 又算进「待确认」名单里（提示词就错了）
        setFlagOnTicket(ticket, action);

        // 第二条：三方标志全为 1 才升级。判定与升级原子完成
        LambdaUpdateWrapper<ReqTicketDO> upgrade = Wrappers.<ReqTicketDO>lambdaUpdate()
                .set(ReqTicketDO::getStatus, target)
                .set(ReqTicketDO::getUpdatedAt, now)
                .set(ReqTicketDO::getUpdatedBy, userId)
                .eq(ReqTicketDO::getId, ticket.getId())
                .eq(ReqTicketDO::getStatus, fromStatus);
        if (confStage) {
            upgrade.eq(ReqTicketDO::getMitPassed, 1)
                    .eq(ReqTicketDO::getPlatformPassed, 1)
                    .eq(ReqTicketDO::getSuperiorPassed, 1);
        } else {
            upgrade.eq(ReqTicketDO::getDsgnItPassed, 1)
                    .eq(ReqTicketDO::getDsgnPlatformPassed, 1)
                    .eq(ReqTicketDO::getDsgnUserPassed, 1)
                    // 设计评审全过 = 设计确认，验收标准在此刻锁定
                    .set(ReqTicketDO::getAcceptanceLocked, 1)
                    .set(ReqTicketDO::getAcceptanceLockedAt, now)
                    .set(ReqTicketDO::getAcceptanceLockedBy, userId);
        }
        int upgraded = reqTicketMapper.update(null, upgrade);

        ticket.setStatus(upgraded > 0 ? target : fromStatus);
        return ticket.getStatus();
    }

    /**
     * 在内存单据对象上同步置位本次动作对应的通过标志。
     *
     * @param ticket 单据
     * @param action 并行置位类动作
     */
    private void setFlagOnTicket(ReqTicketDO ticket, FlowAction action) {
        switch (action) {
            case CONF_MIT_PASS:
                ticket.setMitPassed(1);
                break;
            case CONF_PLATFORM_PASS:
                ticket.setPlatformPassed(1);
                break;
            case CONF_SUPERIOR_PASS:
                ticket.setSuperiorPassed(1);
                break;
            case DSGN_IT_PASS:
                ticket.setDsgnItPassed(1);
                break;
            case DSGN_PLATFORM_PASS:
                ticket.setDsgnPlatformPassed(1);
                break;
            case DSGN_USER_PASS:
                ticket.setDsgnUserPassed(1);
                break;
            default:
                break;
        }
    }

    /**
     * 动作对应的一方通过标志字段。
     *
     * @param action 并行置位类动作
     * @return 标志列
     */
    private SFunction<ReqTicketDO, ?> flagOf(FlowAction action) {
        switch (action) {
            case CONF_MIT_PASS:
                return ReqTicketDO::getMitPassed;
            case CONF_PLATFORM_PASS:
                return ReqTicketDO::getPlatformPassed;
            case CONF_SUPERIOR_PASS:
                return ReqTicketDO::getSuperiorPassed;
            case DSGN_IT_PASS:
                return ReqTicketDO::getDsgnItPassed;
            case DSGN_PLATFORM_PASS:
                return ReqTicketDO::getDsgnPlatformPassed;
            case DSGN_USER_PASS:
                return ReqTicketDO::getDsgnUserPassed;
            default:
                throw new BizCodeException(BIZ_ACTION_INVALID,
                        "该动作不是并行置位类动作", null);
        }
    }

    /**
     * 生成动作成功提示语。以前端拼「已 + 状态标签」，环节名拼出来是病句
     * （「已平台确认」）；改为后端按动作语义生成，前端直接展示。
     *
     * @param action     动作
     * @param toStatus   实际到达状态
     * @param ticket     单据（读最新标志判断还差哪几方）
     * @return 提示语
     */
    private String buildHint(FlowAction action, String toStatus, ReqTicketDO ticket) {
        if (action == FlowAction.CONF_MIT_PASS || action == FlowAction.CONF_PLATFORM_PASS
                || action == FlowAction.CONF_SUPERIOR_PASS) {
            if (TicketStatus.CONFIRMING.equals(toStatus)) {
                return "已确认，待" + joinPendingConf(ticket) + "确认";
            }
            return "三方确认完成，已进入需求评审";
        }
        if (action == FlowAction.DSGN_IT_PASS || action == FlowAction.DSGN_PLATFORM_PASS
                || action == FlowAction.DSGN_USER_PASS) {
            if (TicketStatus.DSGN_REVIEWING.equals(toStatus)) {
                return "已评审通过，待" + joinPendingDsgn(ticket) + "评审";
            }
            return "设计评审完成，已进入开发，验收标准已锁定";
        }
        if (TicketStatus.DRAFT.equals(toStatus) && action.getReturnPoint() != null) {
            return "已驳回，需求退回草稿，可修改后重新提交";
        }
        if (action.changesStatus()) {
            return "已流转至「" + statusLabel(toStatus) + "」";
        }
        return "操作成功";
    }

    /**
     * 三方确认还没过的各方名称，如「平台、上级」。
     *
     * @param ticket 单据
     * @return 顿号分隔的待确认方
     */
    private String joinPendingConf(ReqTicketDO ticket) {
        List<String> pending = new ArrayList<>();
        if (!isPassed(ticket.getMitPassed())) {
            pending.add("MIT");
        }
        if (!isPassed(ticket.getPlatformPassed())) {
            pending.add("平台");
        }
        if (!isPassed(ticket.getSuperiorPassed())) {
            pending.add("上级");
        }
        return String.join("、", pending);
    }

    /**
     * 设计评审还没过的各方名称，如「IT、用户」。
     *
     * @param ticket 单据
     * @return 顿号分隔的待评审方
     */
    private String joinPendingDsgn(ReqTicketDO ticket) {
        List<String> pending = new ArrayList<>();
        if (!isPassed(ticket.getDsgnItPassed())) {
            pending.add("IT");
        }
        if (!isPassed(ticket.getDsgnPlatformPassed())) {
            pending.add("平台");
        }
        if (!isPassed(ticket.getDsgnUserPassed())) {
            pending.add("用户");
        }
        return String.join("、", pending);
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
                return isConfirmer(ticket, action, userId, confirmers);
            case LEADER:
                // 团队侧动作的权限边界就是角色白名单本身（LEADERS / REVIEWERS / IT_ROLES），
                // checkRole 已经验过，能走到这里的人必然持有其一。
                // 原实现只认 MIT_LEADER，评审领导（REVIEW_LEADER）与 IT 运维（IT_OPS）
                // 全被误伤——zhouba 连「评审通过」都点不动，演示时才暴露。
                return true;
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
    /**
     * 当前用户是否可以执行该确认类动作。
     * <p>
     * v0.7 并行化后不再「状态对应唯一确认人」，而是「动作对应一方 + 该方未确认」：
     * 三方确认中，MIT 动作看 MIT 确认人且 mit_passed=0，平台、上级同理；
     * 设计评审中，平台动作看平台确认人且 dsgn_platform_passed=0。
     * 已通过的一方，通过/驳回按钮都不再可用（该方的意见已经落定）。
     *
     * @param ticket     单据
     * @param action     动作
     * @param userId     当前用户
     * @param confirmers 三方确认人
     * @return true 表示该动作轮到此用户
     */
    private boolean isConfirmer(ReqTicketDO ticket, FlowAction action, String userId,
                                ConfirmerResolver.ConfirmerSet confirmers) {
        String status = ticket.getStatus();
        if (TicketStatus.CONFIRMING.equals(status)) {
            if (action == FlowAction.CONF_MIT_PASS || action == FlowAction.CONF_MIT_REJECT) {
                return !isPassed(ticket.getMitPassed())
                        && userId.equals(confirmers.mitUserId());
            }
            if (action == FlowAction.CONF_PLATFORM_PASS
                    || action == FlowAction.CONF_PLATFORM_REJECT) {
                return !isPassed(ticket.getPlatformPassed())
                        && userId.equals(confirmers.platformUserId());
            }
            if (action == FlowAction.CONF_SUPERIOR_PASS
                    || action == FlowAction.CONF_SUPERIOR_REJECT) {
                return !isPassed(ticket.getSuperiorPassed())
                        && userId.equals(confirmers.superiorUserId());
            }
            return false;
        }
        if (TicketStatus.DSGN_REVIEWING.equals(status)
                && (action == FlowAction.DSGN_PLATFORM_PASS
                || action == FlowAction.DSGN_PLATFORM_RETURN)) {
            return !isPassed(ticket.getDsgnPlatformPassed())
                    && userId.equals(confirmers.platformUserId());
        }
        return false;
    }

    /**
     * 通过标志是否已置位。数据库默认值是 0，历史行迁移前可能为 null，统一按未置位处理。
     *
     * @param flag 标志值
     * @return true 表示已置位
     */
    private boolean isPassed(Integer flag) {
        return flag != null && flag == 1;
    }

    /**
     * 该动作所属的一方是否已经确认/评审通过。通过后这一方的全部动作（含驳回）都不再展示。
     *
     * @param action 动作
     * @param ticket 单据
     * @return true 表示该方已落定，动作应隐藏
     */
    private boolean isSettledParty(FlowAction action, ReqTicketDO ticket) {
        switch (action) {
            case CONF_MIT_PASS:
            case CONF_MIT_REJECT:
                return isPassed(ticket.getMitPassed());
            case CONF_PLATFORM_PASS:
            case CONF_PLATFORM_REJECT:
                return isPassed(ticket.getPlatformPassed());
            case CONF_SUPERIOR_PASS:
            case CONF_SUPERIOR_REJECT:
                return isPassed(ticket.getSuperiorPassed());
            case DSGN_IT_PASS:
            case DSGN_IT_RETURN:
                return isPassed(ticket.getDsgnItPassed());
            case DSGN_PLATFORM_PASS:
            case DSGN_PLATFORM_RETURN:
                return isPassed(ticket.getDsgnPlatformPassed());
            case DSGN_USER_PASS:
            case DSGN_USER_RETURN:
                return isPassed(ticket.getDsgnUserPassed());
            default:
                return false;
        }
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
                // 重新提交设计 = 设计评审从头再来，三方标志清零（并行化后各方可先可后）
                ticket.setDsgnItPassed(0);
                ticket.setDsgnPlatformPassed(0);
                ticket.setDsgnUserPassed(0);
                break;
            case SUBMIT_SELF_TEST:
                ticket.setDevAt(now);
                break;
            case UAT_PASS:
                ticket.setUatAt(now);
                break;
            case DOC_CONFIRM:
                ticket.setDocConfirmedAt(now);
                // 重新进三方确认 = 三方标志清零（驳回改完重提的场景）
                ticket.setMitPassed(0);
                ticket.setPlatformPassed(0);
                ticket.setSuperiorPassed(0);
                // 版本锁定：把「谁在什么时候认下了哪一版」记到文档上。
                // 只回填 req_ticket 会丢掉版本维度，重新生成后就看不出上一版被谁确认过。
                回填文档确认(ticket.getId(), userId, now);
                break;
            case SKIP_CONF_MIT:
                ticket.setMitPassed(1);
                break;
            case SKIP_CONF_PLATFORM:
                ticket.setPlatformPassed(1);
                break;
            case SKIP_CONF_SUPERIOR:
                ticket.setSuperiorPassed(1);
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
     * 确认需求文档时，在文档上留下确认痕迹。
     * <p>
     * 只改最新版：旧版是历史，被人确认过就一直是那个样子，
     * 重新生成出新版之后还要能看出上一版当时是谁认的。
     *
     * @param ticketId 单据 ID
     * @param userId   确认人
     * @param now      确认时间
     */
    private void 回填文档确认(String ticketId, String userId, String now) {
        ReqDocDO doc = reqDocMapper.selectOne(Wrappers.<ReqDocDO>lambdaQuery()
                .eq(ReqDocDO::getTicketId, ticketId)
                .orderByDesc(ReqDocDO::getVersion)
                .last("limit 1"));
        if (doc == null) {
            return;
        }
        doc.setConfirmedAt(now);
        doc.setConfirmedBy(userId);
        doc.setUpdatedAt(now);
        reqDocMapper.updateById(doc);
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
        log.setRemark(补点检标记(ticket, action, request.getRemark()));
        log.setReturnPoint(action.getReturnPoint());
        log.setReturnSeq(returnSeq > 0 ? returnSeq : null);
        log.setCreatedAt(now);
        reqFlowLogMapper.insert(log);
        return returnSeq;
    }

    /**
     * 点检通过且清单为空时，在日志备注里打上 `check_items_empty=1`。
     * <p>
     * 空清单放行是刻意的（`02` 4.5），但「这次点检其实什么也没核对」必须留痕 ——
     * 否则事后审计看到「IT 点检通过」，会以为真的逐项看过。
     * 不单独立字段：这是备注性质的标记，报表要统计时按 remark 模糊匹配即可。
     *
     * @param ticket  单据
     * @param action  动作
     * @param 原备注   请求里带的备注
     * @return 加了标记的备注
     */
    private String 补点检标记(ReqTicketDO ticket, FlowAction action, String 原备注) {
        if (action != FlowAction.INSPECT_PASS) {
            return 原备注;
        }
        Long 清单数 = reqCheckItemMapper.selectCount(
                Wrappers.<ReqCheckItemDO>lambdaQuery()
                        .eq(ReqCheckItemDO::getTicketId, ticket.getId()));
        if (清单数 != null && 清单数 > 0) {
            return 原备注;
        }
        return 原备注 == null || 原备注.trim().isEmpty()
                ? "check_items_empty=1" : 原备注 + "；check_items_empty=1";
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
