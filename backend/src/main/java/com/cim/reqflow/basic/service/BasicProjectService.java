package com.cim.reqflow.basic.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cim.reqflow.basic.dto.ProjectSaveDTO;
import com.cim.reqflow.basic.vo.ProjectVO;
import com.cim.reqflow.common.BizException;
import com.cim.reqflow.common.PageResult;
import com.cim.reqflow.common.constant.CommonConstants;
import com.cim.reqflow.common.util.IdUtils;
import com.cim.reqflow.system.entity.SysDeptDO;
import com.cim.reqflow.system.entity.SysUserDO;
import com.cim.reqflow.system.mapper.SysDeptMapper;
import com.cim.reqflow.system.mapper.SysUserMapper;
import com.cim.reqflow.ticket.entity.ReqProjectDO;
import com.cim.reqflow.ticket.entity.ReqSystemDO;
import com.cim.reqflow.ticket.entity.ReqTicketDO;
import com.cim.reqflow.ticket.mapper.ReqProjectMapper;
import com.cim.reqflow.ticket.mapper.ReqSystemMapper;
import com.cim.reqflow.ticket.mapper.ReqTicketMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 项目管理：项目主数据的增删改查。
 * <p>
 * 两条硬规则写在这里，不靠前端拦：
 * 1. project_code 创建后不可修改 —— 它是附件存储路径的第一级，改名等于让历史附件全部失联；
 * 2. 下挂系统或单据的项目不允许删除 —— 单据的 project_id 是必填快照，删了项目就成孤儿。
 * <p>
 * 删除做物理删而不是把 deleted 置 1：uk_project_code 唯一索引不含 deleted 列，
 * 软删后这个编码会被永久占住，用户想用同一个编码重建项目就永远建不了，
 * 报错还看不懂原因。前置校验已确保项目下没有系统与单据，物理删不会留下孤儿。
 *
 * @author Haijie Xiao
 * @date 2026-11-05
 */
@Service
public class BasicProjectService {

    /** 项目状态默认值：规划中 */
    private static final String DEFAULT_STATUS = "PLANNING";

    /** 项目表数据访问 */
    private final ReqProjectMapper reqProjectMapper;
    /** 系统表数据访问，用于统计下挂系统数与校验重复 */
    private final ReqSystemMapper reqSystemMapper;
    /** 单据表数据访问，用于统计下挂单据数 */
    private final ReqTicketMapper reqTicketMapper;
    /** 用户表数据访问，用于把负责人 ID 翻成姓名 */
    private final SysUserMapper sysUserMapper;
    /** 部门表数据访问，用于把归口部门 ID 翻成名称 */
    private final SysDeptMapper sysDeptMapper;

    public BasicProjectService(ReqProjectMapper reqProjectMapper,
                               ReqSystemMapper reqSystemMapper,
                               ReqTicketMapper reqTicketMapper,
                               SysUserMapper sysUserMapper,
                               SysDeptMapper sysDeptMapper) {
        this.reqProjectMapper = reqProjectMapper;
        this.reqSystemMapper = reqSystemMapper;
        this.reqTicketMapper = reqTicketMapper;
        this.sysUserMapper = sysUserMapper;
        this.sysDeptMapper = sysDeptMapper;
    }

