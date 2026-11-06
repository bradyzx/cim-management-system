package com.cim.reqflow.ticket.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.cim.reqflow.common.constant.CommonConstants;
import com.cim.reqflow.system.service.UserAccessService;
import com.cim.reqflow.ticket.entity.ReqFlowLogDO;
import com.cim.reqflow.ticket.entity.ReqTicketDO;
import com.cim.reqflow.ticket.flow.Blocker;
import com.cim.reqflow.ticket.flow.TicketStatus;
import com.cim.reqflow.ticket.mapper.ReqFlowLogMapper;
import com.cim.reqflow.ticket.mapper.ReqTicketMapper;
import com.cim.reqflow.ticket.vo.ActionVO;
import com.cim.reqflow.ticket.vo.TicketListVO;
import com.cim.reqflow.ticket.vo.TicketTodoVO;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 我的待办：把「轮到我处理的单」捞出来。
 * <p>
 * <b>判定口径与详情页的 `/actions` 完全同源</b> —— 直接调
 * {@code TicketFlowService.listActions}，不另写一套。两套判定最典型的后果是
 * 「待办里有这张单，点进去一个按钮都没有」，用户会以为系统坏了。
 * <p>
 * <b>与契约 §4.3 的一处差异（有意为之）</b>：契约写「至少有一个 enabled=true 的动作」才算待办。
 * 照字面做会漏掉最需要提醒的一类单 —— 归属是我、但还差材料（没传设计文档、没填工时）。
 * 这类单的动作按钮是灰的，按契约就不会出现在待办里，于是我永远不知道有张单在等我补材料。
 * 所以这里分两组返回：`dueActions`（现在能点）与 `blockedActions`（要我补东西），
 * 两者都算待办，前端用不同标签区分。
 *
 * @author Haijie Xiao
 * @date 2026-11-05
 */
@Service
public class TicketTodoService {

    /** 业务码：动作归属不符 */
    private static final String BIZ_FORBIDDEN = "FORBIDDEN";

    /**
     * 全局旁路动作码：可选项，不构成「轮到我处理」。
     * <p>
     * 挂起对所有在途单都可点，若算作待办，Leader 的待办会把全团队的单全列出来 ——
     * 待办就没意义了。重开只对超时关闭单有效，属可选操作，同理排除。
     * 恢复（RESUME）不在此列：挂起的单需要人推一把，是真待办。
     * <p>
     * 按动作码而不是枚举比较：动作码是接口契约里传的那个值，直接比字符串，
     * 不会因为枚举里没有这个码就抛异常。
     */
    private static final Set<String> NON_TODO_ACTIONS = Set.of("SUSPEND", "REOPEN");

    /**
     * 回退与终止类动作码：可以点，但不是「轮到你做什么」的主提示。
     * <p>
     * 为什么需要这份名单：`REQ_DOC_DRAFTED` 状态下可用的动作里排在最前的是
     * 「退回重生成」，于是提示语会变成「待你退回重生成」—— 而用户真正该做的是
     * 把附件补齐后「确认需求文档」。退回是分支选择，不是待办本身。
     * <p>
     * 用显式名单而不是「名字里带 REJECT/RETURN」的匹配：`EVAL_FAIL`（评价不符合）
     * 名字像回退，实际是评价环节的正向结论，误判会让提示语失真。
     */
    private static final Set<String> 回退或终止 = Set.of(
            "DOC_REGENERATE", "DUP_CLOSE", "CLARIFY_ABANDON",
            "CONF_MIT_REJECT", "CONF_PLATFORM_REJECT", "CONF_SUPERIOR_REJECT",
            "REVIEW_REJECT", "ASSIGN_REJECT", "ASSIGN_RETURN", "REFUSE",
            "DSGN_IT_RETURN", "DSGN_PLATFORM_RETURN", "DSGN_USER_RETURN",
            "SELF_TEST_FAIL", "DEPLOY_FAIL", "UAT_FAIL", "CLARIFY_FORCE_DOC",
            "RELEASE_REJECT_TEST", "RELEASE_REJECT_DESIGN",
            "PROD_REJECT_FUNC", "PROD_REJECT_DESIGN",
            "MONITOR_RETURN", "INSPECT_FAIL", "REVIEW_CLOSE", "EVAL_TIMEOUT");

