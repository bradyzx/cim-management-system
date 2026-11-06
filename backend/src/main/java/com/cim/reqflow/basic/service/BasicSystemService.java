package com.cim.reqflow.basic.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cim.reqflow.basic.dto.SystemSaveDTO;
import com.cim.reqflow.basic.vo.SystemVO;
import com.cim.reqflow.common.BizException;
import com.cim.reqflow.common.PageResult;
import com.cim.reqflow.common.constant.CommonConstants;
import com.cim.reqflow.common.util.IdUtils;
import com.cim.reqflow.system.entity.SysUserDO;
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
 * 系统管理：系统主数据的增删改查。
 * <p>
 * 系统只属一个项目，(project_id, system_code) 项目内唯一 —— 不同项目可以各有一个 MES。
 * 项目归属与编码创建后不可改：单据上的 project_id 是提单快照，
 * 改归属会让「单据所属项目」与「系统实际归属」长期不一致，只能新建系统解决。
 *
 * @author Haijie Xiao
 * @date 2026-11-05
 */
@Service
public class BasicSystemService {

    /** 系统状态默认值：建设中 */
    private static final String DEFAULT_STATUS = "BUILDING";

    /** 系统表数据访问 */
    private final ReqSystemMapper reqSystemMapper;
    /** 项目表数据访问，用于校验归属与翻译项目名 */
    private final ReqProjectMapper reqProjectMapper;
    /** 单据表数据访问，用于统计下挂单据数 */
    private final ReqTicketMapper reqTicketMapper;
    /** 用户表数据访问，用于把负责人 ID 翻成姓名 */
    private final SysUserMapper sysUserMapper;

    public BasicSystemService(ReqSystemMapper reqSystemMapper,
                              ReqProjectMapper reqProjectMapper,
                              ReqTicketMapper reqTicketMapper,
                              SysUserMapper sysUserMapper) {
        this.reqSystemMapper = reqSystemMapper;
        this.reqProjectMapper = reqProjectMapper;
        this.reqTicketMapper = reqTicketMapper;
        this.sysUserMapper = sysUserMapper;
    }

    /**
     * 分页查询系统列表。
     *
     * @param projectId 项目 ID，可为空表示跨项目
     * @param keyword   关键字，匹配系统编码或名称，可为空
     * @param status    状态过滤，可为空
     * @param page      页码，从 1 开始
     * @param pageSize  每页条数
     * @return 分页结果
     */
    public PageResult<SystemVO> listSystems(String projectId, String keyword, String status,
                                            long page, long pageSize) {
        LambdaQueryWrapper<ReqSystemDO> wrapper = Wrappers.lambdaQuery(ReqSystemDO.class);
        wrapper.eq(ReqSystemDO::getDeleted, 0);
        if (isNotBlank(projectId)) {
            wrapper.eq(ReqSystemDO::getProjectId, projectId);
        }
        if (isNotBlank(keyword)) {
            wrapper.and(w -> w.like(ReqSystemDO::getSystemCode, keyword)
                    .or()
                    .like(ReqSystemDO::getSystemName, keyword));
        }
        if (isNotBlank(status)) {
            wrapper.eq(ReqSystemDO::getStatus, status);
        }
        wrapper.orderByAsc(ReqSystemDO::getProjectId)
                .orderByAsc(ReqSystemDO::getSortNo)
                .orderByAsc(ReqSystemDO::getSystemCode);

        Page<ReqSystemDO> result = reqSystemMapper.selectPage(new Page<>(page, pageSize), wrapper);
        List<ReqSystemDO> systems = result.getRecords();
        if (systems.isEmpty()) {
            return new PageResult<>(new ArrayList<>(), result.getTotal());
        }

        Set<String> projectIds = new HashSet<>();
        Set<String> userIds = new HashSet<>();
        for (ReqSystemDO system : systems) {
            projectIds.add(system.getProjectId());
            if (isNotBlank(system.getOwnerId())) {
                userIds.add(system.getOwnerId());
            }
        }
        Map<String, ReqProjectDO> projects = loadProjects(projectIds);
        Map<String, String> userNames = loadUserNames(userIds);

        List<SystemVO> rows = new ArrayList<>(systems.size());
        for (ReqSystemDO system : systems) {
            SystemVO vo = new SystemVO();
            vo.setId(system.getId());
            vo.setProjectId(system.getProjectId());
            ReqProjectDO project = projects.get(system.getProjectId());
            if (project != null) {
                vo.setProjectCode(project.getProjectCode());
                vo.setProjectName(project.getProjectName());
            }
            vo.setSystemCode(system.getSystemCode());
            vo.setSystemName(system.getSystemName());
            vo.setSystemType(system.getSystemType());
            vo.setOwnerId(system.getOwnerId());
            vo.setOwnerName(userNames.get(system.getOwnerId()));
            vo.setStatus(system.getStatus());
            vo.setRemark(system.getRemark());
            vo.setSortNo(system.getSortNo());
            vo.setTicketCount((int) countTicketsBySystem(system.getId()));
            vo.setCreatedAt(system.getCreatedAt());
            rows.add(vo);
        }
        return new PageResult<>(rows, result.getTotal());
    }

