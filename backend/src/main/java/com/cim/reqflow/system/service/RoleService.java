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
import com.cim.reqflow.system.dto.RoleSaveDTO;
import com.cim.reqflow.system.entity.SysMenuDO;
import com.cim.reqflow.system.entity.SysRoleDO;
import com.cim.reqflow.system.entity.SysRoleMenuDO;
import com.cim.reqflow.system.entity.SysUserRoleDO;
import com.cim.reqflow.system.mapper.SysMenuMapper;
import com.cim.reqflow.system.mapper.SysRoleMapper;
import com.cim.reqflow.system.mapper.SysRoleMenuMapper;
import com.cim.reqflow.system.mapper.SysUserRoleMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 角色服务：角色增删改 + 角色-菜单授权。
 * 三条约束：
 * 1. 内置角色（isSystem = 1）不允许删除、不允许改编码，避免种子权限体系被破坏；
 * 2. 角色下仍有用户绑定时不能删，否则 sys_user_role 里会留下悬空授权；
 * 3. 菜单授权全删全插，因为 sys_role_menu 只有关联意义、无独立生命周期。
 *
 * @author Haijie Xiao
 * @date 2026-11-03
 */
@Service
public class RoleService {

    /** 补全父节点链时的防环上限，坏数据成环时不至于死循环 */
    private static final int MAX_PARENT_DEPTH = 32;

    /** 角色表数据访问 */
    private final SysRoleMapper sysRoleMapper;
    /** 菜单表数据访问 */
    private final SysMenuMapper sysMenuMapper;
    /** 角色菜单关系表数据访问 */
    private final SysRoleMenuMapper sysRoleMenuMapper;
    /** 用户角色关系表数据访问 */
    private final SysUserRoleMapper sysUserRoleMapper;

    public RoleService(SysRoleMapper sysRoleMapper, SysMenuMapper sysMenuMapper,
                       SysRoleMenuMapper sysRoleMenuMapper, SysUserRoleMapper sysUserRoleMapper) {
        this.sysRoleMapper = sysRoleMapper;
        this.sysMenuMapper = sysMenuMapper;
        this.sysRoleMenuMapper = sysRoleMenuMapper;
        this.sysUserRoleMapper = sysUserRoleMapper;
    }

    /**
     * 分页查角色。关键字同时匹配名称与编码。
     *
     * @param pageNo   页码
     * @param pageSize 每页条数
     * @param keyword  关键字，可为空
     * @param id       角色 ID，可为空
     * @param status   状态，可为空
     * @return 分页结果
     */
    public PageResult<SysRoleDO> listRoles(long pageNo, long pageSize, String keyword, String id,
                                         String status) {
        LambdaQueryWrapper<SysRoleDO> wrapper = Wrappers.lambdaQuery(SysRoleDO.class);
        wrapper.eq(SysRoleDO::getIsDeleted, SystemConstants.FLAG_NO);
        if (isNotBlank(keyword)) {
            wrapper.and(w -> w.like(SysRoleDO::getRoleName, keyword)
                    .or()
                    .like(SysRoleDO::getRoleCode, keyword));
        }
        if (isNotBlank(id)) {
            wrapper.eq(SysRoleDO::getId, id);
        }
        if (isNotBlank(status)) {
            wrapper.eq(SysRoleDO::getStatus, status);
        }
        wrapper.orderByAsc(SysRoleDO::getSort);

        Page<SysRoleDO> page = sysRoleMapper.selectPage(new Page<>(pageNo, pageSize), wrapper);
        return new PageResult<>(page.getRecords(), page.getTotal());
    }