    /**
     * 分页查询项目列表。
     * <p>
     * 下挂单据数逐个 count：项目总量在几十条量级，逐个 count 走主键与二级索引都是毫秒级，
     * 为此写一段 group by 的拼装代码反而更难维护，数据量真上来了再改分组统计。
     *
     * @param keyword  关键字，匹配编码或名称，可为空
     * @param status   状态过滤，可为空
     * @param page     页码，从 1 开始
     * @param pageSize 每页条数
     * @return 分页结果
     */
    public PageResult<ProjectVO> listProjects(String keyword, String status,
                                              long page, long pageSize) {
        LambdaQueryWrapper<ReqProjectDO> wrapper = Wrappers.lambdaQuery(ReqProjectDO.class);
        wrapper.eq(ReqProjectDO::getDeleted, 0);
        if (isNotBlank(keyword)) {
            wrapper.and(w -> w.like(ReqProjectDO::getProjectCode, keyword)
                    .or()
                    .like(ReqProjectDO::getProjectName, keyword));
        }
        if (isNotBlank(status)) {
            wrapper.eq(ReqProjectDO::getStatus, status);
        }
        wrapper.orderByAsc(ReqProjectDO::getSortNo)
                .orderByAsc(ReqProjectDO::getCreatedAt);

        Page<ReqProjectDO> result = reqProjectMapper.selectPage(new Page<>(page, pageSize), wrapper);
        List<ReqProjectDO> projects = result.getRecords();
        if (projects.isEmpty()) {
            return new PageResult<>(new ArrayList<>(), result.getTotal());
        }

        List<String> projectIds = new ArrayList<>(projects.size());
        Set<String> userIds = new HashSet<>();
        Set<String> deptIds = new HashSet<>();
        for (ReqProjectDO project : projects) {
            projectIds.add(project.getId());
            if (isNotBlank(project.getOwnerId())) {
                userIds.add(project.getOwnerId());
            }
            if (isNotBlank(project.getDeptId())) {
                deptIds.add(project.getDeptId());
            }
        }
        Map<String, Integer> systemCounts = countSystemsByProjects(projectIds);
        Map<String, String> userNames = loadUserNames(userIds);
        Map<String, String> deptNames = loadDeptNames(deptIds);

        List<ProjectVO> rows = new ArrayList<>(projects.size());
        for (ReqProjectDO project : projects) {
            ProjectVO vo = new ProjectVO();
            vo.setId(project.getId());
            vo.setProjectCode(project.getProjectCode());
            vo.setProjectName(project.getProjectName());
            vo.setProjectType(project.getProjectType());
            vo.setStatus(project.getStatus());
            vo.setOwnerId(project.getOwnerId());
            vo.setOwnerName(userNames.get(project.getOwnerId()));
            vo.setDeptId(project.getDeptId());
            vo.setDeptName(deptNames.get(project.getDeptId()));
            vo.setStartDate(project.getStartDate());
            vo.setEndDate(project.getEndDate());
            vo.setRemark(project.getRemark());
            vo.setSortNo(project.getSortNo());
            vo.setSystemCount(systemCounts.getOrDefault(project.getId(), 0));
            vo.setTicketCount((int) countTicketsByProject(project.getId()));
            vo.setCreatedAt(project.getCreatedAt());
            rows.add(vo);
        }
        return new PageResult<>(rows, result.getTotal());
    }

    /**
     * 新增项目。
     *
     * @param request 项目信息
     */
    @Transactional(rollbackFor = Exception.class)
    public void saveProject(ProjectSaveDTO request) {
        String projectCode = request.getProjectCode().trim();
        if (countProjectsByCode(projectCode, null) > 0) {
            throw new BizException("项目编码已存在：" + projectCode);
        }
        checkDateRange(request.getStartDate(), request.getEndDate());

        String now = LocalDateTime.now().format(CommonConstants.DATE_TIME_FORMATTER);
        ReqProjectDO project = new ReqProjectDO();
        project.setId(IdUtils.nextId());
        project.setProjectCode(projectCode);
        project.setProjectName(request.getProjectName().trim());
        project.setProjectType(request.getProjectType());
        project.setStatus(isNotBlank(request.getStatus()) ? request.getStatus() : DEFAULT_STATUS);
        project.setOwnerId(emptyToNull(request.getOwnerId()));
        project.setDeptId(emptyToNull(request.getDeptId()));
        project.setStartDate(emptyToNull(request.getStartDate()));
        project.setEndDate(emptyToNull(request.getEndDate()));
        project.setRemark(request.getRemark());
        project.setSortNo(request.getSortNo() == null ? Integer.valueOf(0) : request.getSortNo());
        project.setCreatedAt(now);
        project.setUpdatedAt(now);
        project.setDeleted(0);
        reqProjectMapper.insert(project);
    }

