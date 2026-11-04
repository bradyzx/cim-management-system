package com.cim.reqflow.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.cim.reqflow.common.BizException;
import com.cim.reqflow.common.constant.CommonConstants;
import com.cim.reqflow.common.constant.SystemConstants;
import com.cim.reqflow.common.enums.RecordStatusEnum;
import com.cim.reqflow.common.enums.MenuTypeEnum;
import com.cim.reqflow.common.util.IdUtils;
import com.cim.reqflow.system.dto.MenuSaveDTO;
import com.cim.reqflow.system.entity.SysMenuDO;
import com.cim.reqflow.system.entity.SysRoleMenuDO;
import com.cim.reqflow.system.mapper.SysMenuMapper;
import com.cim.reqflow.system.mapper.SysRoleMenuMapper;
import com.cim.reqflow.system.vo.MenuAuthNodeVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 菜单管理：目录 / 菜单 / 按钮 / 内嵌页 / 外链 五种类型的增删改。
 * 三条约束：
 * 1. path 只对非按钮类型有意义，BUTTON 不校验也不落库；
 * 2. 不允许改父级与类型，改父级要把子树可见性全部重算，等真有需求再做；
 * 3. 删除菜单要同步清理角色授权，否则 sys_role_menu 里留下指向不存在菜单的脏记录。
 *
 * @author Haijie Xiao
 * @date 2026-11-03
 */
@Service
public class MenuService {

    /** 菜单表数据访问 */
    private final SysMenuMapper sysMenuMapper;
    /** 角色菜单关系表数据访问 */
    private final SysRoleMenuMapper sysRoleMenuMapper;

    public MenuService(SysMenuMapper sysMenuMapper, SysRoleMenuMapper sysRoleMenuMapper) {
        this.sysMenuMapper = sysMenuMapper;
        this.sysRoleMenuMapper = sysRoleMenuMapper;
    }

    /**
     * 平铺返回全部未删除菜单（含停用），按排序号升序，树由前端组装。
     *
     * @param keyword 关键字，同时匹配名称与路径，可为空
     * @return 菜单列表
     */
    public List<SysMenuDO> listMenus(String keyword) {
        LambdaQueryWrapper<SysMenuDO> wrapper = Wrappers.lambdaQuery(SysMenuDO.class);
        wrapper.eq(SysMenuDO::getIsDeleted, SystemConstants.FLAG_NO);
        if (isNotBlank(keyword)) {
            wrapper.and(w -> w.like(SysMenuDO::getMenuName, keyword)
                    .or()
                    .like(SysMenuDO::getPath, keyword));
        }
        wrapper.orderByAsc(SysMenuDO::getSort);
        return sysMenuMapper.selectList(wrapper);
    }

    /**
     * 查询启用中的菜单，供运行视角组装路由树使用。
     *
     * @return 启用的菜单列表
     */
    public List<SysMenuDO> listActiveMenus() {
        LambdaQueryWrapper<SysMenuDO> wrapper = Wrappers.lambdaQuery(SysMenuDO.class);
        wrapper.eq(SysMenuDO::getIsDeleted, SystemConstants.FLAG_NO);
        wrapper.eq(SysMenuDO::getStatus, RecordStatusEnum.ACTIVE.getCode());
        wrapper.orderByAsc(SysMenuDO::getSort);
        return sysMenuMapper.selectList(wrapper);
    }

    /**
     * 角色授权用的节点列表：平铺、带菜单 ID，含停用节点（停用也要能取消授权）。
     *
     * @return 授权节点列表
     */
    public List<MenuAuthNodeVO> listAuthNodes() {
        List<SysMenuDO> menus = listMenus(null);
        List<MenuAuthNodeVO> nodes = new ArrayList<>(menus.size());
        for (SysMenuDO menu : menus) {
            MenuAuthNodeVO node = new MenuAuthNodeVO();
            node.setId(menu.getId());
            node.setParentId(menu.getParentId());
            node.setMenuName(menu.getMenuName());
            node.setMenuType(menu.getMenuType());
            node.setAuthCode(menu.getAuthCode());
            node.setSort(menu.getSort());
            node.setStatus(menu.getStatus());
            nodes.add(node);
        }
        return nodes;
    }

