package com.cim.reqflow.ticket.controller;

import com.cim.reqflow.common.R;
import com.cim.reqflow.common.constant.CommonConstants;
import com.cim.reqflow.ticket.dto.CaseExecDTO;
import com.cim.reqflow.ticket.dto.CaseGenerateDTO;
import com.cim.reqflow.ticket.dto.CaseSaveDTO;
import com.cim.reqflow.ticket.service.TicketCaseService;
import com.cim.reqflow.ticket.vo.CaseGenerateVO;
import com.cim.reqflow.ticket.vo.CaseListVO;
import com.cim.reqflow.ticket.vo.CoverageVO;
import com.cim.reqflow.ticket.vo.TestCaseVO;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 测试用例接口（契约 `11` 第九章）。
 * <p>
 * 没有类级 `@RequestMapping`：用例的路径天然分两组 —— 挂在单据下的
 * （列表、生成、覆盖率）和挂在用例自己身上的（编辑、执行登记），
 * 强行统一前缀会把 `/cases/{id}` 变成 `/ticket/cases/{id}`，反而绕。
 *
 * @author Haijie Xiao
 * @date 2026-11-05
 */
@RestController
public class CaseController {

    private final TicketCaseService ticketCaseService;

    public CaseController(TicketCaseService ticketCaseService) {
        this.ticketCaseService = ticketCaseService;
    }

    /**
     * 某一轮的用例 + 覆盖率。不传轮次取最新一轮。
     *
     * @param ticketId 单据 ID
     * @param roundNo  轮次，可空
     * @return 用例列表
     */
    @GetMapping("/ticket/{ticketId}/cases")
    public R<CaseListVO> list(@PathVariable String ticketId,
                              @RequestParam(required = false) Integer roundNo) {
        return R.ok(ticketCaseService.list(ticketId, roundNo));
    }

    /**
     * 按验收标准生成用例。验收标准未锁定（设计评审未全过）时拒绝。
     *
     * @param ticketId 单据 ID
     * @param dto      轮次，可空
     * @param request  HTTP 请求，用于取当前登录用户
     * @return 生成结果，带覆盖率
     */
    @PostMapping("/ticket/{ticketId}/cases/generate")
    public R<CaseGenerateVO> generate(@PathVariable String ticketId,
                                      @RequestBody(required = false) CaseGenerateDTO dto,
                                      HttpServletRequest request) {
        return R.ok(ticketCaseService.generate(ticketId, dto, currentUserId(request)));
    }

    /**
     * 覆盖率。UAT 通过时后端会用同一份逻辑再算一次，不信前端传的数。
     *
     * @param ticketId 单据 ID
     * @return 覆盖率
     */
    @GetMapping("/ticket/{ticketId}/coverage")
    public R<CoverageVO> coverage(@PathVariable String ticketId) {
        return R.ok(ticketCaseService.coverage(ticketId));
    }

    /**
     * 编辑用例。编号、来源标准、批次不可改，改过即标 HUMAN。
     *
     * @param caseId 用例 ID
     * @param dto    可编辑字段
     * @return 更新后的用例
     */
    @PutMapping("/cases/{caseId}")
    public R<TestCaseVO> update(@PathVariable String caseId, @RequestBody CaseSaveDTO dto) {
        return R.ok(ticketCaseService.update(caseId, dto));
    }

    /**
     * 执行登记。每次执行追加一条记录，不覆盖历史。
     *
     * @param caseId  用例 ID
     * @param dto     执行结果
     * @param request HTTP 请求，用于取当前登录用户
     * @return 更新后的用例
     */
    @PostMapping("/cases/{caseId}/exec")
    public R<TestCaseVO> exec(@PathVariable String caseId,
                              @RequestBody CaseExecDTO dto,
                              HttpServletRequest request) {
        return R.ok(ticketCaseService.exec(caseId, dto, currentUserId(request)));
    }

    /**
     * 从请求域取当前登录用户 ID。由 JwtInterceptor 写入，进到这里一定非空。
     *
     * @param request HTTP 请求
     * @return 用户 ID
     */
    private String currentUserId(HttpServletRequest request) {
        return (String) request.getAttribute(CommonConstants.ATTR_CURRENT_USER_ID);
    }
}
