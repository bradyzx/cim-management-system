package com.cim.reqflow.controller;

import com.cim.reqflow.common.PageResult;
import com.cim.reqflow.common.R;
import com.cim.reqflow.system.dto.UserQueryDTO;
import com.cim.reqflow.system.dto.UserRoleDTO;
import com.cim.reqflow.system.dto.UserSaveDTO;
import com.cim.reqflow.system.dto.UserStatusDTO;
import com.cim.reqflow.system.service.UserService;
import com.cim.reqflow.system.vo.UserDetailVO;
import com.cim.reqflow.system.vo.UserListVO;
import com.cim.reqflow.system.vo.UserOptionVO;
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
 * 系统管理 - 用户管理。
 * 不提供物理删除：停用（status = DISABLED）代替删除，历史单据上的人员引用不能断。
 *
 * @author Haijie Xiao
 * @date 2026-11-03
 */
@RestController
@RequestMapping("/system/user")
public class SystemUserController {

    /** 用户业务服务 */
    private final UserService userService;

    public SystemUserController(UserService userService) {
        this.userService = userService;
    }

    /**
     * 分页查用户。
     *
     * @param query 查询条件，由请求参数自动绑定
     * @return 分页结果
     */
    @GetMapping("/list")
    public R<PageResult<UserListVO>> list(UserQueryDTO query) {
        return R.ok(userService.listUsers(query));
    }

    /**
     * 派单候选人：只有持有「MIT 开发人员」角色的启用用户。
     * 派单弹窗的下拉框用它，替代原先直接拉全量用户的做法 ——
     * 派给非开发角色，单据会卡死在「待接收」，因为接收动作不对该角色开放。
     *
     * @return 候选人列表，含姓名与部门名
     */
    @GetMapping("/assignees")
    public R<List<UserOptionVO>> listAssignees() {
        return R.ok(userService.listAssignees());
    }

    /**
     * 用户详情。
     *
     * @param id 用户 ID
     * @return 用户详情
     */
    @GetMapping("/{id}")
    public R<UserDetailVO> detail(@PathVariable String id) {
        return R.ok(userService.getUserDetail(id));
    }

    /**
     * 判断登录账号是否已被占用。
     *
     * @param username 登录账号
     * @param id       编辑场景传自身 ID
     * @return true 表示已存在
     */
    @GetMapping("/username-exists")
    public R<Boolean> usernameExists(@RequestParam("username") String username,
                                     @RequestParam(required = false) String id) {
        return R.ok(userService.countUsersByUsername(username, id) > 0);
    }

    /**
     * 新增用户。新增必须给账号与姓名，缺参直接返回业务失败，不抛异常。
     *
     * @param request 用户信息
     * @return 空数据响应
     */
    @PostMapping
    public R<Void> create(@RequestBody UserSaveDTO request) {
        if (request == null || isBlank(request.getUsername()) || isBlank(request.getRealName())) {
            return R.fail("username 和 realName 必填");
        }
        userService.saveUser(request);
        return R.ok();
    }

    /**
     * 修改用户。
     *
     * @param id      用户 ID
     * @param request 用户信息
     * @return 空数据响应
     */
    @PutMapping("/{id}")
    public R<Void> update(@PathVariable String id, @RequestBody UserSaveDTO request) {
        userService.updateUser(id, request);
        return R.ok();
    }

    /**
     * 启用 / 停用用户。
     *
     * @param id      用户 ID
     * @param request 含目标状态
     * @return 空数据响应
     */
    @PutMapping("/{id}/status")
    public R<Void> updateStatus(@PathVariable String id, @RequestBody UserStatusDTO request) {
        String status = request == null ? null : request.getStatus();
        userService.updateUserStatus(id, status);
        return R.ok();
    }

    /**
     * 重置为统一初始口令并强制改密。返回初始口令，由前端一次性提示给管理员。
     *
     * @param id 用户 ID
     * @return 初始口令
     */
    @PutMapping("/{id}/password")
    public R<String> updatePasswordToInitial(@PathVariable String id) {
        return R.ok(userService.updatePasswordToInitial(id));
    }

    /**
     * 查询用户已绑定的角色 ID。
     *
     * @param id 用户 ID
     * @return 角色 ID 列表
     */
    @GetMapping("/{id}/role-ids")
    public R<List<String>> listUserRoleIds(@PathVariable String id) {
        return R.ok(userService.listUserRoleIds(id));
    }

    /**
     * 保存用户角色。
     *
     * @param id      用户 ID
     * @param request 含角色 ID 列表
     * @return 空数据响应
     */
    @PutMapping("/{id}/roles")
    public R<Void> saveRoles(@PathVariable String id, @RequestBody UserRoleDTO request) {
        userService.saveUserRoles(id, request == null ? null : request.getRoleIds());
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
