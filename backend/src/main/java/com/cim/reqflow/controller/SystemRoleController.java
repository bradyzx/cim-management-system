package com.cim.reqflow.controller;

import com.cim.reqflow.common.PageResult;
import com.cim.reqflow.common.R;
import com.cim.reqflow.common.constant.CommonConstants;
import com.cim.reqflow.system.dto.RoleMenuDTO;
import com.cim.reqflow.system.dto.RoleSaveDTO;
import com.cim.reqflow.system.entity.SysRoleDO;
import com.cim.reqflow.system.service.RoleService;
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
 * 系统管理 - 角色管理。
 * 授权保存走「全删全插」，前端树形勾选只需提交勾中的菜单 ID，父节点链由后端补全。
 *
 * @author Haijie Xiao
 * @date 2026-11-03
 */
@RestController
@RequestMapping("/system/role")
public class SystemRoleController {

    /** 角色业务服务 */
    private final RoleService roleService;

    public SystemRoleController(RoleService roleService) {
        this.roleService = roleService;
    }

    /**
     * 分页查角色。
     *
     * @param page     页码
     * @param pageSize 每页条数
     * @param name     关键字，可为空
     * @param id       角色 ID，可为空
     * @param status   状态，可为空
     * @return 分页结果
     */
    @GetMapping("/list")
    public R<PageResult<SysRoleDO>> list(
            @RequestParam(defaultValue = CommonConstants.DEFAULT_PAGE_NO_TEXT) long page,
            @RequestParam(defaultValue = CommonConstants.DEFAULT_PAGE_SIZE_TEXT) long pageSize,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String id,
            @RequestParam(required = false) String status) {
        return R.ok(roleService.listRoles(page, pageSize, name, id, status));
    }

    /**
     * 判断角色编码是否已存在。
     *
     * @param code 角色编码
     * @param id   编辑场景传自身 ID
     * @return true 表示已存在
     */
    @GetMapping("/code-exists")
    public R<Boolean> codeExists(@RequestParam("code") String code,
                                 @RequestParam(required = false) String id) {
        return R.ok(roleService.countRolesByCode(code, id) > 0);
    }

    /**
     * 新增角色。角色编码与名称必填。
     *
     * @param request 角色信息
     * @return 空数据响应
     */
    @PostMapping
    public R<Void> create(@RequestBody RoleSaveDTO request) {
        if (request == null || isBlank(request.getRoleCode()) || isBlank(request.getRoleName())) {
            return R.fail("roleCode 和 roleName 必填");
        }
        roleService.saveRole(request);
        return R.ok();
    }

    /**
     * 修改角色。
     *
     * @param id      角色 ID
     * @param request 角色信息
     * @return 空数据响应
     */
    @PutMapping("/{id}")
    public R<Void> update(@PathVariable String id, @RequestBody RoleSaveDTO request) {
        roleService.updateRole(id, request);
        return R.ok();
    }

    /**
     * 删除角色（逻辑删除）。
     *
     * @param id 角色 ID
     * @return 空数据响应
     */
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable String id) {
        roleService.deleteRole(id);
        return R.ok();
    }

    /**
     * 查询角色已授权的菜单 ID。
     *
     * @param id 角色 ID
     * @return 菜单 ID 列表
     */
    @GetMapping("/{id}/menu-ids")
    public R<List<String>> listMenuIds(@PathVariable String id) {
        return R.ok(roleService.listMenuIdsByRole(id));
    }

    /**
     * 保存角色菜单授权。
     *
     * @param id      角色 ID
     * @param request 含菜单 ID 列表
     * @return 空数据响应
     */
    @PutMapping("/{id}/menus")
    public R<Void> saveMenus(@PathVariable String id, @RequestBody RoleMenuDTO request) {
        roleService.saveRoleMenus(id, request == null ? null : request.getMenuIds());
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