    /** 一小时多少分钟，用于格式化停留时长 */
    private static final double MINUTES_PER_HOUR = 60.0;

    /** 待办性质：现在就能办 */
    private static final String TODO_DUE = "DUE";

    /** 待办性质：需要先补材料 */
    private static final String TODO_BLOCKED = "BLOCKED";

    /** 一天多少小时 */
    private static final int HOURS_PER_DAY = 24;

    private final ReqTicketMapper reqTicketMapper;

    private final ReqFlowLogMapper reqFlowLogMapper;

    private final TicketScopeResolver ticketScopeResolver;

    private final TicketFlowService ticketFlowService;

    private final TicketQueryService ticketQueryService;

    private final UserAccessService userAccessService;

    public TicketTodoService(ReqTicketMapper reqTicketMapper,
                             ReqFlowLogMapper reqFlowLogMapper,
                             TicketScopeResolver ticketScopeResolver,
                             TicketFlowService ticketFlowService,
                             TicketQueryService ticketQueryService,
                             UserAccessService userAccessService) {
        this.reqTicketMapper = reqTicketMapper;
        this.reqFlowLogMapper = reqFlowLogMapper;
        this.ticketScopeResolver = ticketScopeResolver;
        this.ticketFlowService = ticketFlowService;
        this.ticketQueryService = ticketQueryService;
        this.userAccessService = userAccessService;
    }

    /**
     * 待办列表，按停留时长倒序 —— 等得最久的排最前，这是唯一合理的排序：
     * 待办不按创建时间排，否则老单沉底，越拖越久。
     *
     * @param ticketType 单据类型，可空表示全部
     * @param userId     当前用户
     * @return 待办列表
     */
    public List<TicketTodoVO> list(String ticketType, String userId) {
        // 角色与按钮权限查一次，几十张单共用；每张单各查一次是两倍查询量
        List<String> roleCodes = userAccessService.listRoleCodes(userId);
        List<String> authCodes = userAccessService.listAuthCodes(userId);

        List<ReqTicketDO> tickets = 可见在途单(ticketType, userId);
        if (tickets.isEmpty()) {
            return List.of();
        }

        // 单号、状态、优先级、提出人这些列与需求列表同源，翻译逻辑也共用
        List<TicketListVO> rows = ticketQueryService.toListRows(tickets);
        Map<String, String> 最后流转时间 = 最后流转时间(tickets);

        List<TicketTodoVO> result = new ArrayList<>();
        for (int i = 0; i < tickets.size(); i++) {
            ReqTicketDO ticket = tickets.get(i);
            List<ActionVO> actions = ticketFlowService.listActions(
                    ticket, userId, roleCodes, authCodes);

            List<String> dueActions = new ArrayList<>();
            List<String> dueLabels = new ArrayList<>();
            List<String> blockedActions = new ArrayList<>();
            List<String> blockedLabels = new ArrayList<>();
            List<String> blockedReasons = new ArrayList<>();

            for (ActionVO action : actions) {
                if (NON_TODO_ACTIONS.contains(action.getAction())) {
                    continue;
                }
                // 归属不是我：listActions 在归属不符时只放一条 FORBIDDEN，不带前置校验结果
                if (只有归属不符(action)) {
                    continue;
                }
                if (Boolean.TRUE.equals(action.getEnabled())) {
                    dueActions.add(action.getAction());
                    dueLabels.add(action.getLabel());
                    continue;
                }
                blockedActions.add(action.getAction());
                blockedLabels.add(action.getLabel());
                for (Blocker blocker : action.getBlockers()) {
                    if (!blockedReasons.contains(blocker.message())) {
                        blockedReasons.add(blocker.message());
                    }
                }
            }

            // 两组都空说明这张单此刻轮不到我，不进待办
            if (dueActions.isEmpty() && blockedActions.isEmpty()) {
                continue;
            }

            TicketTodoVO vo = 组装(rows.get(i), ticket,
                    最后流转时间.getOrDefault(ticket.getId(), ticket.getCreatedAt()));
            vo.setDueActions(dueActions);
            vo.setDueActionLabels(dueLabels);
            vo.setBlockedActions(blockedActions);
            vo.setBlockedActionLabels(blockedLabels);
            vo.setBlockedReasons(blockedReasons);
            vo.setTodoHint(拼提示(dueActions, dueLabels, blockedActions, blockedReasons));
            vo.setTodoType(待办性质(dueActions, blockedActions));
            result.add(vo);
        }

        // 等得久的排前面；相等时按单号，保证刷新两次顺序一致
        result.sort((甲, 乙) -> {
            int 比较 = Double.compare(
                    nvl(乙.getWaitingHours()), nvl(甲.getWaitingHours()));
            return 比较 != 0 ? 比较 : 甲.getTicketNo().compareTo(乙.getTicketNo());
        });
        return result;
    }