    /**
     * 统计同名菜单的数量，用于名称判重。
     *
     * @param menuName  菜单名称
     * @param excludeId 编辑场景传自身 ID
     * @return 命中记录数，大于 0 表示已存在
     */
    public long countMenusByName(String menuName, String excludeId) {
        return countByField(true, menuName, excludeId);
    }

    /**
     * 统计同路径菜单的数量，用于路由路径判重。
     *
     * @param path      路由路径
     * @param excludeId 编辑场景传自身 ID
     * @return 命中记录数，大于 0 表示已存在
     */
    public long countMenusByPath(String path, String excludeId) {
        return countByField(false, path, excludeId);
    }

    /**
     * 新增菜单。
     *
     * @param request 菜单信息
     */
    @Transactional(rollbackFor = Exception.class)
    public void saveMenu(MenuSaveDTO request) {
        checkMenuType(request.getMenuType());
        boolean needPath = needPath(request.getMenuType());
        if (needPath && !isNotBlank(request.getPath())) {
            throw new BizException("该类型菜单必须填写路由路径");
        }
        if (needPath && countMenusByPath(request.getPath(), null) > 0) {
            throw new BizException("路由路径已存在：" + request.getPath());
        }
        if (isNotBlank(request.getParentId())
                && sysMenuMapper.selectById(request.getParentId()) == null) {
            throw new BizException("上级菜单不存在");
        }

        String now = LocalDateTime.now().format(CommonConstants.DATE_TIME_FORMATTER);
        SysMenuDO menu = new SysMenuDO();
        menu.setId(IdUtils.nextId());
        menu.setMenuName(request.getMenuName());
        menu.setMenuType(request.getMenuType());
        menu.setParentId(isNotBlank(request.getParentId()) ? request.getParentId() : null);
        menu.setPath(needPath ? request.getPath() : null);
        menu.setComponent(MenuTypeEnum.BUTTON.getCode().equals(request.getMenuType())
                ? null : request.getComponent());
        menu.setAuthCode(request.getAuthCode());
        menu.setIcon(request.getIcon());
        menu.setSort(request.getSort() == null ? Integer.valueOf(0) : request.getSort());
        menu.setVisible(request.getVisible() == null
                ? SystemConstants.FLAG_YES : request.getVisible());
        menu.setKeepAlive(request.getKeepAlive() == null
                ? SystemConstants.FLAG_YES : request.getKeepAlive());
        menu.setLinkUrl(MenuTypeEnum.LINK.getCode().equals(request.getMenuType())
                ? request.getLinkUrl() : null);
        menu.setStatus(isNotBlank(request.getStatus())
                ? request.getStatus() : RecordStatusEnum.ACTIVE.getCode());
        menu.setCreatedAt(now);
        menu.setUpdatedAt(now);
        menu.setIsDeleted(SystemConstants.FLAG_NO);
        sysMenuMapper.insert(menu);
    }

