package com.cim.reqflow.ticket.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.cim.reqflow.common.BizException;
import com.cim.reqflow.system.entity.SysDeptDO;
import com.cim.reqflow.system.entity.SysDictDataDO;
import com.cim.reqflow.system.entity.SysUserDO;
import com.cim.reqflow.system.mapper.SysDeptMapper;
import com.cim.reqflow.system.mapper.SysDictDataMapper;
import com.cim.reqflow.system.mapper.SysUserMapper;
import com.cim.reqflow.ticket.entity.ReqFlowLogDO;
import com.cim.reqflow.ticket.entity.ReqProjectDO;
import com.cim.reqflow.ticket.entity.ReqSystemDO;
import com.cim.reqflow.ticket.entity.ReqTicketDO;
import com.cim.reqflow.ticket.flow.FlowAction;
import com.cim.reqflow.ticket.flow.FlowRegistry;
import com.cim.reqflow.ticket.mapper.ReqFlowLogMapper;
import com.cim.reqflow.ticket.mapper.ReqProjectMapper;
import com.cim.reqflow.ticket.mapper.ReqSystemMapper;
import com.cim.reqflow.ticket.mapper.ReqTicketMapper;
import com.cim.reqflow.ticket.vo.TicketDetailVO;
import com.cim.reqflow.ticket.vo.TimelineItemVO;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 单据详情查询：把一张单的全部可读信息与完整时间线一次组装好。
 * <p>
 * 所有编码（状态、优先级、类型、退回原因、关闭原因、动作码）在这里就翻成中文，
 * 前端不做第二次映射。字典是活的，前端缓存一份映射迟早会和库里对不上。
 * <p>
 * 本类只读，不写任何表。状态变更一律走 {@code TicketFlowService}。
 *
 * @author Haijie Xiao
 * @date 2026-11-04
 */
@Service
public class TicketDetailService {

    /** 字典类型：单据状态 */
    private static final String DICT_STATUS = "ticket_status";

    /** 字典类型：优先级 */
    private static final String DICT_PRIORITY = "ticket_priority";

    /** 字典类型：单据类型 */
    private static final String DICT_TICKET_TYPE = "ticket_type";

    /** 字典类型：退回原因 */
    private static final String DICT_RETURN_REASON = "return_reason";

    /** 字典类型：关闭原因 */
    private static final String DICT_CLOSE_REASON = "close_reason";

    /** 系统动作没有操作人，展示成「系统」而不是空白 */
    private static final String SYSTEM_OPERATOR = "系统";

    /** 标记位：已锁定 */
    private static final int FLAG_YES = 1;

    private final ReqTicketMapper reqTicketMapper;

    private final ReqFlowLogMapper reqFlowLogMapper;

    private final ReqProjectMapper reqProjectMapper;

    private final ReqSystemMapper reqSystemMapper;

    private final SysDictDataMapper sysDictDataMapper;

    private final SysUserMapper sysUserMapper;

    private final SysDeptMapper sysDeptMapper;

    public TicketDetailService(ReqTicketMapper reqTicketMapper,
                               ReqFlowLogMapper reqFlowLogMapper,
                               ReqProjectMapper reqProjectMapper,
                               ReqSystemMapper reqSystemMapper,
                               SysDictDataMapper sysDictDataMapper,
                               SysUserMapper sysUserMapper,
                               SysDeptMapper sysDeptMapper) {
        this.reqTicketMapper = reqTicketMapper;
        this.reqFlowLogMapper = reqFlowLogMapper;
        this.reqProjectMapper = reqProjectMapper;
        this.reqSystemMapper = reqSystemMapper;
        this.sysDictDataMapper = sysDictDataMapper;
        this.sysUserMapper = sysUserMapper;
        this.sysDeptMapper = sysDeptMapper;
    }