    /**
     * 修改项目。编码与创建时间不可改，其余字段按传来的值覆盖：
     * 字符串传空串等于清空（清负责人、清结束日期都是正常操作），不传 null 表示不动。
     * <p>
     * 用 UpdateWrapper 逐列 set，而不是先改实体再 updateById：
     * MyBatis-Plus 的 updateById 默认跳过 null 字段，set(null) 根本写不进库，
     * 用户把结束日期清掉再保存会发现日期还在。逐列 set 才能真的写成 null。
     *
     * @param id      项目 ID
     * @param request 项目信息
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateProject(String id, ProjectSaveDTO request) {
        ReqProjectDO project = reqProjectMapper.selectById(id);
        if (project == null || Integer.valueOf(1).equals(project.getDeleted())) {
            throw new BizException("项目不存在");
        }

        // 编码不参与修改：前端已置灰，这里再拦一次，避免有人直接调接口改坏附件路径
        if (isNotBlank(request.getProjectCode())
                && !request.getProjectCode().trim().equals(project.getProjectCode())) {
            throw new BizException("项目编码创建后不可修改，如需更换请新建项目");
        }

        // 起止日期先合成最终值再校验：不然先写进去一个更早的结束日期、校验再抛异常，就是半更新
        String startDate = request.getStartDate() == null
                ? project.getStartDate() : emptyToNull(request.getStartDate());
        String endDate = request.getEndDate() == null
                ? project.getEndDate() : emptyToNull(request.getEndDate());
        checkDateRange(startDate, endDate);

        LambdaUpdateWrapper<ReqProjectDO> update = Wrappers.lambdaUpdate(ReqProjectDO.class);
        update.eq(ReqProjectDO::getId, id);
        if (isNotBlank(request.getProjectName())) {
            update.set(ReqProjectDO::getProjectName, request.getProjectName().trim());
        }
        if (request.getProjectType() != null) {
            update.set(ReqProjectDO::getProjectType, emptyToNull(request.getProjectType()));
        }
        if (isNotBlank(request.getStatus())) {
            update.set(ReqProjectDO::getStatus, request.getStatus());
        }
        if (request.getOwnerId() != null) {
            update.set(ReqProjectDO::getOwnerId, emptyToNull(request.getOwnerId()));
        }
        if (request.getDeptId() != null) {
            update.set(ReqProjectDO::getDeptId, emptyToNull(request.getDeptId()));
        }
        if (request.getStartDate() != null) {
            update.set(ReqProjectDO::getStartDate, startDate);
        }
        if (request.getEndDate() != null) {
            update.set(ReqProjectDO::getEndDate, endDate);
        }
        if (request.getSortNo() != null) {
            update.set(ReqProjectDO::getSortNo, request.getSortNo());
        }
        if (request.getRemark() != null) {
            update.set(ReqProjectDO::getRemark, request.getRemark());
        }
        update.set(ReqProjectDO::getUpdatedAt,
                LocalDateTime.now().format(CommonConstants.DATE_TIME_FORMATTER));
        reqProjectMapper.update(null, update);
    }

    /**
     * 删除项目。下挂系统或单据都不允许删：单据的 project_id 是提单时的必填快照，
     * 项目删掉后单据会指向不存在的记录，报表按项目汇总时直接丢数。
     *
     * @param id 项目 ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteProject(String id) {
        ReqProjectDO project = reqProjectMapper.selectById(id);
        if (project == null || Integer.valueOf(1).equals(project.getDeleted())) {
            throw new BizException("项目不存在");
        }

        long systemCount = countSystemsByProjects(List.of(id)).getOrDefault(id, 0);
        if (systemCount > 0) {
            throw new BizException("该项目下还有 " + systemCount
                    + " 个系统，请先在「系统管理」里删除或调整归属");
        }
        long ticketCount = countTicketsByProject(id);
        if (ticketCount > 0) {
            throw new BizException("该项目下还有 " + ticketCount
                    + " 张需求单，不能删除；不再使用请把状态改为「已关闭」");
        }
        reqProjectMapper.deleteById(id);
    }

    /**
     * 统计编码占用情况。
     *
     * @param projectCode 项目编码
     * @param excludeId   编辑场景传自身 ID，避免把自己算成重复
     * @return 命中记录数
     */
    private long countProjectsByCode(String projectCode, String excludeId) {
        LambdaQueryWrapper<ReqProjectDO> wrapper = Wrappers.lambdaQuery(ReqProjectDO.class);
        wrapper.eq(ReqProjectDO::getProjectCode, projectCode);
        if (isNotBlank(excludeId)) {
            wrapper.ne(ReqProjectDO::getId, excludeId);
        }
        return reqProjectMapper.selectCount(wrapper);
    }

