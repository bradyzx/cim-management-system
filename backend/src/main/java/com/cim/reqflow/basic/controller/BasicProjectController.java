package com.cim.reqflow.basic.controller;

import com.cim.reqflow.basic.dto.ProjectSaveDTO;
import com.cim.reqflow.basic.service.BasicProjectService;
import com.cim.reqflow.basic.vo.ProjectVO;
import com.cim.reqflow.common.PageResult;
import com.cim.reqflow.common.R;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 基础数据 - 项目管理。
 * <p>
 * 与 {@code /basic/projects}（建单下拉）不是一套接口：那个只回编码与名称，供表单联动；
 * 这里回完整字段，供管理页面维护。权限由菜单的按钮码 project:add / edit / delete 控制，
 * 与流转动作矩阵无关，后端不再重复判角色。
 *
 * @author Haijie Xiao
 * @date 2026-11-05
 */
@RestController
@RequestMapping("/basic/project")
public class BasicProjectController {

    /** 项目业务服务 */
    private final BasicProjectService basicProjectService;

    public BasicProjectController(BasicProjectService basicProjectService) {
        this.basicProjectService = basicProjectService;
    }

    /**
     * 分页查询项目。
     *
     * @param keyword  编码或名称关键字，可为空
     * @param status   状态过滤，可为空
     * @param page     页码，从 1 开始
     * @param pageSize 每页条数
     * @return 分页结果
     */
    @GetMapping("/list")
    public R<PageResult<ProjectVO>> list(@RequestParam(required = false) String keyword,
                                         @RequestParam(required = false) String status,
                                         @RequestParam(defaultValue = "1") long page,
                                         @RequestParam(defaultValue = "20") long pageSize) {
        return R.ok(basicProjectService.listProjects(keyword, status, page, pageSize));
    }

    /**
     * 新增项目。编码与名称必填。
     *
     * @param request 项目信息
     * @return 空数据响应
     */
    @PostMapping
    public R<Void> create(@RequestBody ProjectSaveDTO request) {
        if (request == null || isBlank(request.getProjectCode())
                || isBlank(request.getProjectName())) {
            return R.fail("项目编码与项目名称必填");
        }
        basicProjectService.saveProject(request);
        return R.ok();
    }

    /**
     * 修改项目。编码不允许改。
     *
     * @param id      项目 ID
     * @param request 项目信息
     * @return 空数据响应
     */
    @PutMapping("/{id}")
    public R<Void> update(@PathVariable String id, @RequestBody ProjectSaveDTO request) {
        if (request == null || isBlank(request.getProjectName())) {
            return R.fail("项目名称必填");
        }
        basicProjectService.updateProject(id, request);
        return R.ok();
    }

    /**
     * 删除项目。下挂系统或单据时拒绝。
     *
     * @param id 项目 ID
     * @return 空数据响应
     */
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable String id) {
        basicProjectService.deleteProject(id);
        return R.ok();
    }

    /**
     * 判断字符串是否空白。
     *
     * @param text 待判断字符串
     * @return true 表示 null 或全是空白
     */
    private boolean isBlank(String text) {
        return text == null || text.isBlank();
    }
}