    /**
     * 查单据详情。
     *
     * @param ticketId 单据 ID
     * @return 详情，含完整时间线
     */
    public TicketDetailVO detail(String ticketId) {
        ReqTicketDO ticket = reqTicketMapper.selectById(ticketId);
        if (ticket == null) {
            throw new BizException("单据不存在：" + ticketId);
        }

        Map<String, SysDictDataDO> statusDict = loadDict(DICT_STATUS);
        Map<String, SysDictDataDO> priorityDict = loadDict(DICT_PRIORITY);
        Map<String, SysDictDataDO> typeDict = loadDict(DICT_TICKET_TYPE);
        Map<String, SysDictDataDO> reasonDict = loadDict(DICT_RETURN_REASON);
        Map<String, SysDictDataDO> closeDict = loadDict(DICT_CLOSE_REASON);

        List<ReqFlowLogDO> logs = reqFlowLogMapper.selectList(
                Wrappers.<ReqFlowLogDO>lambdaQuery()
                        .eq(ReqFlowLogDO::getTicketId, ticketId)
                        .orderByAsc(ReqFlowLogDO::getCreatedAt)
                        .orderByAsc(ReqFlowLogDO::getId));

        Map<String, String> userNames = loadUserNames(ticket, logs);

        TicketDetailVO vo = new TicketDetailVO();
        vo.setId(ticket.getId());
        vo.setTicketNo(ticket.getTicketNo());
        vo.setTicketType(ticket.getTicketType());
        vo.setTicketTypeLabel(labelOf(typeDict, ticket.getTicketType()));
        vo.setTitle(ticket.getTitle());
        vo.setDescription(ticket.getDescription());
        vo.setContentAsis(ticket.getContentAsis());
        vo.setContentTobe(ticket.getContentTobe());
        vo.setStatus(ticket.getStatus());
        vo.setStatusLabel(labelOf(statusDict, ticket.getStatus()));
        vo.setStatusCssClass(cssOf(statusDict, ticket.getStatus()));
        vo.setPriority(ticket.getPriority());
        vo.setPriorityLabel(labelOf(priorityDict, ticket.getPriority()));
        vo.setPriorityCssClass(cssOf(priorityDict, ticket.getPriority()));
        vo.setReporterName(userNames.getOrDefault(ticket.getReporterId(), ""));
        vo.setCreatorName(userNames.getOrDefault(ticket.getCreatorId(), ""));
        vo.setOwnerName(userNames.getOrDefault(ticket.getOwnerId(), ""));
        vo.setAssigneeName(userNames.getOrDefault(ticket.getAssigneeId(), ""));
        vo.setDeptName(resolveDeptName(ticket.getDeptId()));
        vo.setProjectName(resolveProjectName(ticket.getProjectId()));
        vo.setSystemCode(ticket.getSystemCode());
        vo.setSystemName(resolveSystemName(ticket.getSystemId()));
        vo.setModuleCode(ticket.getModuleCode());
        vo.setRoiDesc(ticket.getRoiDesc());
        vo.setRoiValue(ticket.getRoiValue());
        vo.setProposeDate(ticket.getProposeDate());
        vo.setExpectDueDate(ticket.getExpectDueDate());
        vo.setEstimatedHours(ticket.getEstimatedHours());
        vo.setActualHours(ticket.getActualHours());
        vo.setSlaDueAt(ticket.getSlaDueAt());
        vo.setSlaOverdueHours(ticket.getSlaOverdueHours());
        vo.setReturnCount(ticket.getReturnCount());
        vo.setSubmittedAt(ticket.getSubmittedAt());
        vo.setAssignedAt(ticket.getAssignedAt());
        vo.setDesignAt(ticket.getDesignAt());
        vo.setDevAt(ticket.getDevAt());
        vo.setUatAt(ticket.getUatAt());
        vo.setReleaseAt(ticket.getReleaseAt());
        vo.setAcceptanceCriteria(ticket.getAcceptanceCriteria());
        vo.setAcceptanceLocked(ticket.getAcceptanceLocked());
        vo.setSuspendedFrom(ticket.getSuspendedFrom());
        vo.setSuspendedFromLabel(labelOf(statusDict, ticket.getSuspendedFrom()));
        vo.setSuspendReason(ticket.getSuspendReason());
        vo.setExpectResumeAt(ticket.getExpectResumeAt());
        vo.setMonitorNote(ticket.getMonitorNote());
        vo.setMitPassed(ticket.getMitPassed());
        vo.setPlatformPassed(ticket.getPlatformPassed());
        vo.setSuperiorPassed(ticket.getSuperiorPassed());
        vo.setDsgnItPassed(ticket.getDsgnItPassed());
        vo.setDsgnPlatformPassed(ticket.getDsgnPlatformPassed());
        vo.setDsgnUserPassed(ticket.getDsgnUserPassed());
        vo.setEvalResult(ticket.getEvalResult());
        vo.setSatisfactionScore(ticket.getSatisfactionScore());
        vo.setEvalComment(ticket.getEvalComment());
        vo.setEvaluatedAt(ticket.getEvaluatedAt());
        vo.setCloseReason(ticket.getCloseReason());
        vo.setCloseReasonLabel(labelOf(closeDict, ticket.getCloseReason()));
        vo.setClosedAt(ticket.getClosedAt());
        vo.setBranchNameSuggest(ticket.getBranchNameSuggest());
        vo.setCreatedAt(ticket.getCreatedAt());
        vo.setUpdatedAt(ticket.getUpdatedAt());
        vo.setTimeline(buildTimeline(logs, statusDict, reasonDict, userNames));
        return vo;
    }

