package com.cim.reqflow.basic.controller;

import com.cim.reqflow.basic.dto.SystemSaveDTO;
import com.cim.reqflow.basic.service.BasicSystemService;
import com.cim.reqflow.basic.vo.SystemVO;
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
 * 基础数据 - 系统管理。
 * <p>
 * 系统只属一个项目，编码在项目内唯一。列表可以不带 projectId 跨项目查看，
 * 管理页需要一次看清所有系统分别挂在哪个项目下。
 *
 * @author Haijie Xiao
 * @date 2026-11-05
 */
@RestController
@RequestMapping("/basic/system")
public class BasicSystemController {

    /** 系统业务服务 */
    private final BasicSystemService basicSystemService;

    public BasicSystemController(BasicSystemService basicSystemService) {
        this.basicSystemService = basicSystemService;
    }

    /**
     * 分页查询系统。
     *
     * @param projectId 项目 ID，可为空表示跨项目
     * @param keyword   编码或名称关键字，可为空
     * @param status    状态过滤，可为空
     * @param page      页码，从 1 开始
     * @param pageSize  每页条数
     * @return 分页结果
     */
    @GetMapping("/list")
    public R<PageResult<SystemVO>> list(@RequestParam(required = false) String projectId,
                                        @RequestParam(required = false) String keyword,
                                        @RequestParam(required = false) String status,
                                        @RequestParam(defaultValue = "1") long page,
                                        @RequestParam(defaultValue = "20") long pageSize) {
        return R.ok(basicSystemService.listSystems(projectId, keyword, status, page, pageSize));
    }

    /**
     * 新增系统。所属项目、编码、名称必填。
     *
     * @param request 系统信息
     * @return 空数据响应
     */
    @PostMapping
    public R<Void> create(@RequestBody SystemSaveDTO request) {
        if (request == null || isBlank(request.getProjectId())
                || isBlank(request.getSystemCode()) || isBlank(request.getSystemName())) {
            return R.fail("所属项目、系统编码与系统名称必填");
        }
        basicSystemService.saveSystem(request);
        return R.ok();
    }

    /**
     * 修改系统。所属项目与编码不允许改。
     *
     * @param id      系统 ID
     * @param request 系统信息
     * @return 空数据响应
     */
    @PutMapping("/{id}")
    public R<Void> update(@PathVariable String id, @RequestBody SystemSaveDTO request) {
        if (request == null || isBlank(request.getSystemName())) {
            return R.fail("系统名称必填");
        }
        basicSystemService.updateSystem(id, request);
        return R.ok();
    }

    /**
     * 删除系统。下挂单据时拒绝。
     *
     * @param id 系统 ID
     * @return 空数据响应
     */
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable String id) {
        basicSystemService.deleteSystem(id);
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
