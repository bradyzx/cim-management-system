package com.cim.reqflow.controller;

import com.cim.reqflow.common.R;
import com.cim.reqflow.system.dto.DeptSaveDTO;
import com.cim.reqflow.system.entity.SysDeptDO;
import com.cim.reqflow.system.service.DeptService;
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
 * 系统管理 - 部门管理。
 * 列表平铺返回（已按 ancestors 排好序），树由前端组装；本期不支持改父级，
 * 因为改父级要把整棵子树的 ancestors 与层级全部重算，容易写错，等真有需求再做。
 *
 * @author Haijie Xiao
 * @date 2026-11-03
 */
@RestController
@RequestMapping("/system/dept")
public class SystemDeptController {

    /** 部门业务服务 */
    private final DeptService deptService;

    public SystemDeptController(DeptService deptService) {
        this.deptService = deptService;
    }

    /**
     * 查询全部部门（平铺）。
     *
     * @param name 关键字，可为空
     * @return 部门列表
     */
    @GetMapping("/list")
    public R<List<SysDeptDO>> list(@RequestParam(required = false) String name) {
        return R.ok(deptService.listDepts(name));
    }

    /**
     * 判断部门编码是否已存在。
     *
     * @param code 部门编码
     * @param id   编辑场景传自身 ID
     * @return true 表示已存在
     */
    @GetMapping("/code-exists")
    public R<Boolean> codeExists(@RequestParam("code") String code,
                                 @RequestParam(required = false) String id) {
        return R.ok(deptService.countDeptsByCode(code, id) > 0);
    }

    /**
     * 新增部门。部门编码与名称必填。
     *
     * @param request 部门信息
     * @return 空数据响应
     */
    @PostMapping
    public R<Void> create(@RequestBody DeptSaveDTO request) {
        if (request == null || isBlank(request.getDeptCode()) || isBlank(request.getDeptName())) {
            return R.fail("deptCode 和 deptName 必填");
        }
        deptService.saveDept(request);
        return R.ok();
    }

    /**
     * 修改部门。
     *
     * @param id      部门 ID
     * @param request 部门信息
     * @return 空数据响应
     */
    @PutMapping("/{id}")
    public R<Void> update(@PathVariable String id, @RequestBody DeptSaveDTO request) {
        deptService.updateDept(id, request);
        return R.ok();
    }

    /**
     * 删除部门（物理删除，删除前已校验无子部门、无人员引用）。
     *
     * @param id 部门 ID
     * @return 空数据响应
     */
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable String id) {
        deptService.deleteDept(id);
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