    /**
     * 新增系统。所属项目必须存在，(项目, 编码) 也不能重复。
     *
     * @param request 系统信息
     */
    @Transactional(rollbackFor = Exception.class)
    public void saveSystem(SystemSaveDTO request) {
        ReqProjectDO project = reqProjectMapper.selectById(request.getProjectId());
        if (project == null || Integer.valueOf(1).equals(project.getDeleted())) {
            throw new BizException("所属项目不存在，请重新选择");
        }

        String systemCode = request.getSystemCode().trim();
        if (countSystemsByCode(request.getProjectId(), systemCode) > 0) {
            throw new BizException("该项目下已存在系统编码：" + systemCode);
        }

        String now = LocalDateTime.now().format(CommonConstants.DATE_TIME_FORMATTER);
        ReqSystemDO system = new ReqSystemDO();
        system.setId(IdUtils.nextId());
        system.setProjectId(request.getProjectId());
        system.setSystemCode(systemCode);
        system.setSystemName(request.getSystemName().trim());
        system.setSystemType(request.getSystemType());
        system.setOwnerId(isNotBlank(request.getOwnerId()) ? request.getOwnerId().trim() : null);
        system.setStatus(isNotBlank(request.getStatus()) ? request.getStatus() : DEFAULT_STATUS);
        system.setRemark(request.getRemark());
        system.setSortNo(request.getSortNo() == null ? Integer.valueOf(0) : request.getSortNo());
        system.setCreatedAt(now);
        system.setUpdatedAt(now);
        system.setDeleted(0);
        reqSystemMapper.insert(system);
    }

