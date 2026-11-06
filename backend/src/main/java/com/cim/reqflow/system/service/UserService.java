package com.cim.reqflow.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cim.reqflow.common.BizException;
import com.cim.reqflow.common.PageResult;
import com.cim.reqflow.common.constant.CommonConstants;
import com.cim.reqflow.common.constant.SystemConstants;
import com.cim.reqflow.common.enums.RecordStatusEnum;
import com.cim.reqflow.common.util.IdUtils;
import com.cim.reqflow.system.dto.UserQueryDTO;
import com.cim.reqflow.system.dto.UserSaveDTO;
import com.cim.reqflow.system.entity.SysDeptDO;
import com.cim.reqflow.system.entity.SysRoleDO;
import com.cim.reqflow.system.entity.SysUserDO;
import com.cim.reqflow.system.entity.SysUserDeptDO;
import com.cim.reqflow.system.entity.SysUserRoleDO;
import com.cim.reqflow.system.mapper.SysDeptMapper;
import com.cim.reqflow.system.mapper.SysRoleMapper;
import com.cim.reqflow.system.mapper.SysUserDeptMapper;
import com.cim.reqflow.system.mapper.SysUserMapper;
import com.cim.reqflow.system.mapper.SysUserRoleMapper;
import com.cim.reqflow.system.vo.UserDetailVO;
import com.cim.reqflow.system.vo.UserListVO;
import com.cim.reqflow.system.vo.UserOptionVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 用户管理：账号增改、启停、口令重置、角色分配。
 * 三条纪律：
 * 1. 不做物理删除，用停用（status = DISABLED）代替，历史单据上的提出人、处理人引用不能断；
 * 2. 角色绑定带 deptId（角色按部门生效），主部门变更时同步刷新，否则兼岗权限会错位；
 * 3. 口令哈希绝不出后端，列表与详情都不返回该字段。
 *
 * @author Haijie Xiao
 * @date 2026-11-03
 */
@Service
public class UserService {

    /** 开发角色编码：派单只能派给持有该角色的人 */
    private static final String ROLE_CODE_MIT_DEV = "MIT_DEV";

    /** 用户状态：启用。停用的人不能再接单 */
    private static final String USER_STATUS_ACTIVE = "ACTIVE";

    /** 用户表数据访问 */
    private final SysUserMapper sysUserMapper;
    /** 部门表数据访问 */
    private final SysDeptMapper sysDeptMapper;
    /** 角色表数据访问 */
    private final SysRoleMapper sysRoleMapper;
    /** 用户部门关系表数据访问 */
    private final SysUserDeptMapper sysUserDeptMapper;
    /** 用户角色关系表数据访问 */
    private final SysUserRoleMapper sysUserRoleMapper;
    /** 口令散列与改密服务 */
    private final PasswordService passwordService;

    public UserService(SysUserMapper sysUserMapper, SysDeptMapper sysDeptMapper,
                       SysRoleMapper sysRoleMapper, SysUserDeptMapper sysUserDeptMapper,
                       SysUserRoleMapper sysUserRoleMapper, PasswordService passwordService) {
        this.sysUserMapper = sysUserMapper;
        this.sysDeptMapper = sysDeptMapper;
        this.sysRoleMapper = sysRoleMapper;
        this.sysUserDeptMapper = sysUserDeptMapper;
        this.sysUserRoleMapper = sysUserRoleMapper;
        this.passwordService = passwordService;
    }