    /**
     * 一次查出多个项目各挂了多少系统。
     *
     * @param projectIds 项目 ID 集合
     * @return 项目 ID → 系统数
     */
    private Map<String, Integer> countSystemsByProjects(List<String> projectIds) {
        Map<String, Integer> counts = new HashMap<>();
        List<ReqSystemDO> systems = reqSystemMapper.selectList(
                Wrappers.<ReqSystemDO>lambdaQuery()
                        .in(ReqSystemDO::getProjectId, projectIds)
                        .eq(ReqSystemDO::getDeleted, 0));
        for (ReqSystemDO system : systems) {
            counts.merge(system.getProjectId(), 1, Integer::sum);
        }
        return counts;
    }

    /**
     * 统计某个项目下挂的需求单数量。
     *
     * @param projectId 项目 ID
     * @return 单据数
     */
    private long countTicketsByProject(String projectId) {
        return reqTicketMapper.selectCount(
                Wrappers.<ReqTicketDO>lambdaQuery().eq(ReqTicketDO::getProjectId, projectId));
    }

    /**
     * 批量取用户姓名。
     *
     * @param userIds 用户 ID 集合
     * @return 用户 ID → 姓名
     */
    private Map<String, String> loadUserNames(Set<String> userIds) {
        Map<String, String> names = new HashMap<>();
        if (userIds.isEmpty()) {
            return names;
        }
        List<SysUserDO> users = sysUserMapper.selectBatchIds(userIds);
        for (SysUserDO user : users) {
            names.put(user.getId(), isNotBlank(user.getRealName())
                    ? user.getRealName() : user.getUsername());
        }
        return names;
    }

    /**
     * 批量取部门名称。
     *
     * @param deptIds 部门 ID 集合
     * @return 部门 ID → 名称
     */
    private Map<String, String> loadDeptNames(Set<String> deptIds) {
        Map<String, String> names = new HashMap<>();
        if (deptIds.isEmpty()) {
            return names;
        }
        List<SysDeptDO> depts = sysDeptMapper.selectBatchIds(deptIds);
        for (SysDeptDO dept : depts) {
            names.put(dept.getId(), dept.getDeptName());
        }
        return names;
    }

    /**
     * 校验起止日期。两个都可为空；都填时结束不能早于开始。
     *
     * @param startDate 开始日期 YYYY-MM-DD
     * @param endDate   结束日期 YYYY-MM-DD
     */
    private void checkDateRange(String startDate, String endDate) {
        if (isNotBlank(startDate) && isNotBlank(endDate) && endDate.compareTo(startDate) < 0) {
            throw new BizException("结束日期不能早于开始日期");
        }
    }

    /**
     * 空白串转 null，避免库里出现 '' 与 null 两种「没填」。
     *
     * @param text 待转换字符串
     * @return 非空白返回原值（去首尾空格），否则 null
     */
    private String emptyToNull(String text) {
        return isNotBlank(text) ? text.trim() : null;
    }

    /**
     * 判断字符串是否非空白。
     *
     * @param text 待判断字符串
     * @return true 表示非 null 且含非空白字符
     */
    private boolean isNotBlank(String text) {
        return text != null && !text.isBlank();
    }
}
