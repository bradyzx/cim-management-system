package com.cim.reqflow.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.cim.reqflow.common.BizException;
import com.cim.reqflow.common.constant.SystemConstants;
import com.cim.reqflow.common.enums.RecordStatusEnum;
import com.cim.reqflow.system.entity.SysMenuDO;
import com.cim.reqflow.system.entity.SysRoleDO;
import com.cim.reqflow.system.entity.SysRoleMenuDO;
import com.cim.reqflow.system.entity.SysUserDO;
import com.cim.reqflow.system.entity.SysUserRoleDO;
import com.cim.reqflow.system.mapper.SysMenuMapper;
import com.cim.reqflow.system.mapper.SysRoleMapper;
import com.cim.reqflow.system.mapper.SysRoleMenuMapper;
import com.cim.reqflow.system.mapper.SysUserMapper;
import com.cim.reqflow.system.mapper.SysUserRoleMapper;
import com.cim.reqflow.system.vo.UserInfoVO;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 用户权限解析：角色码、可见菜单、按钮权限码，全部沿「用户 → 角色 → 菜单」链路推导。
 * 一期只做功能权限；数据范围（部门维度）待业务模块开工时在此扩展，
 * 保持「权限只从一个入口算」的原则，避免各处各算一套。
 *
 * @author Haijie Xiao
 * @date 2026-11-03
 */
@Service
public class UserAccessService {

    /** 用户表数据访问 */
    private final SysUserMapper sysUserMapper;
    /** 用户角色关系表数据访问 */
    private final SysUserRoleMapper sysUserRoleMapper;
    /** 角色表数据访问 */
    private final SysRoleMapper sysRoleMapper;
    /** 角色菜单关系表数据访问 */
    private final SysRoleMenuMapper sysRoleMenuMapper;
    /** 菜单表数据访问 */
    private final SysMenuMapper sysMenuMapper;

    public UserAccessService(SysUserMapper sysUserMapper, SysUserRoleMapper sysUserRoleMapper,
                             SysRoleMapper sysRoleMapper, SysRoleMenuMapper sysRoleMenuMapper,
                             SysMenuMapper sysMenuMapper) {
        this.sysUserMapper = sysUserMapper;
        this.sysUserRoleMapper = sysUserRoleMapper;
        this.sysRoleMapper = sysRoleMapper;
        this.sysRoleMenuMapper = sysRoleMenuMapper;
        this.sysMenuMapper = sysMenuMapper;
    }

    /**
     * 按登录账号查用户。
     *
     * @param username 登录账号
     * @return 用户实体，查不到返回 null
     */
    public SysUserDO getUserByUsername(String username) {
        LambdaQueryWrapper<SysUserDO> wrapper = Wrappers.lambdaQuery(SysUserDO.class);
        wrapper.eq(SysUserDO::getUsername, username);
        return sysUserMapper.selectOne(wrapper);
    }

    /**
     * 用户启用的角色编码列表。角色被停用的不生效。
     *
     * @param userId 用户 ID
     * @return 角色编码列表，无角色时返回空列表
     */
    public List<String> listRoleCodes(String userId) {
        List<String> roleCodes = new ArrayList<>();
        List<String> roleIds = listUserRoleIds(userId);
        if (roleIds.isEmpty()) {
            return roleCodes;
        }
        List<SysRoleDO> roles = sysRoleMapper.selectByIds(roleIds);
        for (SysRoleDO role : roles) {
            if (RecordStatusEnum.ACTIVE.getCode().equals(role.getStatus())) {
                roleCodes.add(role.getRoleCode());
            }
        }
        return roleCodes;
    }

    /**
     * 用户被授权的全部菜单（含按钮），只取启用且未删除的。
     *
     * @param userId 用户 ID
     * @return 菜单列表，无授权时返回空列表
     */
    public List<SysMenuDO> listGrantedMenus(String userId) {
        List<String> roleIds = listUserRoleIds(userId);
        if (roleIds.isEmpty()) {
            return new ArrayList<>();
        }

        LambdaQueryWrapper<SysRoleMenuDO> grantWrapper = Wrappers.lambdaQuery(SysRoleMenuDO.class);
        grantWrapper.in(SysRoleMenuDO::getRoleId, roleIds);
        List<SysRoleMenuDO> grants = sysRoleMenuMapper.selectList(grantWrapper);
        if (grants.isEmpty()) {
            return new ArrayList<>();
        }

        // 多角色可能授权了同一个菜单，去重后一次查出
        List<String> menuIds = new ArrayList<>();
        for (SysRoleMenuDO grant : grants) {
            if (!menuIds.contains(grant.getMenuId())) {
                menuIds.add(grant.getMenuId());
            }
        }
        LambdaQueryWrapper<SysMenuDO> menuWrapper = Wrappers.lambdaQuery(SysMenuDO.class);
        menuWrapper.in(SysMenuDO::getId, menuIds);
        menuWrapper.eq(SysMenuDO::getStatus, RecordStatusEnum.ACTIVE.getCode());
        menuWrapper.eq(SysMenuDO::getIsDeleted, SystemConstants.FLAG_NO);
        return sysMenuMapper.selectList(menuWrapper);
    }

    /**
     * 按钮权限码列表，供 /auth/codes 使用。权限码为空的菜单不输出。
     *
     * @param userId 用户 ID
     * @return 权限码列表
     */
    public List<String> listAuthCodes(String userId) {
        List<String> authCodes = new ArrayList<>();
        for (SysMenuDO menu : listGrantedMenus(userId)) {
            String authCode = menu.getAuthCode();
            if (authCode != null && !authCode.isBlank()) {
                authCodes.add(authCode);
            }
        }
        return authCodes;
    }

    /**
     * 组装 /user/info 与登录返回共用的用户信息体。
     *
     * @param userId 用户 ID
     * @return 用户信息
     */
    public UserInfoVO getUserInfo(String userId) {
        SysUserDO user = sysUserMapper.selectById(userId);
        if (user == null) {
            throw new BizException("用户不存在", BizException.STATUS_UNAUTHORIZED);
        }
        UserInfoVO userInfo = new UserInfoVO();
        userInfo.setId(user.getId());
        userInfo.setUsername(user.getUsername());
        userInfo.setRealName(user.getRealName());
        userInfo.setAvatar(user.getAvatar());
        userInfo.setRoles(listRoleCodes(userId));
        userInfo.setHomePath(SystemConstants.HOME_PATH);
        return userInfo;
    }

    /**
     * 查询用户绑定的角色 ID 列表。
     *
     * @param userId 用户 ID
     * @return 角色 ID 列表
     */
    private List<String> listUserRoleIds(String userId) {
        LambdaQueryWrapper<SysUserRoleDO> wrapper = Wrappers.lambdaQuery(SysUserRoleDO.class);
        wrapper.eq(SysUserRoleDO::getUserId, userId);
        List<SysUserRoleDO> relations = sysUserRoleMapper.selectList(wrapper);
        List<String> roleIds = new ArrayList<>(relations.size());
        for (SysUserRoleDO relation : relations) {
            roleIds.add(relation.getRoleId());
        }
        return roleIds;
    }
}