    /**
     * 分页查用户，附带部门名与角色编码，供列表直接展示。
     * 部门名与角色用批量查询补齐，避免每条用户各查一次（N+1 查询）。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    public PageResult<UserListVO> listUsers(UserQueryDTO query) {
        UserQueryDTO condition = query == null ? new UserQueryDTO() : query;
        LambdaQueryWrapper<SysUserDO> wrapper = Wrappers.lambdaQuery(SysUserDO.class);
        // 关键字同时匹配账号与姓名：运维通常只记得其中一个
        if (isNotBlank(condition.getName())) {
            String keyword = condition.getName();
            wrapper.and(w -> w.like(SysUserDO::getUsername, keyword)
                    .or()
                    .like(SysUserDO::getRealName, keyword));
        }
        if (isNotBlank(condition.getId())) {
            wrapper.eq(SysUserDO::getId, condition.getId());
        }
        if (isNotBlank(condition.getDeptId())) {
            wrapper.eq(SysUserDO::getPrimaryDeptId, condition.getDeptId());
        }
        if (isNotBlank(condition.getStatus())) {
            wrapper.eq(SysUserDO::getStatus, condition.getStatus());
        }
        if (isNotBlank(condition.getStartTime())) {
            wrapper.ge(SysUserDO::getCreatedAt, condition.getStartTime());
        }
        if (isNotBlank(condition.getEndTime())) {
            wrapper.le(SysUserDO::getCreatedAt, condition.getEndTime());
        }
        wrapper.orderByAsc(SysUserDO::getUsername);

        long pageNo = condition.getPage() == null
                ? CommonConstants.DEFAULT_PAGE_NO : condition.getPage();
        long pageSize = condition.getPageSize() == null
                ? CommonConstants.DEFAULT_PAGE_SIZE : condition.getPageSize();
        Page<SysUserDO> page = sysUserMapper.selectPage(new Page<>(pageNo, pageSize), wrapper);
        List<SysUserDO> users = page.getRecords();

        Map<String, String> deptNameMap = loadDeptNameMap();
        Map<String, List<String>> userRoleMap = loadUserRoleMap(users);

        List<UserListVO> rows = new ArrayList<>(users.size());
        for (SysUserDO user : users) {
            UserListVO row = new UserListVO();
            row.setId(user.getId());
            row.setUsername(user.getUsername());
            row.setRealName(user.getRealName());
            row.setEmail(user.getEmail());
            // 列表返回明文：本接口只有拥有用户管理权限的角色可访问，且前端编辑弹窗直接复用行数据，
            // 一旦在列表脱敏，回填保存就会把掩码写进库。展示脱敏留给二期按角色分级后再做
            row.setMobile(user.getMobile());
            row.setEmploymentType(user.getEmploymentType());
            row.setPrimaryDeptId(user.getPrimaryDeptId());
            row.setDeptName(deptNameMap.getOrDefault(user.getPrimaryDeptId(), ""));
            row.setAuthType(user.getAuthType());
            row.setStatus(user.getStatus());
            row.setMustChangePassword(user.getMustChangePassword());
            row.setRemark(user.getRemark());
            row.setLastLoginAt(user.getLastLoginAt());
            row.setCreatedAt(user.getCreatedAt());
            row.setRoles(userRoleMap.getOrDefault(user.getId(), new ArrayList<>()));
            rows.add(row);
        }
        return new PageResult<>(rows, page.getTotal());
    }

    /**
     * 派单候选人：只返回持有「MIT 开发人员」角色的启用用户。
     * <p>
     * 为什么不给 {@link #listUsers} 加一个 roleCode 过滤参数了事：派单候选人是一条固定的
     * 业务口径（只有开发能承办），用户管理列表要能查全员。合成一个接口，调用方迟早漏传参数，
     * 把全员又放回下拉框里 —— 派给非开发角色，单据会卡死在「待接收」，
     * 因为接收动作只对开发角色开放。
     *
     * @return 候选人列表，含部门名便于区分同名；没有开发角色时返回空列表
     */
    public List<UserOptionVO> listAssignees() {
        // 第一步：按角色编码找角色 ID。编码是稳定标识，不按角色名称匹配
        SysRoleDO devRole = sysRoleMapper.selectOne(
                Wrappers.<SysRoleDO>lambdaQuery().eq(SysRoleDO::getRoleCode, ROLE_CODE_MIT_DEV));
        if (devRole == null) {
            return List.of();
        }

        // 第二步：该角色绑定了哪些人
        List<SysUserRoleDO> relations = sysUserRoleMapper.selectList(
                Wrappers.<SysUserRoleDO>lambdaQuery().eq(SysUserRoleDO::getRoleId, devRole.getId()));
        if (relations.isEmpty()) {
            return List.of();
        }
        List<String> userIds = new ArrayList<>(relations.size());
        for (SysUserRoleDO relation : relations) {
            userIds.add(relation.getUserId());
        }

        // 第三步：只保留启用的人。停用后角色绑定还在，但已经不在岗，不能再接单
        List<SysUserDO> users = sysUserMapper.selectList(
                Wrappers.<SysUserDO>lambdaQuery()
                        .in(SysUserDO::getId, userIds)
                        .eq(SysUserDO::getStatus, USER_STATUS_ACTIVE)
                        .orderByAsc(SysUserDO::getUsername));
        if (users.isEmpty()) {
            return List.of();
        }

        Map<String, String> deptNameMap = loadDeptNameMap();
        List<UserOptionVO> options = new ArrayList<>(users.size());
        for (SysUserDO user : users) {
            UserOptionVO option = new UserOptionVO();
            option.setId(user.getId());
            option.setRealName(user.getRealName());
            option.setDeptName(deptNameMap.getOrDefault(user.getPrimaryDeptId(), ""));
            options.add(option);
        }
        return options;
    }