    /**
     * 取当前用户可见、且未关闭的单据。
     * <p>
     * 排除 CLOSED：关闭单只剩「重开」一个动作，属可选操作，不是待办。
     * 挂起（SUSPENDED）与待评审（SUSPENDED_REVIEW）保留 —— 前者要人恢复、
     * 后者要 Leader 给结论，都是真待办。
     *
     * @param ticketType 单据类型，可空
     * @param userId     当前用户
     * @return 单据列表
     */
    private List<ReqTicketDO> 可见在途单(String ticketType, String userId) {
        LambdaQueryWrapper<ReqTicketDO> wrapper = Wrappers.lambdaQuery(ReqTicketDO.class);
        wrapper.ne(ReqTicketDO::getStatus, TicketStatus.CLOSED);
        if (ticketType != null && !ticketType.trim().isEmpty()) {
            wrapper.eq(ReqTicketDO::getTicketType, ticketType);
        }
        // 数据范围规则按单据类型选，未指定类型时按通配规则算
        String scopeType = ticketType == null || ticketType.trim().isEmpty()
                ? "*" : ticketType;
        ticketScopeResolver.applyReadableScope(wrapper, userId, scopeType);
        return reqTicketMapper.selectList(wrapper);
    }

    /**
     * 动作是否只是「归属不符」。
     * <p>
     * listActions 的写法是：归属不符只塞一条 FORBIDDEN；归属对上才跑前置校验。
     * 所以「blockers 恰好一条且是 FORBIDDEN」就等于这张单不归我处理。
     *
     * @param action 动作
     * @return 归属不符返回 true
     */
    private boolean 只有归属不符(ActionVO action) {
        return action.getBlockers().size() == 1
                && BIZ_FORBIDDEN.equals(action.getBlockers().get(0).bizCode());
    }

    /**
     * 判断这张单现在能不能办。
     * <p>
     * 判据是「有没有能把单往下推的动作」，不是「有没有任何可点的动作」：
     * 需求文档待确认的单上，「退回重生成」「判定重复关闭」一直可点，
     * 但那不表示用户现在能推进它，它还卡在缺附件上。
     *
     * @param dueCodes     可执行动作码
     * @param blockedCodes 被挡动作码
     * @return DUE 能办 / BLOCKED 需先补材料
     */
    private String 待办性质(List<String> dueCodes, List<String> blockedCodes) {
        for (String code : dueCodes) {
            if (!回退或终止.contains(code)) {
                return TODO_DUE;
            }
        }
        for (String code : blockedCodes) {
            if (!回退或终止.contains(code)) {
                return TODO_BLOCKED;
            }
        }
        // 只剩回退/终止类可选：至少能办，交给用户判断
        return dueCodes.isEmpty() ? TODO_BLOCKED : TODO_DUE;
    }