    /**
     * 组装时间线。动作码翻成中文，未知码保留原文 —— 宁可显示 SUBMIT_X 也别显示空白。
     *
     * @param logs         流转记录
     * @param statusLabels 状态标签
     * @param reasonLabels 退回原因标签
     * @param userNames    用户名
     * @return 时间线，顺序与入参一致
     */
    private List<TimelineItemVO> buildTimeline(List<ReqFlowLogDO> logs,
                                               Map<String, SysDictDataDO> statusDict,
                                               Map<String, SysDictDataDO> reasonDict,
                                               Map<String, String> userNames) {
        List<TimelineItemVO> timeline = new ArrayList<>(logs.size());
        for (ReqFlowLogDO log : logs) {
            TimelineItemVO item = new TimelineItemVO();
            item.setId(log.getId());
            item.setAction(log.getAction());
            item.setActionLabel(resolveActionLabel(log.getAction()));
            item.setFromStatus(log.getFromStatus());
            item.setFromStatusLabel(labelOf(statusDict, log.getFromStatus()));
            item.setToStatus(log.getToStatus());
            item.setToStatusLabel(labelOf(statusDict, log.getToStatus()));
            item.setOperatorName(log.getOperatorId() == null
                    ? SYSTEM_OPERATOR
                    : userNames.getOrDefault(log.getOperatorId(), SYSTEM_OPERATOR));
            item.setReasonCode(log.getReasonCode());
            item.setReasonLabel(labelOf(reasonDict, log.getReasonCode()));
            item.setRemark(log.getRemark());
            item.setReturnSeq(log.getReturnSeq());
            item.setCreatedAt(log.getCreatedAt());
            timeline.add(item);
        }

        // 括号里的人：from 侧是本条操作人，to 侧是下一条的操作人。
        // 要等整条时间线都组装完才能回填 —— 最后一条没有「下一条」，
        // toOwner 留空，界面就不画括号（单据刚进这一步，确实还没人接）。
        for (int i = 0; i < timeline.size(); i++) {
            TimelineItemVO item = timeline.get(i);
            item.setFromOwner(item.getOperatorName());
            if (i + 1 < timeline.size()) {
                item.setToOwner(timeline.get(i + 1).getOperatorName());
            }
        }
        return timeline;
    }

    /**
     * 动作码转中文。动作表里没有的历史码原样返回。
     *
     * @param actionCode 动作码
     * @return 中文名
     */
    private String resolveActionLabel(String actionCode) {
        if (actionCode == null || actionCode.isEmpty()) {
            return "";
        }
        FlowAction action = FlowRegistry.byCode(actionCode);
        return action == null ? actionCode : action.getLabel();
    }