    /**
     * 判断登录账号是否已被占用。
     *
     * @param username  登录账号
     * @param excludeId 编辑场景传自身 ID，用于排除自己
     * @return 命中记录数，大于 0 表示已存在
     */
    public long countUsersByUsername(String username, String excludeId) {
        LambdaQueryWrapper<SysUserDO> wrapper = Wrappers.lambdaQuery(SysUserDO.class);
        wrapper.eq(SysUserDO::getUsername, username);
        if (isNotBlank(excludeId)) {
            wrapper.ne(SysUserDO::getId, excludeId);
        }
        return sysUserMapper.selectCount(wrapper);
    }

    /**
     * 用户详情，含已绑定角色 ID，供编辑弹窗回显。
     *
     * @param id 用户 ID
     * @return 用户详情
     */
    public UserDetailVO getUserDetail(String id) {
        SysUserDO user = sysUserMapper.selectById(id);
        if (user == null) {
            throw new BizException("用户不存在");
        }
        UserDetailVO detail = new UserDetailVO();
        detail.setId(user.getId());
        detail.setUsername(user.getUsername());
        detail.setRealName(user.getRealName());
        detail.setEmail(user.getEmail());
        detail.setMobile(user.getMobile());
        detail.setEmploymentType(user.getEmploymentType());
        detail.setPrimaryDeptId(user.getPrimaryDeptId());
        detail.setStatus(user.getStatus());
        detail.setRemark(user.getRemark());
        detail.setRoleIds(listUserRoleIds(id));
        return detail;
    }

    /**
     * 新增用户。初始口令由统一口令策略生成并强制首次改密，
     * 口令经线下渠道告知本人，不由接口返回。
     *
     * @param request 用户信息
     */
    @Transactional(rollbackFor = Exception.class)
    public void saveUser(UserSaveDTO request) {
        if (countUsersByUsername(request.getUsername(), null) > 0) {
            throw new BizException("登录账号已存在：" + request.getUsername());
        }

        String now = LocalDateTime.now().format(CommonConstants.DATE_TIME_FORMATTER);
        SysUserDO user = new SysUserDO();
        user.setId(IdUtils.nextId());
        user.setUsername(request.getUsername());
        user.setPasswordHash(passwordService.hash(passwordService.getInitialPassword()));
        user.setRealName(request.getRealName());
        user.setEmail(request.getEmail());
        user.setMobile(request.getMobile());
        user.setAuthType(SystemConstants.AUTH_TYPE_LOCAL);
        user.setSyncSource(SystemConstants.SYNC_SOURCE_LOCAL);
        user.setEmploymentType(isNotBlank(request.getEmploymentType())
                ? request.getEmploymentType() : SystemConstants.EMPLOYMENT_TYPE_EMPLOYEE);
        user.setPrimaryDeptId(request.getPrimaryDeptId());
        user.setStatus(isNotBlank(request.getStatus())
                ? request.getStatus() : RecordStatusEnum.ACTIVE.getCode());
        user.setMustChangePassword(SystemConstants.FLAG_YES);
        user.setRemark(request.getRemark());
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        sysUserMapper.insert(user);

        syncPrimaryDept(user.getId(), request.getPrimaryDeptId(), now);
        saveUserRoles(user.getId(), request.getRoleIds());
    }