    /**
     * 拼一句「待你做什么」。优先级顺序有讲究：
     * <ol>
     *   <li>现在就能点的正向动作 —— 最该先做；</li>
     *   <li>被前置条件挡住的<em>正向</em>动作 —— 提示去补什么材料；</li>
     *   <li>只有回退/终止类动作可点时，才退而报它 —— 那是分支选择，不是待办。</li>
     * </ol>
     *
     * @param dueCodes       可执行动作码
     * @param dueLabels      可执行动作名
     * @param blockedCodes   被挡动作码
     * @param blockedReasons 被挡原因
     * @return 提示语
     */
    private String 拼提示(List<String> dueCodes, List<String> dueLabels,
                          List<String> blockedCodes, List<String> blockedReasons) {
        for (int i = 0; i < dueCodes.size(); i++) {
            if (!回退或终止.contains(dueCodes.get(i))) {
                return 待你(dueLabels.get(i));
            }
        }
        for (String code : blockedCodes) {
            if (!回退或终止.contains(code)) {
                // 只取第一条：完整清单在 blockedReasons 里，前端用悬停展示。
                // 全拼进一格会把表格撑爆，用户扫一眼反而什么也读不到
                return "缺材料：" + (blockedReasons.isEmpty() ? "请补齐本环节材料" : blockedReasons.get(0));
            }
        }
        if (!dueLabels.isEmpty()) {
            return 待你(dueLabels.get(0));
        }
        return "缺材料：" + (blockedReasons.isEmpty() ? "请补齐本环节材料" : blockedReasons.get(0));
    }

    /**
     * 接上动作名。动作名以英文开头时补一个空格，
     * 否则会拼出「待你UAT 通过」这种中文紧贴英文的病句。
     *
     * @param label 动作名
     * @return 「待你 XX」
     */
    private String 待你(String label) {
        if (label == null || label.isEmpty()) {
            return "待处理";
        }
        char 首字 = label.charAt(0);
        boolean 英文或数字 = (首字 >= 'A' && 首字 <= 'Z') || (首字 >= 'a' && 首字 <= 'z')
                || (首字 >= '0' && 首字 <= '9');
        return 英文或数字 ? "待你 " + label : "待你" + label;
    }

    /**
     * 每张单最后一条流转记录的时间，即「进入当前环节的时间」。
     * <p>
     * 一次查回全部再在内存里取最大：一次待办最多几十张单、几百条日志，
     * 为它单独写一条 GROUP BY 语句不值当。数据量上来后改成 SQL 聚合。
     *
     * @param tickets 单据列表
     * @return 单据 ID 到最后流转时间
     */
    private Map<String, String> 最后流转时间(List<ReqTicketDO> tickets) {
        List<String> ids = new ArrayList<>(tickets.size());
        for (ReqTicketDO ticket : tickets) {
            ids.add(ticket.getId());
        }
        List<ReqFlowLogDO> logs = reqFlowLogMapper.selectList(
                Wrappers.<ReqFlowLogDO>lambdaQuery()
                        .select(ReqFlowLogDO::getTicketId, ReqFlowLogDO::getCreatedAt)
                        .in(ReqFlowLogDO::getTicketId, ids));

        Map<String, String> 结果 = new HashMap<>();
        for (ReqFlowLogDO 一条 : logs) {
            if (一条.getCreatedAt() == null) {
                continue;
            }
            String 旧的 = 结果.get(一条.getTicketId());
            // 时间是 "yyyy-MM-dd HH:mm:ss" 字符串，字典序等于时间序，不用解析
            if (旧的 == null || 一条.getCreatedAt().compareTo(旧的) > 0) {
                结果.put(一条.getTicketId(), 一条.getCreatedAt());
            }
        }
        return 结果;
    }

