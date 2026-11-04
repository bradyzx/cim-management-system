package com.cim.reqflow.controller;

import com.cim.reqflow.common.R;
import com.cim.reqflow.common.constant.CommonConstants;
import com.cim.reqflow.system.service.MenuTreeService;
import com.cim.reqflow.system.service.UserAccessService;
import com.cim.reqflow.system.vo.MenuNodeVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 按登录人角色动态下发路由树。
 * 这是接前端框架最核心的接口——树结构直接决定侧边栏与路由注册。
 *
 * @author Haijie Xiao
 * @date 2026-11-03
 */
@RestController
public class MenuController {

    /** 用户权限解析服务 */
    private final UserAccessService userAccessService;
    /** 菜单树组装服务 */
    private final MenuTreeService menuTreeService;

    public MenuController(UserAccessService userAccessService, MenuTreeService menuTreeService) {
        this.userAccessService = userAccessService;
        this.menuTreeService = menuTreeService;
    }

    /**
     * 当前登录人的路由树。可见性控制发生在后端，不靠前端隐藏。
     *
     * @param userId 当前用户 ID，由拦截器写入
     * @return 路由树
     */
    @GetMapping("/menu/all")
    public R<List<MenuNodeVO>> listUserMenu(
            @RequestAttribute(CommonConstants.ATTR_CURRENT_USER_ID) String userId) {
        return R.ok(menuTreeService.buildRouteTree(userAccessService.listGrantedMenus(userId)));
    }
}