    /**
     * 判断角色编码是否已被占用。
     *
     * @param roleCode  角色编码
     * @param excludeId 编辑场景传自身 ID，用于排除自己
     * @return 命中记录数，大于 0 表示已存在
     */
    public long countRolesByCode(String roleCode, String excludeId) {
        LambdaQueryWrapper<SysRoleDO> wrapper = Wrappers.lambdaQuery(SysRoleDO.class);
        wrapper.eq(SysRoleDO::getRoleCode, roleCode);
        wrapper.eq(SysRoleDO::getIsDeleted, SystemConstants.FLAG_NO);
        if (isNotBlank(excludeId)) {
            wrapper.ne(SysRoleDO::getId, excludeId);
        }
        return sysRoleMapper.selectCount(wrapper);
    }

    /**
     * 新增角色。新增的一律是自定义角色，内置标记固定为 0。
     *
     * @param request 角色信息
     */
    @Transactional(rollbackFor = Exception.class)
    public void saveRole(RoleSaveDTO request) {
        if (countRolesByCode(request.getRoleCode(), null) > 0) {
            throw new BizException("角色编码已存在：" + request.getRoleCode());
        }
        SysRoleDO role = new SysRoleDO();
        role.setId(IdUtils.nextId());
        role.setRoleCode(request.getRoleCode());
        role.setRoleName(request.getRoleName());
        role.setIsSystem(SystemConstants.FLAG_NO);
        role.setSort(request.getSort() == null ? Integer.valueOf(0) : request.getSort());
        role.setStatus(isNotBlank(request.getStatus())
                ? request.getStatus() : RecordStatusEnum.ACTIVE.getCode());
        role.setRemark(request.getRemark());
        role.setIsDeleted(SystemConstants.FLAG_NO);
        String now = LocalDateTime.now().format(CommonConstants.DATE_TIME_FORMATTER);
        role.setCreatedAt(now);
        role.setUpdatedAt(now);
        sysRoleMapper.insert(role);
    }

    /**
     * 修改角色。内置角色禁止改编码：编码被数据范围规则与代码内判定直接引用。
     *
     * @param id      角色 ID
     * @param request 角色信息
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateRole(String id, RoleSaveDTO request) {
        SysRoleDO role = sysRoleMapper.selectById(id);
        if (role == null || SystemConstants.FLAG_YES.equals(role.getIsDeleted())) {
            throw new BizException("角色不存在");
        }

        boolean codeChanged = isNotBlank(request.getRoleCode())
                && !request.getRoleCode().equals(role.getRoleCode());
        if (codeChanged && SystemConstants.FLAG_YES.equals(role.getIsSystem())) {
            throw new BizException("内置角色不允许修改编码：" + role.getRoleCode());
        }
        if (codeChanged && countRolesByCode(request.getRoleCode(), id) > 0) {
            throw new BizException("角色编码已存在：" + request.getRoleCode());
        }
        if (codeChanged) {
            role.setRoleCode(request.getRoleCode());
        }
        if (isNotBlank(request.getRoleName())) {
            role.setRoleName(request.getRoleName());
        }
        if (request.getSort() != null) {
            role.setSort(request.getSort());
        }
        if (isNotBlank(request.getStatus())) {
            role.setStatus(request.getStatus());
        }
        if (request.getRemark() != null) {
            role.setRemark(request.getRemark());
        }
        role.setUpdatedAt(LocalDateTime.now().format(CommonConstants.DATE_TIME_FORMATTER));
        sysRoleMapper.updateById(role);
    }

    /**
     * 删除角色（逻辑删除）。内置角色与仍有用户绑定的角色不允许删除。
     *
     * @param id 角色 ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteRole(String id) {
        SysRoleDO role = sysRoleMapper.selectById(id);
        if (role == null || SystemConstants.FLAG_YES.equals(role.getIsDeleted())) {
            throw new BizException("角色不存在");
        }
        if (SystemConstants.FLAG_YES.equals(role.getIsSystem())) {
            throw new BizException("内置角色不允许删除：" + role.getRoleName());
        }

        // 还有用户挂在这个角色下，删掉会让人静默丢权限，必须先解绑
        LambdaQueryWrapper<SysUserRoleDO> bindWrapper = Wrappers.lambdaQuery(SysUserRoleDO.class);
        bindWrapper.eq(SysUserRoleDO::getRoleId, id);
        if (sysUserRoleMapper.selectCount(bindWrapper) > 0) {
            throw new BizException("该角色下仍有用户绑定，请先在用户管理中解除绑定");
        }

        role.setIsDeleted(SystemConstants.FLAG_YES);
        role.setUpdatedAt(LocalDateTime.now().format(CommonConstants.DATE_TIME_FORMATTER));
        sysRoleMapper.updateById(role);

        // 角色删了授权就没有意义，直接物理清掉（本表无逻辑删除字段）
        LambdaQueryWrapper<SysRoleMenuDO> grantWrapper = Wrappers.lambdaQuery(SysRoleMenuDO.class);
        grantWrapper.eq(SysRoleMenuDO::getRoleId, id);
        sysRoleMenuMapper.delete(grantWrapper);
    }

    /**
     * 查询角色已授权的菜单 ID 列表（平铺，不含树结构）。
     *
     * @param roleId 角色 ID
     * @return 菜单 ID 列表
     */
    public List<String> listMenuIdsByRole(String roleId) {
        checkRoleExists(roleId);
        LambdaQueryWrapper<SysRoleMenuDO> wrapper = Wrappers.lambdaQuery(SysRoleMenuDO.class);
        wrapper.eq(SysRoleMenuDO::getRoleId, roleId);
        List<SysRoleMenuDO> grants = sysRoleMenuMapper.selectList(wrapper);
        List<String> menuIds = new ArrayList<>(grants.size());
        for (SysRoleMenuDO grant : grants) {
            menuIds.add(grant.getMenuId());
        }
        return menuIds;
    }

