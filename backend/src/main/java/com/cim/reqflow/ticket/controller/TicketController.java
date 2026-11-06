package com.cim.reqflow.ticket.controller;

import com.cim.reqflow.common.PageResult;
import com.cim.reqflow.common.R;
import com.cim.reqflow.common.constant.CommonConstants;
import com.cim.reqflow.ticket.dto.TicketActionDTO;
import com.cim.reqflow.ticket.dto.TicketCreateDTO;
import com.cim.reqflow.ticket.dto.TicketDocSaveDTO;
import com.cim.reqflow.ticket.dto.TicketQueryDTO;
import com.cim.reqflow.ticket.service.FlowStepService;
import com.cim.reqflow.ticket.service.TicketCreateService;
import com.cim.reqflow.ticket.service.TicketDetailService;
import com.cim.reqflow.ticket.service.TicketDocService;
import com.cim.reqflow.ticket.service.TicketFlowService;
import com.cim.reqflow.ticket.service.TicketQueryService;
import com.cim.reqflow.ticket.service.TicketTodoService;
import com.cim.reqflow.ticket.vo.ActionResultVO;
import com.cim.reqflow.ticket.vo.ActionVO;
import com.cim.reqflow.ticket.vo.FlowStepVO;
import com.cim.reqflow.ticket.vo.TicketCreatedVO;
import com.cim.reqflow.ticket.vo.TicketDetailVO;
import com.cim.reqflow.ticket.vo.TicketDocVO;
import com.cim.reqflow.ticket.vo.TicketListVO;
import com.cim.reqflow.ticket.vo.TicketTodoVO;
import jakarta.servlet.http.HttpServletRequest;
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

    /** 需求文档读写 */
    private final TicketDocService ticketDocService;

    /** 我的待办 */
    private final TicketTodoService ticketTodoService;

    /** 步骤条数据：每步的人、材料、接手人 */
    private final FlowStepService flowStepService;

    public TicketController(TicketFlowService ticketFlowService,
                            TicketDetailService ticketDetailService,
                            TicketQueryService ticketQueryService,
                            TicketCreateService ticketCreateService,
                            TicketDocService ticketDocService,
                            TicketTodoService ticketTodoService,
                            FlowStepService flowStepService) {
        this.ticketFlowService = ticketFlowService;
        this.ticketDetailService = ticketDetailService;
        this.ticketQueryService = ticketQueryService;
        this.ticketCreateService = ticketCreateService;
        this.ticketDocService = ticketDocService;
        this.ticketTodoService = ticketTodoService;
        this.flowStepService = flowStepService;
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
     * 步骤条数据：十步各自的处理人、需要 / 已传 / 还缺的材料、交给谁。
     * <p>
     * 单独一个接口而不是塞进详情：详情页首屏要的是正文，这一步的数据只在鼠标悬停时才看，
     * 混在一起会让首屏多查四张表。前端 hover 首次触发时再拉，一次拉全十步。
     *
     * @param ticketId 单据 ID
     * @return 十个步骤
     */
    @GetMapping("/{ticketId}/flow-steps")
    public R<List<FlowStepVO>> flowSteps(@PathVariable String ticketId) {
        return R.ok(flowStepService.build(ticketId));
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
     * 我的待办：轮到我处理的单，按停留时长倒序。
     * <p>
     * 判定与 `/actions` 同源，前端不自己算「该不该我处理」。分两组返回：
     * `dueActions` 现在能点、`blockedActions` 要我补材料 —— 后者也进待办，
     * 否则我永远不知道有张单卡在等我传附件。
     *
     * @param ticketType 单据类型，可空表示全部
     * @param request    HTTP 请求，用于取当前登录用户
     * @return 待办列表，不分页
     */
    @GetMapping("/todo")
    public R<List<TicketTodoVO>> todo(@RequestParam(required = false) String ticketType,
                                      HttpServletRequest request) {
        return R.ok(ticketTodoService.list(ticketType, currentUserId(request)));
    }

    /**
     * 需求文档（12 节结构化 + 渲染好的 Markdown）。
     * <p>
     * 同时返回 `editable` 与 `missingSections`：前端据此决定能否编辑、
     * 哪几节标红。这两个判断涉及状态机与校验规则，不在前端重算。
     *
     * @param ticketId 单据 ID
     * @return 文档
     */
    @GetMapping("/{ticketId}/doc")
    public R<TicketDocVO> doc(@PathVariable String ticketId) {
        return R.ok(ticketDocService.get(ticketId));
    }

    /**
     * 保存人工编辑后的需求文档。仅提出人、仅「需求文档待确认」环节可改。
     * <p>
     * 正文由后端从 docJson 现算，请求体不需要带 Markdown。
     *
     * @param ticketId 单据 ID
     * @param dto      12 节结构化数据
     * @param request  HTTP 请求，用于取当前登录用户
     * @return 保存后的文档
     */
    @PutMapping("/{ticketId}/doc")
    public R<TicketDocVO> saveDoc(@PathVariable String ticketId,
                                  @RequestBody TicketDocSaveDTO dto,
                                  HttpServletRequest request) {
        return R.ok(ticketDocService.save(ticketId, dto, currentUserId(request)));
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
