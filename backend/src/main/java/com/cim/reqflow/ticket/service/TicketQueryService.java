package com.cim.reqflow.ticket.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cim.reqflow.common.PageResult;
import com.cim.reqflow.common.constant.CommonConstants;
import com.cim.reqflow.system.entity.SysDeptDO;
import com.cim.reqflow.system.entity.SysDictDataDO;
import com.cim.reqflow.system.entity.SysUserDO;
import com.cim.reqflow.system.mapper.SysDeptMapper;
import com.cim.reqflow.system.mapper.SysDictDataMapper;
import com.cim.reqflow.system.mapper.SysUserMapper;
import com.cim.reqflow.ticket.dto.TicketQueryDTO;
import com.cim.reqflow.ticket.entity.ReqProjectDO;
import com.cim.reqflow.ticket.entity.ReqTicketDO;
import com.cim.reqflow.ticket.mapper.ReqProjectMapper;
import com.cim.reqflow.ticket.mapper.ReqTicketMapper;
import com.cim.reqflow.ticket.vo.TicketListVO;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 单据列表查询。
 * <p>
 * 两件事必须在这里做对：
 * ① **数据范围由后端算**（{@link TicketScopeResolver}），前端只传业务条件；
 * ② **标签与姓名一次批量补齐**，不在循环里逐条查库 —— 一页 20 条会打出上百次查询。
 *
 * @author Haijie Xiao
 * @date 2026-11-04
 */
@Service
public class TicketQueryService {

    /** 字典类型：单据状态 */
    private static final String DICT_STATUS = "ticket_status";

    /** 字典类型：优先级 */
    private static final String DICT_PRIORITY = "ticket_priority";

    /** 字典类型：单据类型 */
    private static final String DICT_TICKET_TYPE = "ticket_type";

    /** 关键字里的状态分隔符，支持前端多选状态 */
    private static final String STATUS_SEPARATOR = ",";

    /** 日期起点后缀 */
    private static final String DAY_START = " 00:00:00";

    /** 日期终点后缀 */
    private static final String DAY_END = " 23:59:59";

    private final ReqTicketMapper reqTicketMapper;

    private final ReqProjectMapper reqProjectMapper;

    private final SysDictDataMapper sysDictDataMapper;

    private final SysUserMapper sysUserMapper;

    private final SysDeptMapper sysDeptMapper;

    private final TicketScopeResolver ticketScopeResolver;

    public TicketQueryService(ReqTicketMapper reqTicketMapper,
                              ReqProjectMapper reqProjectMapper,
                              SysDictDataMapper sysDictDataMapper,
                              SysUserMapper sysUserMapper,
                              SysDeptMapper sysDeptMapper,
                              TicketScopeResolver ticketScopeResolver) {
        this.reqTicketMapper = reqTicketMapper;
        this.reqProjectMapper = reqProjectMapper;
        this.sysDictDataMapper = sysDictDataMapper;
        this.sysUserMapper = sysUserMapper;
        this.sysDeptMapper = sysDeptMapper;
        this.ticketScopeResolver = ticketScopeResolver;
    }