    /**
     * 修改用户。字段为空表示不修改；主部门变化时同步刷新角色绑定的部门。
     *
     * @param id      用户 ID
     * @param request 用户信息
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateUser(String id, UserSaveDTO request) {
        SysUserDO user = sysUserMapper.selectById(id);
        if (user == null) {
            throw new BizException("用户不存在");
        }

        boolean usernameChanged = isNotBlank(request.getUsername())
                && !request.getUsername().equals(user.getUsername());
        if (usernameChanged && countUsersByUsername(request.getUsername(), id) > 0) {
            throw new BizException("登录账号已存在：" + request.getUsername());
        }
        if (usernameChanged) {
            user.setUsername(request.getUsername());
        }
        if (isNotBlank(request.getRealName())) {
            user.setRealName(request.getRealName());
        }
        if (request.getEmail() != null) {
            user.setEmail(request.getEmail());
        }
        if (request.getMobile() != null) {
            user.setMobile(request.getMobile());
        }
        if (isNotBlank(request.getEmploymentType())) {
            user.setEmploymentType(request.getEmploymentType());
        }
        if (isNotBlank(request.getStatus())) {
            user.setStatus(request.getStatus());
        }
        if (request.getRemark() != null) {
            user.setRemark(request.getRemark());
        }

        String now = LocalDateTime.now().format(CommonConstants.DATE_TIME_FORMATTER);
        boolean deptChanged = isNotBlank(request.getPrimaryDeptId())
                && !request.getPrimaryDeptId().equals(user.getPrimaryDeptId());
        if (deptChanged) {
            user.setPrimaryDeptId(request.getPrimaryDeptId());
            syncPrimaryDept(id, request.getPrimaryDeptId(), now);
            // 角色按部门生效，主部门变了角色绑定的部门也要跟着换，否则权限判定对不上人
            syncUserRoleDept(id, request.getPrimaryDeptId());
        }
        user.setUpdatedAt(now);
        sysUserMapper.updateById(user);
    }

    /**
     * 启用 / 停用用户。
     *
     * @param id     用户 ID
     * @param status 目标状态
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateUserStatus(String id, String status) {
        if (!isNotBlank(status)) {
            throw new BizException("status 必填");
        }
        SysUserDO user = sysUserMapper.selectById(id);
        if (user == null) {
            throw new BizException("用户不存在");
        }
        user.setStatus(status);
        user.setUpdatedAt(LocalDateTime.now().format(CommonConstants.DATE_TIME_FORMATTER));
        sysUserMapper.updateById(user);
    }

    /**
     * 重置为初始口令并强制改密。
     *
     * @param id 用户 ID
     * @return 初始口令，由前端一次性提示给管理员
     */
    @Transactional(rollbackFor = Exception.class)
    public String updatePasswordToInitial(String id) {
        return passwordService.resetToInitialPassword(id);
    }

    /**
     * 查询用户已绑定的角色 ID 列表。
     *
     * @param userId 用户 ID
     * @return 角色 ID 列表
     */
    public List<String> listUserRoleIds(String userId) {
        LambdaQueryWrapper<SysUserRoleDO> wrapper = Wrappers.lambdaQuery(SysUserRoleDO.class);
        wrapper.eq(SysUserRoleDO::getUserId, userId);
        List<SysUserRoleDO> relations = sysUserRoleMapper.selectList(wrapper);
        List<String> roleIds = new ArrayList<>(relations.size());
        for (SysUserRoleDO relation : relations) {
            roleIds.add(relation.getRoleId());
        }
        return roleIds;
    }

    /**
     * 保存用户角色：全删全插。角色绑定的部门取用户当前主部门，全局角色留空。
     *
     * @param userId  用户 ID
     * @param roleIds 角色 ID 列表
     */
    @Transactional(rollbackFor = Exception.class)
    public void saveUserRoles(String userId, List<String> roleIds) {
        LambdaQueryWrapper<SysUserRoleDO> deleteWrapper = Wrappers.lambdaQuery(SysUserRoleDO.class);
        deleteWrapper.eq(SysUserRoleDO::getUserId, userId);
        sysUserRoleMapper.delete(deleteWrapper);

        if (roleIds == null || roleIds.isEmpty()) {
            return;
        }
        SysUserDO user = sysUserMapper.selectById(userId);
        String deptId = user == null ? null : user.getPrimaryDeptId();
        String now = LocalDateTime.now().format(CommonConstants.DATE_TIME_FORMATTER);

        for (String roleId : roleIds) {
            SysRoleDO role = sysRoleMapper.selectById(roleId);
            if (role == null || SystemConstants.FLAG_YES.equals(role.getIsDeleted())) {
                continue;
            }
            SysUserRoleDO relation = new SysUserRoleDO();
            relation.setId(IdUtils.nextId());
            relation.setUserId(userId);
            relation.setRoleId(roleId);
            // ADMIN 是全局角色，绑定部门会让它只在单个部门内生效，这里留空
            boolean isAdminRole = SystemConstants.ROLE_ADMIN.equals(role.getRoleCode());
            relation.setDeptId(isAdminRole ? null : deptId);
            relation.setCreatedAt(now);
            sysUserRoleMapper.insert(relation);
        }
    }

