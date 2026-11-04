package com.cim.reqflow.controller;

import com.cim.reqflow.common.R;
import com.cim.reqflow.system.dto.MenuSaveDTO;
import com.cim.reqflow.system.entity.SysMenuDO;
import com.cim.reqflow.system.service.MenuService;
import com.cim.reqflow.system.service.MenuTreeService;
import com.cim.reqflow.system.vo.MenuAuthNodeVO;
import com.cim.reqflow.system.vo.MenuNodeVO;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 系统管理 - 菜单管理。
 * 三个读接口按用途分开，不要混用：
 * /list  运行视角的路由树（无 ID，给前端注册路由）
 * /nodes 角色授权用的平铺节点（有 ID，做勾选键）
 * /all   管理页面用的平铺全量（含停用，带全部字段，做维护表格）
 *
 * @author Haijie Xiao
 * @date 2026-11-03
 */
@RestController
@RequestMapping("/system/menu")
public class SystemMenuController {

    /** 菜单业务服务 */
    private final MenuService menuService;
    /** 菜单树组装服务 */
    private final MenuTreeService menuTreeService;

    public SystemMenuController(MenuService menuService, MenuTreeService menuTreeService) {
        this.menuService = menuService;
        this.menuTreeService = menuTreeService;
    }

    /**
     * 完整菜单树，只含启用节点，不分页。
     *
     * @return 路由树
     */
    @GetMapping("/list")
    public R<List<MenuNodeVO>> listRouteTree() {
        return R.ok(menuTreeService.buildRouteTree(menuService.listActiveMenus()));
    }

    /**
     * 角色授权用的节点列表：平铺返回、带菜单 ID，前端自行组树。
     *
     * @return 节点列表
     */
    @GetMapping("/nodes")
    public R<List<MenuAuthNodeVO>> listAuthNodes() {
        return R.ok(menuService.listAuthNodes());
    }

    /**
     * 管理页面用的平铺全量列表（含停用）。
     *
     * @param name 关键字，可为空
     * @return 菜单列表
     */
    @GetMapping("/all")
    public R<List<SysMenuDO>> listAll(@RequestParam(required = false) String name) {
        return R.ok(menuService.listMenus(name));
    }

    /**
     * 判断菜单名称是否已存在。
     *
     * @param name 菜单名称
     * @param id   编辑场景传自身 ID
     * @return true 表示已存在
     */
    @GetMapping("/name-exists")
    public R<Boolean> nameExists(@RequestParam("name") String name,
                                 @RequestParam(required = false) String id) {
        return R.ok(menuService.countMenusByName(name, id) > 0);
    }

    /**
     * 判断路由路径是否已存在。
     *
     * @param path 路由路径
     * @param id   编辑场景传自身 ID
     * @return true 表示已存在
     */
    @GetMapping("/path-exists")
    public R<Boolean> pathExists(@RequestParam("path") String path,
                                 @RequestParam(required = false) String id) {
        return R.ok(menuService.countMenusByPath(path, id) > 0);
    }

    /**
     * 新增菜单。菜单名称与类型必填。
     *
     * @param request 菜单信息
     * @return 空数据响应
     */
    @PostMapping
    public R<Void> create(@RequestBody MenuSaveDTO request) {
        if (request == null || isBlank(request.getMenuName()) || isBlank(request.getMenuType())) {
            return R.fail("menuName 和 menuType 必填");
        }
        menuService.saveMenu(request);
        return R.ok();
    }

    /**
     * 修改菜单。
     *
     * @param id      菜单 ID
     * @param request 菜单信息
     * @return 空数据响应
     */
    @PutMapping("/{id}")
    public R<Void> update(@PathVariable String id, @RequestBody MenuSaveDTO request) {
        menuService.updateMenu(id, request);
        return R.ok();
    }

    /**
     * 删除菜单（逻辑删除）。
     *
     * @param id 菜单 ID
     * @return 空数据响应
     */
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable String id) {
        menuService.deleteMenu(id);
        return R.ok();
    }

    /**
     * 判断字符串是否为空白。
     *
     * @param text 待判断字符串
     * @return true 表示为 null 或全空白
     */
    private boolean isBlank(String text) {
        return text == null || text.isBlank();
    }
}
