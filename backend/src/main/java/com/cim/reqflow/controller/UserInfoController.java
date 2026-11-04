package com.cim.reqflow.controller;

import com.cim.reqflow.common.R;
import com.cim.reqflow.common.constant.CommonConstants;
import com.cim.reqflow.system.service.UserAccessService;
import com.cim.reqflow.system.vo.UserInfoVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RestController;

/**
 * 当前登录人信息。
 *
 * @author Haijie Xiao
 * @date 2026-11-03
 */
@RestController
public class UserInfoController {

    /** 用户权限解析服务 */
    private final UserAccessService userAccessService;

    public UserInfoController(UserAccessService userAccessService) {
        this.userAccessService = userAccessService;
    }

    /**
     * 查询当前登录人信息。
     *
     * @param userId 当前用户 ID，由拦截器写入
     * @return 用户信息
     */
    @GetMapping("/user/info")
    public R<UserInfoVO> currentUser(
            @RequestAttribute(CommonConstants.ATTR_CURRENT_USER_ID) String userId) {
        return R.ok(userAccessService.getUserInfo(userId));
    }
}