    /**
     * 保存角色-菜单授权：全删全插。
     * 前端树形勾选可能只传叶子节点，这里沿 parentId 补全父节点链，
     * 否则按角色过滤菜单时父目录缺失，子菜单渲染不出来。
     *
     * @param roleId  角色 ID
     * @param menuIds 勾选的菜单 ID 列表
     */
    @Transactional(rollbackFor = Exception.class)
    public void saveRoleMenus(String roleId, List<String> menuIds) {
        checkRoleExists(roleId);

        LambdaQueryWrapper<SysRoleMenuDO> deleteWrapper = Wrappers.lambdaQuery(SysRoleMenuDO.class);
        deleteWrapper.eq(SysRoleMenuDO::getRoleId, roleId);
        sysRoleMenuMapper.delete(deleteWrapper);

        if (menuIds == null || menuIds.isEmpty()) {
            return;
        }

        Set<String> grantedMenuIds = new HashSet<>(menuIds);
        for (String menuId : menuIds) {
            String currentId = menuId;
            int depth = 0;
            while (currentId != null && depth++ < MAX_PARENT_DEPTH) {
                SysMenuDO menu = sysMenuMapper.selectById(currentId);
                if (menu == null) {
                    break;
                }
                grantedMenuIds.add(menu.getId());
                currentId = menu.getParentId();
            }
        }

        String now = LocalDateTime.now().format(CommonConstants.DATE_TIME_FORMATTER);
        for (String menuId : grantedMenuIds) {
            SysRoleMenuDO relation = new SysRoleMenuDO();
            relation.setId(IdUtils.nextId());
            relation.setRoleId(roleId);
            relation.setMenuId(menuId);
            relation.setCreatedAt(now);
            sysRoleMenuMapper.insert(relation);
        }
    }

    /**
     * 校验角色存在且未删除。
     *
     * @param roleId 角色 ID
     */
    private void checkRoleExists(String roleId) {
        SysRoleDO role = sysRoleMapper.selectById(roleId);
        if (role == null || SystemConstants.FLAG_YES.equals(role.getIsDeleted())) {
            throw new BizException("角色不存在");
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