    /**
     * 分页查询单据。
     *
     * @param query  查询条件
     * @param userId 当前用户，用于算可见范围
     * @return 分页结果
     */
    public PageResult<TicketListVO> page(TicketQueryDTO query, String userId) {
        TicketQueryDTO condition = query == null ? new TicketQueryDTO() : query;
        LambdaQueryWrapper<ReqTicketDO> wrapper = Wrappers.lambdaQuery(ReqTicketDO.class);

        if (isNotBlank(condition.getKeyword())) {
            String keyword = condition.getKeyword().trim();
            // 单号与标题一起匹配：用户经常只记得其中一段
            wrapper.and(w -> w.like(ReqTicketDO::getTicketNo, keyword)
                    .or()
                    .like(ReqTicketDO::getTitle, keyword));
        }
        if (isNotBlank(condition.getTicketType())) {
            wrapper.eq(ReqTicketDO::getTicketType, condition.getTicketType());
        }
        if (isNotBlank(condition.getStatus())) {
            wrapper.in(ReqTicketDO::getStatus, splitStatus(condition.getStatus()));
        }
        if (isNotBlank(condition.getPriority())) {
            wrapper.eq(ReqTicketDO::getPriority, condition.getPriority());
        }
        if (isNotBlank(condition.getProjectId())) {
            wrapper.eq(ReqTicketDO::getProjectId, condition.getProjectId());
        }
        if (isNotBlank(condition.getSystemId())) {
            wrapper.eq(ReqTicketDO::getSystemId, condition.getSystemId());
        }
        if (isNotBlank(condition.getAssigneeId())) {
            wrapper.eq(ReqTicketDO::getAssigneeId, condition.getAssigneeId());
        }
        // 时间字段是 "yyyy-MM-dd HH:mm:ss" 字符串，补全到日首日尾再比较，字典序等于时间序
        if (isNotBlank(condition.getStartDate())) {
            wrapper.ge(ReqTicketDO::getCreatedAt, condition.getStartDate() + DAY_START);
        }
        if (isNotBlank(condition.getEndDate())) {
            wrapper.le(ReqTicketDO::getCreatedAt, condition.getEndDate() + DAY_END);
        }

        // 数据范围按单据类型选规则；未指定类型时按通配规则算（一期规则全是 *，够用）
        String scopeType = isNotBlank(condition.getTicketType())
                ? condition.getTicketType() : "*";
        ticketScopeResolver.applyReadableScope(wrapper, userId, scopeType);

        wrapper.orderByDesc(ReqTicketDO::getCreatedAt);
        wrapper.orderByDesc(ReqTicketDO::getId);

        long pageNo = condition.getPage() == null
                ? CommonConstants.DEFAULT_PAGE_NO : condition.getPage();
        long pageSize = condition.getPageSize() == null
                ? CommonConstants.DEFAULT_PAGE_SIZE : condition.getPageSize();
        Page<ReqTicketDO> page = reqTicketMapper.selectPage(new Page<>(pageNo, pageSize), wrapper);
        return new PageResult<>(toListRows(page.getRecords()), page.getTotal());
    }

    /**
     * 把单据实体批量转成列表行：字典与名称一次加载完，再逐条组装。
     * <p>
     * 抽成公开方法是为了给待办复用。待办也要展示单号、状态、优先级、提出人这些列，
     * 如果各写一遍，同一个状态码在两处翻译成不同的中文也没人会发现。
     *
     * @param tickets 单据实体，顺序与返回结果一致
     * @return 列表行
     */
    public List<TicketListVO> toListRows(List<ReqTicketDO> tickets) {
        if (tickets == null || tickets.isEmpty()) {
            return new ArrayList<>();
        }

        Map<String, SysDictDataDO> statusDict = loadDict(DICT_STATUS);
        Map<String, SysDictDataDO> priorityDict = loadDict(DICT_PRIORITY);
        Map<String, SysDictDataDO> typeDict = loadDict(DICT_TICKET_TYPE);
        Map<String, String> userNames = loadUserNames(tickets);
        Map<String, String> deptNames = loadDeptNames(tickets);
        Map<String, String> projectNames = loadProjectNames(tickets);

        List<TicketListVO> rows = new ArrayList<>(tickets.size());
        for (ReqTicketDO ticket : tickets) {
            TicketListVO row = new TicketListVO();
            row.setId(ticket.getId());
            row.setTicketNo(ticket.getTicketNo());
            row.setTicketType(ticket.getTicketType());
            row.setTicketTypeLabel(labelOf(typeDict, ticket.getTicketType()));
            row.setTitle(ticket.getTitle());
            row.setStatus(ticket.getStatus());
            row.setStatusLabel(labelOf(statusDict, ticket.getStatus()));
            row.setStatusCssClass(cssOf(statusDict, ticket.getStatus()));
            row.setPriority(ticket.getPriority());
            row.setPriorityLabel(labelOf(priorityDict, ticket.getPriority()));
            row.setPriorityCssClass(cssOf(priorityDict, ticket.getPriority()));
            row.setReporterName(userNames.getOrDefault(ticket.getReporterId(), ""));
            row.setAssigneeName(userNames.getOrDefault(ticket.getAssigneeId(), ""));
            row.setDeptName(deptNames.getOrDefault(ticket.getDeptId(), ""));
            row.setProjectName(projectNames.getOrDefault(ticket.getProjectId(), ""));
            row.setSystemCode(ticket.getSystemCode());
            row.setSlaDueAt(ticket.getSlaDueAt());
            row.setSlaOverdueHours(ticket.getSlaOverdueHours());
            row.setReturnCount(ticket.getReturnCount());
            row.setCreatedAt(ticket.getCreatedAt());
            rows.add(row);
        }
        return rows;
    }