    /**
     * 把列表行拷成待办行，并算出停留时长。
     *
     * @param row     列表行
     * @param ticket  单据实体，取 SLA 相关字段
     * @param 进入时间 进入当前环节的时间
     * @return 待办行
     */
    private TicketTodoVO 组装(TicketListVO row, ReqTicketDO ticket, String 进入时间) {
        TicketTodoVO vo = new TicketTodoVO();
        vo.setId(row.getId());
        vo.setTicketNo(row.getTicketNo());
        vo.setTicketType(row.getTicketType());
        vo.setTicketTypeLabel(row.getTicketTypeLabel());
        vo.setTitle(row.getTitle());
        vo.setStatus(row.getStatus());
        vo.setStatusLabel(row.getStatusLabel());
        vo.setStatusCssClass(row.getStatusCssClass());
        vo.setPriority(row.getPriority());
        vo.setPriorityLabel(row.getPriorityLabel());
        vo.setPriorityCssClass(row.getPriorityCssClass());
        vo.setReporterName(row.getReporterName());
        vo.setAssigneeName(row.getAssigneeName());
        vo.setDeptName(row.getDeptName());
        vo.setProjectName(row.getProjectName());
        vo.setSystemCode(row.getSystemCode());
        vo.setSlaDueAt(row.getSlaDueAt());
        vo.setSlaOverdueHours(row.getSlaOverdueHours());
        vo.setReturnCount(row.getReturnCount());
        vo.setCreatedAt(row.getCreatedAt());

        double 小时 = 停留小时(进入时间);
        vo.setWaitingHours(小时);
        vo.setWaitingText(格式化时长(小时));
        vo.setSlaOverdue(ticket.getSlaDueAt() != null
                && !ticket.getSlaDueAt().isEmpty()
                && ticket.getSlaDueAt().compareTo(
                        LocalDateTime.now().format(CommonConstants.DATE_TIME_FORMATTER)) < 0);
        return vo;
    }

    /**
     * 从进入当前环节到现在的自然小时数。
     * <p>
     * 用自然小时而不是工作小时：待办要的是「这件事压了多久」，用户心里也是按日历算的；
     * 工作小时口径留给 SLA 超期判定（那边按工作日历逐日累计）。两者的用途不同，
     * 混用会让「等了 3 天」和「超期 3 小时」对不上，用户会来回问。
     *
     * @param 进入时间 进入当前环节的时间串
     * @return 小时数，解析失败返回 0
     */
    private double 停留小时(String 进入时间) {
        if (进入时间 == null || 进入时间.trim().isEmpty()) {
            return 0;
        }
        try {
            LocalDateTime 起点 = LocalDateTime.parse(进入时间, CommonConstants.DATE_TIME_FORMATTER);
            double 小时 = Duration.between(起点, LocalDateTime.now()).toMinutes() / MINUTES_PER_HOUR;
            return Math.max(小时, 0);
        } catch (Exception 异常) {
            return 0;
        }
    }

    /**
     * 小时数转成人话。「3 天 2 小时」比「74.0 小时」好读得多。
     *
     * @param 小时 小时数
     * @return 可读时长
     */
    private String 格式化时长(double 小时) {
        if (小时 < 1) {
            int 分钟 = (int) Math.round(小时 * MINUTES_PER_HOUR);
            return 分钟 <= 0 ? "刚进入" : 分钟 + " 分钟";
        }
        if (小时 < HOURS_PER_DAY) {
            return (int) 小时 + " 小时";
        }
        int 天 = (int) (小时 / HOURS_PER_DAY);
        int 余下 = (int) (小时 - 天 * HOURS_PER_DAY);
        return 余下 > 0 ? 天 + " 天 " + 余下 + " 小时" : 天 + " 天";
    }

    /**
     * 空值当 0，排序时不会因为 null 抛异常。
     *
     * @param 值 数值
     * @return 兜底后的值
     */
    private double nvl(Double 值) {
        return 值 == null ? 0 : 值;
    }
}
