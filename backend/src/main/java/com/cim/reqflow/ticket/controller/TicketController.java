package com.cim.reqflow.ticket.controller;

import com.cim.reqflow.common.PageResult;
import com.cim.reqflow.common.R;
import com.cim.reqflow.common.constant.CommonConstants;
import com.cim.reqflow.ticket.dto.TicketActionDTO;
import com.cim.reqflow.ticket.dto.TicketCreateDTO;
import com.cim.reqflow.ticket.dto.TicketQueryDTO;
import com.cim.reqflow.ticket.service.TicketCreateService;
import com.cim.reqflow.ticket.service.TicketDetailService;
import com.cim.reqflow.ticket.service.TicketFlowService;
import com.cim.reqflow.ticket.service.TicketQueryService;
import com.cim.reqflow.ticket.vo.ActionResultVO;
import com.cim.reqflow.ticket.vo.ActionVO;
import com.cim.reqflow.ticket.vo.TicketCreatedVO;
import com.cim.reqflow.ticket.vo.TicketDetailVO;
import com.cim.reqflow.ticket.vo.TicketListVO;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 单据流转接口。
 * <p>
 * 两个接口构成工作流程的全部入口：`/actions` 给出「现在能做什么」，
 * `/action` 执行其中一个。前端不在本地判断权限与按钮状态，
 * 否则权限规则改一次就要改两处，联调时必然对不上。
 *
 * @author Haijie Xiao
 * @date 2026-11-04
 */
@RestController
@RequestMapping("/ticket")
public class TicketController {

    /** 流转引擎 */
    private final TicketFlowService ticketFlowService;

    /** 详情查询，只读 */
    private final TicketDetailService ticketDetailService;

    /** 列表查询，只读 */
    private final TicketQueryService ticketQueryService;

    /** 建单 */
    private final TicketCreateService ticketCreateService;

    public TicketController(TicketFlowService ticketFlowService,
                            TicketDetailService ticketDetailService,
                            TicketQueryService ticketQueryService,
                            TicketCreateService ticketCreateService) {
        this.ticketFlowService = ticketFlowService;
        this.ticketDetailService = ticketDetailService;
        this.ticketQueryService = ticketQueryService;
        this.ticketCreateService = ticketCreateService;
    }

    /**
     * 新建单据。提交即触发规则版查重与文档生成，
     * 命中相似需求时返回 DUP_FOUND + 相似列表，由用户决定是否坚持新建。
     *
     * @param dto    表单
     * @param request HTTP 请求，用于取当前登录用户（即提出人）
     * @return 建单结果
     */
    @PostMapping
    public R<TicketCreatedVO> create(@RequestBody TicketCreateDTO dto,
                                     HttpServletRequest request) {
        return R.ok(ticketCreateService.create(dto, currentUserId(request)));
    }

    /**
     * 单据列表。可见范围由后端按角色算，前端只传业务条件。
     *
     * @param query   查询条件
     * @param request HTTP 请求，用于取当前登录用户
     * @return 分页结果
     */
    @GetMapping("/list")
    public R<PageResult<TicketListVO>> list(TicketQueryDTO query, HttpServletRequest request) {
        return R.ok(ticketQueryService.page(query, currentUserId(request)));
    }

    /**
     * 单据详情，含完整流转时间线。详情页首屏只调这一个接口。
     *
     * @param ticketId 单据 ID
     * @return 详情
     */
    @GetMapping("/{ticketId}/detail")
    public R<TicketDetailVO> detail(@PathVariable String ticketId) {
        return R.ok(ticketDetailService.detail(ticketId));
    }

    /**
     * 当前用户在该单上可执行的动作，含每个动作的可用性与阻塞原因。
     *
     * @param ticketId 单据 ID
     * @param request  HTTP 请求，用于取当前登录用户
     * @return 动作列表
     */
    @GetMapping("/{ticketId}/actions")
    public R<List<ActionVO>> listActions(@PathVariable String ticketId,
                                         HttpServletRequest request) {
        String userId = currentUserId(request);
        return R.ok(ticketFlowService.listActions(ticketId, userId));
    }

    /**
     * 执行一个流转动作。这是本系统唯一的写状态入口。
     *
     * @param ticketId 单据 ID
     * @param action   动作请求
     * @param request  HTTP 请求，用于取当前登录用户
     * @return 执行结果
     */
    @PostMapping("/{ticketId}/action")
    public R<ActionResultVO> execute(@PathVariable String ticketId,
                                     @RequestBody TicketActionDTO action,
                                     HttpServletRequest request) {
        String userId = currentUserId(request);
        return R.ok(ticketFlowService.execute(ticketId, action, userId));
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