    /**
     * 拆分状态多选值。
     *
     * @param status 逗号分隔的状态串
     * @return 状态列表
     */
    private List<String> splitStatus(String status) {
        return Arrays.stream(status.split(STATUS_SEPARATOR))
                .map(String::trim)
                .filter(item -> !item.isEmpty())
                .toList();
    }

    /**
     * 批量取人名。
     *
     * @param tickets 单据列表
     * @return 用户 ID 到姓名的映射
     */
    private Map<String, String> loadUserNames(List<ReqTicketDO> tickets) {
        Set<String> userIds = new HashSet<>();
        for (ReqTicketDO ticket : tickets) {
            addIfPresent(userIds, ticket.getReporterId());
            addIfPresent(userIds, ticket.getAssigneeId());
        }
        Map<String, String> names = new HashMap<>(userIds.size());
        if (userIds.isEmpty()) {
            return names;
        }
        for (SysUserDO user : sysUserMapper.selectBatchIds(userIds)) {
            names.put(user.getId(), user.getRealName());
        }
        return names;
    }

    /**
     * 批量取部门名。
     *
     * @param tickets 单据列表
     * @return 部门 ID 到名称的映射
     */
    private Map<String, String> loadDeptNames(List<ReqTicketDO> tickets) {
        Set<String> deptIds = new HashSet<>();
        for (ReqTicketDO ticket : tickets) {
            addIfPresent(deptIds, ticket.getDeptId());
        }
        Map<String, String> names = new HashMap<>(deptIds.size());
        if (deptIds.isEmpty()) {
            return names;
        }
        for (SysDeptDO dept : sysDeptMapper.selectBatchIds(deptIds)) {
            names.put(dept.getId(), dept.getDeptName());
        }
        return names;
    }

    /**
     * 批量取项目名。
     *
     * @param tickets 单据列表
     * @return 项目 ID 到名称的映射
     */
    private Map<String, String> loadProjectNames(List<ReqTicketDO> tickets) {
        Set<String> projectIds = new HashSet<>();
        for (ReqTicketDO ticket : tickets) {
            addIfPresent(projectIds, ticket.getProjectId());
        }
        Map<String, String> names = new HashMap<>(projectIds.size());
        if (projectIds.isEmpty()) {
            return names;
        }
        for (ReqProjectDO project : reqProjectMapper.selectBatchIds(projectIds)) {
            names.put(project.getId(), project.getProjectName());
        }
        return names;
    }

    /**
     * 一次性载入某类字典。
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

    /**
     * 取字典标签。
     *
     * @param dict  字典
     * @param value 字典值
     * @return 标签，取不到返回原值
     */
    private String labelOf(Map<String, SysDictDataDO> dict, String value) {
        if (value == null) {
            return null;
        }
        SysDictDataDO item = dict.get(value);
        return item == null || item.getDictLabel() == null ? value : item.getDictLabel();
    }

    /**
     * 取字典语义色。
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
     * 非空才收集。
     *
     * @param target 目标集合
     * @param value  值
     */
    private void addIfPresent(Set<String> target, String value) {
        if (value != null && !value.isEmpty()) {
            target.add(value);
        }
    }

    /**
     * 字符串非空判断。
     *
     * @param value 值
     * @return 非空返回 true
     */
    private boolean isNotBlank(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