    /**
     * 构造 userId → 部门名 的对照表。
     *
     * @return 部门名对照表
     */
    private Map<String, String> loadDeptNameMap() {
        Map<String, String> nameMap = new LinkedHashMap<>();
        for (SysDeptDO dept : sysDeptMapper.selectList(Wrappers.lambdaQuery(SysDeptDO.class))) {
            nameMap.put(dept.getId(), dept.getDeptName());
        }
        return nameMap;
    }

    /**
     * 构造 userId → 角色编码列表 的对照表。一次查关系、一次查角色，全部在内存里拼。
     *
     * @param users 当前页用户
     * @return 角色编码对照表
     */
    private Map<String, List<String>> loadUserRoleMap(List<SysUserDO> users) {
        Map<String, List<String>> roleMap = new HashMap<>(16);
        if (users.isEmpty()) {
            return roleMap;
        }

        List<String> userIds = new ArrayList<>(users.size());
        for (SysUserDO user : users) {
            userIds.add(user.getId());
        }
        LambdaQueryWrapper<SysUserRoleDO> relationWrapper =
                Wrappers.lambdaQuery(SysUserRoleDO.class);
        relationWrapper.in(SysUserRoleDO::getUserId, userIds);
        List<SysUserRoleDO> relations = sysUserRoleMapper.selectList(relationWrapper);
        if (relations.isEmpty()) {
            return roleMap;
        }

        Map<String, String> roleCodeMap = new HashMap<>(16);
        for (SysRoleDO role : sysRoleMapper.selectList(Wrappers.lambdaQuery(SysRoleDO.class))) {
            roleCodeMap.put(role.getId(), role.getRoleCode());
        }
        for (SysUserRoleDO relation : relations) {
            String roleCode = roleCodeMap.get(relation.getRoleId());
            if (roleCode == null) {
                continue;
            }
            roleMap.computeIfAbsent(relation.getUserId(), key -> new ArrayList<>()).add(roleCode);
        }
        return roleMap;
    }

    /**
     * 主部门关系同步：先清掉旧记录，再写一条主岗记录。
     *
     * @param userId 用户 ID
     * @param deptId 部门 ID
     * @param now    当前时间
     */
    private void syncPrimaryDept(String userId, String deptId, String now) {
        if (!isNotBlank(deptId)) {
            return;
        }
        LambdaQueryWrapper<SysUserDeptDO> deleteWrapper = Wrappers.lambdaQuery(SysUserDeptDO.class);
        deleteWrapper.eq(SysUserDeptDO::getUserId, userId);
        sysUserDeptMapper.delete(deleteWrapper);

        SysUserDeptDO relation = new SysUserDeptDO();
        relation.setId(IdUtils.nextId());
        relation.setUserId(userId);
        relation.setDeptId(deptId);
        relation.setIsPrimary(SystemConstants.FLAG_YES);
        relation.setPostType(SystemConstants.POST_TYPE_PRIMARY);
        relation.setStatus(RecordStatusEnum.ACTIVE.getCode());
        relation.setCreatedAt(now);
        relation.setUpdatedAt(now);
        sysUserDeptMapper.insert(relation);
    }

    /**
     * 主部门变化时，把该用户非全局角色绑定的部门一起改掉。
     *
     * @param userId 用户 ID
     * @param deptId 新部门 ID
     */
    private void syncUserRoleDept(String userId, String deptId) {
        LambdaQueryWrapper<SysUserRoleDO> wrapper = Wrappers.lambdaQuery(SysUserRoleDO.class);
        wrapper.eq(SysUserRoleDO::getUserId, userId);
        for (SysUserRoleDO relation : sysUserRoleMapper.selectList(wrapper)) {
            if (relation.getDeptId() == null) {
                continue;
            }
            relation.setDeptId(deptId);
            sysUserRoleMapper.updateById(relation);
        }
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