    /**
     * 修改系统。所属项目与编码不可改，其余字段传空串表示清空、传 null 表示不动。
     * <p>
     * 与修改项目一样用 UpdateWrapper 逐列 set：updateById 会跳过 null，
     * 用户清掉系统负责人、清空备注都写不进库。
     *
     * @param id      系统 ID
     * @param request 系统信息
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateSystem(String id, SystemSaveDTO request) {
        ReqSystemDO system = reqSystemMapper.selectById(id);
        if (system == null || Integer.valueOf(1).equals(system.getDeleted())) {
            throw new BizException("系统不存在");
        }

        // 归属与编码不参与修改：前端已置灰，这里再拦一次，防止直接调接口改坏历史单据的归属
        if (isNotBlank(request.getProjectId())
                && !request.getProjectId().equals(system.getProjectId())) {
            throw new BizException("系统所属项目不可修改，如需换项目请新建系统");
        }
        if (isNotBlank(request.getSystemCode())
                && !request.getSystemCode().trim().equals(system.getSystemCode())) {
            throw new BizException("系统编码创建后不可修改，如需更换请新建系统");
        }

        LambdaUpdateWrapper<ReqSystemDO> update = Wrappers.lambdaUpdate(ReqSystemDO.class);
        update.eq(ReqSystemDO::getId, id);
        if (isNotBlank(request.getSystemName())) {
            update.set(ReqSystemDO::getSystemName, request.getSystemName().trim());
        }
        if (request.getSystemType() != null) {
            update.set(ReqSystemDO::getSystemType, isNotBlank(request.getSystemType())
                    ? request.getSystemType().trim() : null);
        }
        if (request.getOwnerId() != null) {
            update.set(ReqSystemDO::getOwnerId, isNotBlank(request.getOwnerId())
                    ? request.getOwnerId().trim() : null);
        }
        if (isNotBlank(request.getStatus())) {
            update.set(ReqSystemDO::getStatus, request.getStatus());
        }
        if (request.getSortNo() != null) {
            update.set(ReqSystemDO::getSortNo, request.getSortNo());
        }
        if (request.getRemark() != null) {
            update.set(ReqSystemDO::getRemark, request.getRemark());
        }
        update.set(ReqSystemDO::getUpdatedAt,
                LocalDateTime.now().format(CommonConstants.DATE_TIME_FORMATTER));
        reqSystemMapper.update(null, update);
    }

    /**
     * 删除系统。下挂单据的系统不允许删，否则单据的 system_id 会指向不存在的记录。
     * 与项目一样做物理删：uk_system_project_code 唯一索引不含 deleted，软删会永久占住编码。
     *
     * @param id 系统 ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteSystem(String id) {
        ReqSystemDO system = reqSystemMapper.selectById(id);
        if (system == null || Integer.valueOf(1).equals(system.getDeleted())) {
            throw new BizException("系统不存在");
        }

        long ticketCount = countTicketsBySystem(id);
        if (ticketCount > 0) {
            throw new BizException("该系统下还有 " + ticketCount
                    + " 张需求单，不能删除；不再使用请把状态改为「已下线」");
        }
        reqSystemMapper.deleteById(id);
    }

    /**
     * 统计同项目下编码占用情况。
     *
     * @param projectId  项目 ID
     * @param systemCode 系统编码
     * @return 命中记录数
     */
    private long countSystemsByCode(String projectId, String systemCode) {
        return reqSystemMapper.selectCount(
                Wrappers.<ReqSystemDO>lambdaQuery()
                        .eq(ReqSystemDO::getProjectId, projectId)
                        .eq(ReqSystemDO::getSystemCode, systemCode));
    }

    /**
     * 统计某个系统下挂的需求单数量。
     *
     * @param systemId 系统 ID
     * @return 单据数
     */
    private long countTicketsBySystem(String systemId) {
        return reqTicketMapper.selectCount(
                Wrappers.<ReqTicketDO>lambdaQuery().eq(ReqTicketDO::getSystemId, systemId));
    }

    /**
     * 批量取项目，用于翻译所属项目名称。
     *
     * @param projectIds 项目 ID 集合
     * @return 项目 ID → 项目
     */
    private Map<String, ReqProjectDO> loadProjects(Set<String> projectIds) {
        Map<String, ReqProjectDO> projects = new HashMap<>();
        if (projectIds.isEmpty()) {
            return projects;
        }
        List<ReqProjectDO> rows = reqProjectMapper.selectBatchIds(projectIds);
        for (ReqProjectDO project : rows) {
            projects.put(project.getId(), project);
        }
        return projects;
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
     * 判断字符串是否非空白。
     *
     * @param text 待判断字符串
     * @return true 表示非 null 且含非空白字符
     */
    private boolean isNotBlank(String text) {
        return text != null && !text.isBlank();
    }
}
