package com.cim.reqflow.controller;

import com.cim.reqflow.common.R;
import com.cim.reqflow.common.constant.SystemConstants;
import com.cim.reqflow.system.dto.DataScopeRuleSaveDTO;
import com.cim.reqflow.system.entity.SysDataScopeRuleDO;
import com.cim.reqflow.system.service.DataScopeService;
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
 * 系统管理 - 数据范围规则。规则总量小，列表不分页，整表返回。
 *
 * @author Haijie Xiao
 * @date 2026-11-03
 */
@RestController
@RequestMapping("/system/data-scope")
public class SystemDataScopeController {

    /** 数据范围规则服务 */
    private final DataScopeService dataScopeService;

    public SystemDataScopeController(DataScopeService dataScopeService) {
        this.dataScopeService = dataScopeService;
    }

    /**
     * 查询数据范围规则。
     *
     * @param roleCode   角色编码，可为空
     * @param ticketType 单据类型，可为空
     * @return 规则列表
     */
    @GetMapping("/list")
    public R<List<SysDataScopeRuleDO>> list(@RequestParam(required = false) String roleCode,
                                          @RequestParam(required = false) String ticketType) {
        return R.ok(dataScopeService.listRules(roleCode, ticketType));
    }

    /**
     * 新增规则。单据类型不传按 * 处理，即兜底规则。
     *
     * @param request 规则信息
     * @return 空数据响应
     */
    @PostMapping
    public R<Void> create(@RequestBody DataScopeRuleSaveDTO request) {
        if (request == null || isBlank(request.getRoleCode())) {
            return R.fail("roleCode 必填");
        }
        if (isBlank(request.getScopeType())) {
            return R.fail("scopeType 必填");
        }
        if (isBlank(request.getTicketType())) {
            request.setTicketType(SystemConstants.TICKET_TYPE_ALL);
        }
        dataScopeService.saveRule(request);
        return R.ok();
    }

    /**
     * 更新规则。
     *
     * @param id      规则 ID
     * @param request 规则信息
     * @return 空数据响应
     */
    @PutMapping("/{id}")
    public R<Void> update(@PathVariable String id, @RequestBody DataScopeRuleSaveDTO request) {
        dataScopeService.updateRule(id, request);
        return R.ok();
    }

    /**
     * 删除规则（物理删除）。
     *
     * @param id 规则 ID
     * @return 空数据响应
     */
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable String id) {
        dataScopeService.deleteRule(id);
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
