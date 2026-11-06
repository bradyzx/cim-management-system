package com.cim.reqflow.ticket.controller;

import com.cim.reqflow.common.R;
import com.cim.reqflow.common.constant.CommonConstants;
import com.cim.reqflow.ticket.dto.CheckItemSaveDTO;
import com.cim.reqflow.ticket.service.CheckItemService;
import com.cim.reqflow.ticket.vo.CheckItemListVO;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 上线点检接口（契约 `11` 第十章）。
 * <p>
 * 与测试用例一样不设类级前缀：清单挂在单据下，登记挂在点检项自己身上。
 *
 * @author Haijie Xiao
 * @date 2026-11-05
 */
@RestController
public class CheckItemController {

    private final CheckItemService checkItemService;

    public CheckItemController(CheckItemService checkItemService) {
        this.checkItemService = checkItemService;
    }

    /**
     * 点检清单。`empty=1` 表示未配置，此时允许直接点检通过。
     *
     * @param ticketId 单据 ID
     * @return 清单
     */
    @GetMapping("/ticket/{ticketId}/check-items")
    public R<CheckItemListVO> list(@PathVariable String ticketId) {
        return R.ok(checkItemService.list(ticketId));
    }

    /**
     * 从需求文档第 5 节功能清单生成草稿。已有点检项时拒绝，避免冲掉人工补充。
     *
     * @param ticketId 单据 ID
     * @param request  HTTP 请求，用于取当前登录用户
     * @return 生成后的清单
     */
    @PostMapping("/ticket/{ticketId}/check-items/from-doc")
    public R<CheckItemListVO> fromDoc(@PathVariable String ticketId,
                                      HttpServletRequest request) {
        return R.ok(checkItemService.fromDoc(ticketId, currentUserId(request)));
    }

    /**
     * 单项登记。不通过必须填写说明。
     *
     * @param itemId  点检项 ID
     * @param dto     结果与说明
     * @param request HTTP 请求，用于取当前登录用户
     * @return 更新后的清单
     */
    @PutMapping("/check-items/{itemId}")
    public R<CheckItemListVO> update(@PathVariable String itemId,
                                     @RequestBody CheckItemSaveDTO dto,
                                     HttpServletRequest request) {
        return R.ok(checkItemService.update(itemId, dto, currentUserId(request)));
    }

    /**
     * 从请求域取当前登录用户 ID。
     *
     * @param request HTTP 请求
     * @return 用户 ID
     */
    private String currentUserId(HttpServletRequest request) {
        return (String) request.getAttribute(CommonConstants.ATTR_CURRENT_USER_ID);
    }
}