    /**
     * 修改菜单。类型与父级不可改，因此路径有效性按现有类型判断。
     *
     * @param id      菜单 ID
     * @param request 菜单信息
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateMenu(String id, MenuSaveDTO request) {
        SysMenuDO menu = sysMenuMapper.selectById(id);
        if (menu == null || SystemConstants.FLAG_YES.equals(menu.getIsDeleted())) {
            throw new BizException("菜单不存在");
        }

        boolean needPath = needPath(menu.getMenuType());
        boolean pathChanged = isNotBlank(request.getPath())
                && !request.getPath().equals(menu.getPath());
        if (pathChanged && needPath && countMenusByPath(request.getPath(), id) > 0) {
            throw new BizException("路由路径已存在：" + request.getPath());
        }
        if (isNotBlank(request.getMenuName())) {
            menu.setMenuName(request.getMenuName());
        }
        if (pathChanged && needPath) {
            menu.setPath(request.getPath());
        }
        if (request.getComponent() != null) {
            menu.setComponent(request.getComponent());
        }
        if (request.getAuthCode() != null) {
            menu.setAuthCode(isNotBlank(request.getAuthCode()) ? request.getAuthCode() : null);
        }
        if (request.getIcon() != null) {
            menu.setIcon(request.getIcon());
        }
        if (request.getSort() != null) {
            menu.setSort(request.getSort());
        }
        if (request.getVisible() != null) {
            menu.setVisible(request.getVisible());
        }
        if (request.getKeepAlive() != null) {
            menu.setKeepAlive(request.getKeepAlive());
        }
        if (MenuTypeEnum.LINK.getCode().equals(menu.getMenuType())
                && request.getLinkUrl() != null) {
            menu.setLinkUrl(request.getLinkUrl());
        }
        if (isNotBlank(request.getStatus())) {
            menu.setStatus(request.getStatus());
        }
        menu.setUpdatedAt(LocalDateTime.now().format(CommonConstants.DATE_TIME_FORMATTER));
        sysMenuMapper.updateById(menu);
    }

    /**
     * 删除菜单（逻辑删除）。存在子节点时不允许删除。
     *
     * @param id 菜单 ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteMenu(String id) {
        SysMenuDO menu = sysMenuMapper.selectById(id);
        if (menu == null || SystemConstants.FLAG_YES.equals(menu.getIsDeleted())) {
            throw new BizException("菜单不存在");
        }

        LambdaQueryWrapper<SysMenuDO> childWrapper = Wrappers.lambdaQuery(SysMenuDO.class);
        childWrapper.eq(SysMenuDO::getParentId, id);
        childWrapper.eq(SysMenuDO::getIsDeleted, SystemConstants.FLAG_NO);
        if (sysMenuMapper.selectCount(childWrapper) > 0) {
            throw new BizException("该菜单下还有子节点，请先删除子节点");
        }

        menu.setIsDeleted(SystemConstants.FLAG_YES);
        menu.setUpdatedAt(LocalDateTime.now().format(CommonConstants.DATE_TIME_FORMATTER));
        sysMenuMapper.updateById(menu);

        // 菜单没了授权就不该保留，否则角色授权树里会出现指向不存在菜单的脏 ID
        LambdaQueryWrapper<SysRoleMenuDO> grantWrapper = Wrappers.lambdaQuery(SysRoleMenuDO.class);
        grantWrapper.eq(SysRoleMenuDO::getMenuId, id);
        sysRoleMenuMapper.delete(grantWrapper);
    }

    /**
     * 按字段判重。byName 为 true 时查名称，否则查路径；两者都只查未删除记录。
     *
     * @param byName    是否按名称判重
     * @param value     待判重值
     * @param excludeId 编辑场景传自身 ID
     * @return 命中记录数，大于 0 表示已存在
     */
    private long countByField(boolean byName, String value, String excludeId) {
        LambdaQueryWrapper<SysMenuDO> wrapper = Wrappers.lambdaQuery(SysMenuDO.class);
        if (byName) {
            wrapper.eq(SysMenuDO::getMenuName, value);
        } else {
            wrapper.eq(SysMenuDO::getPath, value);
        }
        wrapper.eq(SysMenuDO::getIsDeleted, SystemConstants.FLAG_NO);
        if (isNotBlank(excludeId)) {
            wrapper.ne(SysMenuDO::getId, excludeId);
        }
        return sysMenuMapper.selectCount(wrapper);
    }

    /**
     * 判断该类型是否需要校验路由路径：按钮不需要，其余四种必须有。
     *
     * @param menuType 菜单类型
     * @return true 表示需要路径
     */
    private boolean needPath(String menuType) {
        return !MenuTypeEnum.BUTTON.getCode().equals(menuType);
    }

    /**
     * 校验菜单类型合法性。
     *
     * @param menuType 菜单类型
     */
    private void checkMenuType(String menuType) {
        if (!isNotBlank(menuType)) {
            throw new BizException("menuType 必填");
        }
        if (MenuTypeEnum.of(menuType) == null) {
            throw new BizException("menuType 非法：" + menuType);
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