    /**
     * 取字典标签，值不在字典里时返回原值 —— 显示原始码总比显示空白强。
     *
     * @param dict  字典
     * @param value 字典值
     * @return 标签
     */
    private String labelOf(Map<String, SysDictDataDO> dict, String value) {
        if (value == null) {
            return null;
        }
        SysDictDataDO item = dict.get(value);
        return item == null || item.getDictLabel() == null ? value : item.getDictLabel();
    }

    /**
     * 取字典的语义色，供前端标签直接使用。
     *
     * @param dict  字典
     * @param value 字典值
     * @return 语义色，取不到返回 null
     */
    private String cssOf(Map<String, SysDictDataDO> dict, String value) {
        if (value == null) {
            return null;
        }
        SysDictDataDO item = dict.get(value);
        return item == null ? null : item.getCssClass();
    }

    /**
     * 查部门名称。
     *
     * @param deptId 部门 ID
     * @return 部门名称，查不到返回空串
     */
    private String resolveDeptName(String deptId) {
        if (deptId == null || deptId.isEmpty()) {
            return "";
        }
        SysDeptDO dept = sysDeptMapper.selectById(deptId);
        return dept == null || dept.getDeptName() == null ? "" : dept.getDeptName();
    }

    /**
     * 查项目名称。
     *
     * @param projectId 项目 ID
     * @return 项目名称，查不到返回空串
     */
    private String resolveProjectName(String projectId) {
        if (projectId == null || projectId.isEmpty()) {
            return "";
        }
        ReqProjectDO project = reqProjectMapper.selectById(projectId);
        return project == null || project.getProjectName() == null ? "" : project.getProjectName();
    }

    /**
     * 查系统名称。
     *
     * @param systemId 系统 ID
     * @return 系统名称，查不到返回空串
     */
    private String resolveSystemName(String systemId) {
        if (systemId == null || systemId.isEmpty()) {
            return "";
        }
        ReqSystemDO system = reqSystemMapper.selectById(systemId);
        return system == null || system.getSystemName() == null ? "" : system.getSystemName();
    }

    /**
     * 批量取相关人名。单据上 4 个角色 + 时间线里所有操作人，一次查完。
     *
     * @param ticket 单据
     * @param logs   流转记录
     * @return 用户 ID 到姓名的映射
     */
    private Map<String, String> loadUserNames(ReqTicketDO ticket, List<ReqFlowLogDO> logs) {
        Set<String> userIds = new HashSet<>();
        addIfPresent(userIds, ticket.getReporterId());
        addIfPresent(userIds, ticket.getCreatorId());
        addIfPresent(userIds, ticket.getOwnerId());
        addIfPresent(userIds, ticket.getAssigneeId());
        for (ReqFlowLogDO log : logs) {
            addIfPresent(userIds, log.getOperatorId());
        }

        Map<String, String> names = new HashMap<>(userIds.size());
        if (userIds.isEmpty()) {
            return names;
        }
        List<SysUserDO> users = sysUserMapper.selectBatchIds(userIds);
        for (SysUserDO user : users) {
            names.put(user.getId(), user.getRealName());
        }
        return names;
    }

    /**
     * 非空才收集，避免 selectBatchIds 收到含 null 的集合。
     *
     * @param target 目标集合
     * @param value  待加入的值
     */
    private void addIfPresent(Set<String> target, String value) {
        if (value != null && !value.isEmpty()) {
            target.add(value);
        }
    }

    /**
     * 一次性载入某类字典的全部值。
     *
     * @param dictType 字典类型
     * @return 字典值到字典行的映射
     */
    private Map<String, SysDictDataDO> loadDict(String dictType) {
        List<SysDictDataDO> items = sysDictDataMapper.selectList(
                Wrappers.<SysDictDataDO>lambdaQuery()
                        .eq(SysDictDataDO::getDictType, dictType));
        Map<String, SysDictDataDO> dict = new HashMap<>(items.size());
        for (SysDictDataDO item : items) {
            dict.put(item.getDictValue(), item);
        }
        return dict;
    }
}
