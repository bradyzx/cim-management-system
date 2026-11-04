package com.cim.reqflow.system.service;

import com.cim.reqflow.common.constant.SystemConstants;
import com.cim.reqflow.common.enums.MenuTypeEnum;
import com.cim.reqflow.system.entity.SysMenuDO;
import com.cim.reqflow.system.vo.MenuNodeVO;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 把平铺的菜单行组装成前端路由树。
 * 树在内存里递归组装：菜单总量是几十条量级，不值得为它写一次性的 SQL 递归，
 * 而且 SQLite 与 Oracle 的递归语法完全不同，写进 SQL 会破坏换库纪律。
 *
 * @author Haijie Xiao
 * @date 2026-11-03
 */
@Service
public class MenuTreeService {

    /** 无路径时的兜底路由名前缀 */
    private static final String FALLBACK_NAME_PREFIX = "Route";

    /**
     * 组装路由树。只有 DIR 与 MENU 两类节点进树，BUTTON 是按钮权限，不下发到路由。
     *
     * @param menus 平铺菜单列表
     * @return 路由树
     */
    public List<MenuNodeVO> buildRouteTree(List<SysMenuDO> menus) {
        List<SysMenuDO> routeNodes = new ArrayList<>(menus.size());
        for (SysMenuDO menu : menus) {
            boolean isRoute = MenuTypeEnum.DIR.getCode().equals(menu.getMenuType())
                    || MenuTypeEnum.MENU.getCode().equals(menu.getMenuType());
            if (isRoute) {
                routeNodes.add(menu);
            }
        }
        return mountChildren(routeNodes, null);
    }

    /**
     * 递归挂载某一层的子节点。
     *
     * @param allNodes 全部候选节点
     * @param parentId 父节点 ID，null 表示根层
     * @return 该层的节点列表
     */
    private List<MenuNodeVO> mountChildren(List<SysMenuDO> allNodes, String parentId) {
        List<SysMenuDO> currentLevel = new ArrayList<>();
        for (SysMenuDO menu : allNodes) {
            boolean isCurrentLevel = parentId == null
                    ? menu.getParentId() == null
                    : parentId.equals(menu.getParentId());
            if (isCurrentLevel) {
                currentLevel.add(menu);
            }
        }
        currentLevel.sort(
                Comparator.comparing(menu -> menu.getSort() == null ? 0 : menu.getSort()));

        List<MenuNodeVO> nodes = new ArrayList<>(currentLevel.size());
        for (SysMenuDO menu : currentLevel) {
            MenuNodeVO node = toRouteNode(menu);
            List<MenuNodeVO> childNodes = mountChildren(allNodes, menu.getId());
            if (!childNodes.isEmpty()) {
                for (MenuNodeVO child : childNodes) {
                    node.addChild(child);
                }
                // 目录补 redirect：点目录名直接进第一个子页面，而不是落到空白页
                node.setRedirect(childNodes.get(0).getPath());
            }
            nodes.add(node);
        }
        return nodes;
    }

    /**
     * 单个菜单行转路由节点。
     *
     * @param menu 菜单行
     * @return 路由节点
     */
    private MenuNodeVO toRouteNode(SysMenuDO menu) {
        MenuNodeVO node = new MenuNodeVO();
        node.setName(deriveRouteName(menu.getPath()));
        node.setPath(menu.getPath());
        node.setComponent(menu.getComponent());

        MenuNodeVO.Meta meta = new MenuNodeVO.Meta();
        meta.setTitle(menu.getMenuName());
        meta.setIcon(menu.getIcon());
        meta.setOrder(menu.getSort());
        meta.setKeepAlive(SystemConstants.FLAG_YES.equals(menu.getKeepAlive()));
        node.setMeta(meta);
        return node;
    }

    /**
     * 由路径推导路由名：/req/todo 推导为 ReqTodo，/req/detail/:id 推导为 ReqDetailId。
     * 路径在库内唯一，因此推导出的路由名也唯一。路径为空时用纳秒时间戳兜底，保证不为空。
     *
     * @param path 路由路径
     * @return 路由名
     */
    private String deriveRouteName(String path) {
        if (path == null || path.isBlank()) {
            return FALLBACK_NAME_PREFIX + System.nanoTime();
        }
        StringBuilder name = new StringBuilder(path.length());
        for (String segment : path.split("/")) {
            if (segment.isEmpty()) {
                continue;
            }
            // 首字母大写后拼接，:id 这类动态段也照转，保证名字合法且唯一
            name.append(Character.toUpperCase(segment.charAt(0))).append(segment.substring(1));
        }
        return name.toString();
    }
}
