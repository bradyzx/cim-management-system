package com.cim.reqflow.auth;

import com.cim.reqflow.auth.dto.ChangePasswordDTO;
import com.cim.reqflow.auth.dto.LoginDTO;
import com.cim.reqflow.auth.vo.LoginVO;
import com.cim.reqflow.common.BizException;
import com.cim.reqflow.common.R;
import com.cim.reqflow.common.constant.CommonConstants;
import com.cim.reqflow.common.constant.SystemConstants;
import com.cim.reqflow.common.enums.RecordStatusEnum;
import com.cim.reqflow.system.entity.SysUserDO;
import com.cim.reqflow.system.mapper.SysUserMapper;
import com.cim.reqflow.system.service.PasswordService;
import com.cim.reqflow.system.service.UserAccessService;
import com.cim.reqflow.system.vo.UserInfoVO;
import io.jsonwebtoken.Claims;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 认证接口：登录、刷新、退出、权限码、自助改密。
 * 两个最容易写错的契约点：
 * 1. /auth/refresh 返回的 data 是裸字符串，不是 { accessToken: "..." } 对象；
 * 2. 登录失败返回 HTTP 403 + code = -1，不是 HTTP 200。
 *
 * @author Haijie Xiao
 * @date 2026-11-03
 */
@RestController
@RequestMapping("/auth")
public class AuthController {

    /** Cookie 有效期换算：1 小时秒数 */
    private static final long SECONDS_PER_HOUR = 3600L;

    /** 清理 Cookie 时使用的有效期 */
    private static final long COOKIE_EXPIRED_MAX_AGE = 0L;

    /** 令牌签发与解析服务 */
    private final JwtService jwtService;
    /** 用户权限解析服务 */
    private final UserAccessService userAccessService;
    /** 用户表数据访问 */
    private final SysUserMapper sysUserMapper;
    /** 口令散列与改密服务 */
    private final PasswordService passwordService;

    /** 刷新令牌有效期（小时） */
    @Value("${cim.jwt.refresh-hours}")
    private long refreshHours;

    /** 是否给 Cookie 加 Secure 标记。本地 http 环境必须关闭，生产 HTTPS 必须开启 */
    @Value("${cim.jwt.cookie-secure:false}")
    private boolean cookieSecure;

    public AuthController(JwtService jwtService, UserAccessService userAccessService,
                          SysUserMapper sysUserMapper, PasswordService passwordService) {
        this.jwtService = jwtService;
        this.userAccessService = userAccessService;
        this.sysUserMapper = sysUserMapper;
        this.passwordService = passwordService;
    }

    /**
     * 账号口令登录。成功后下发访问令牌并写入 HttpOnly 刷新 Cookie。
     *
     * @param loginRequest 登录请求
     * @return 登录返回体，附带 Set-Cookie
     */
    @PostMapping("/login")
    public ResponseEntity<R<LoginVO>> login(@RequestBody LoginDTO loginRequest) {
        String username = loginRequest == null ? null : loginRequest.getUsername();
        String password = loginRequest == null ? null : loginRequest.getPassword();
        if (isBlank(username) || isBlank(password)) {
            throw new BizException("username and password are required",
                    BizException.STATUS_PARAM_ERROR);
        }

        SysUserDO user = userAccessService.getUserByUsername(username);
        if (user == null) {
            throw new BizException("用户名或密码错误", BizException.STATUS_FORBIDDEN);
        }
        if (!RecordStatusEnum.ACTIVE.getCode().equals(user.getStatus())) {
            throw new BizException("账号已停用或锁定，请联系管理员", BizException.STATUS_FORBIDDEN);
        }
        // 域账号没有本地口令哈希，AD 登录一期未接入
        if (!SystemConstants.AUTH_TYPE_LOCAL.equals(user.getAuthType())
                || user.getPasswordHash() == null) {
            throw new BizException("该账号为域账号，AD 登录暂未接入", BizException.STATUS_FORBIDDEN);
        }
        if (!passwordService.matches(password, user.getPasswordHash())) {
            throw new BizException("用户名或密码错误", BizException.STATUS_FORBIDDEN);
        }

        String accessToken = jwtService.createAccessToken(user.getId(), user.getUsername());
        String refreshToken = jwtService.createRefreshToken(user.getId(), user.getUsername());

        UserInfoVO userInfo = userAccessService.getUserInfo(user.getId());
        // 首次登录或被管理员重置过口令时前端必须弹不可关闭的改密框
        Boolean needChangePassword =
                SystemConstants.FLAG_YES.equals(user.getMustChangePassword());
        LoginVO loginVO = LoginVO.of(userInfo, accessToken, needChangePassword);

        touchLastLoginAt(user.getId());

        ResponseCookie cookie = buildRefreshCookie(refreshToken, refreshHours * SECONDS_PER_HOUR);
        return ResponseEntity.ok()
                .header("Set-Cookie", cookie.toString())
                .body(R.ok(loginVO));
    }

    /**
     * 刷新访问令牌。刷新令牌从 HttpOnly Cookie 读取，不经过前端 JS。
     *
     * @param refreshToken 刷新令牌
     * @return data 为新的访问令牌字符串
     */
    @PostMapping("/refresh")
    public R<String> refresh(
            @CookieValue(value = CommonConstants.REFRESH_COOKIE_NAME, required = false)
            String refreshToken) {
        if (isBlank(refreshToken)) {
            throw new BizException("刷新令牌缺失", BizException.STATUS_UNAUTHORIZED);
        }

        Claims claims;
        try {
            claims = jwtService.parseRefreshToken(refreshToken);
        } catch (RuntimeException e) {
            // 过期、签名不符、格式错误都视为不可用，前端收到 401 后回登录页
            throw new BizException("刷新令牌已过期，请重新登录", BizException.STATUS_UNAUTHORIZED);
        }
        String userId = claims.getSubject();
        String username = claims.get("username", String.class);
        // 契约要求 data 直接是字符串，套一层对象前端会解析失败
        return R.ok(jwtService.createAccessToken(userId, username));
    }

    /**
     * 退出登录：下发同名空值且立即过期的 Cookie，浏览器随即覆盖旧值。
     *
     * @return 空数据响应
     */
    @PostMapping("/logout")
    public ResponseEntity<R<String>> logout() {
        ResponseCookie cookie = buildRefreshCookie("", COOKIE_EXPIRED_MAX_AGE);
        return ResponseEntity.ok()
                .header("Set-Cookie", cookie.toString())
                .body(R.ok(""));
    }

    /**
     * 当前登录人的按钮权限码，前端用于按钮级显隐。
     *
     * @param userId 当前用户 ID，由拦截器写入
     * @return 权限码列表
     */
    @GetMapping("/codes")
    public R<List<String>> listAuthCodes(
            @RequestAttribute(CommonConstants.ATTR_CURRENT_USER_ID) String userId) {
        return R.ok(userAccessService.listAuthCodes(userId));
    }

    /**
     * 自助改密。首次登录时前端拿到 needChangePassword = true 后必须先走这里。
     *
     * @param userId              当前用户 ID
     * @param changePasswordRequest 改密请求
     * @return 空数据响应
     */
    @PostMapping("/change-password")
    public R<Void> changePassword(
            @RequestAttribute(CommonConstants.ATTR_CURRENT_USER_ID) String userId,
            @RequestBody ChangePasswordDTO changePasswordRequest) {
        String oldPassword =
                changePasswordRequest == null ? null : changePasswordRequest.getOldPassword();
        String newPassword =
                changePasswordRequest == null ? null : changePasswordRequest.getNewPassword();
        passwordService.changePassword(userId, oldPassword, newPassword);
        return R.ok();
    }

    /**
     * 只更新登录时间一个字段。selectById 后整行回写会覆盖并发修改，所以单独构造更新对象。
     *
     * @param userId 用户 ID
     */
    private void touchLastLoginAt(String userId) {
        SysUserDO updateRow = new SysUserDO();
        updateRow.setId(userId);
        updateRow.setLastLoginAt(LocalDateTime.now().format(CommonConstants.DATE_TIME_FORMATTER));
        sysUserMapper.updateById(updateRow);
    }

    /**
     * 构造刷新令牌 Cookie。HttpOnly 防 XSS 读取，SameSite=Lax 兼顾跨站刷新。
     *
     * @param value      Cookie 值
     * @param maxAgeSecs 有效期（秒），0 表示立即失效
     * @return Cookie 对象
     */
    private ResponseCookie buildRefreshCookie(String value, long maxAgeSecs) {
        return ResponseCookie.from(CommonConstants.REFRESH_COOKIE_NAME, value)
                .httpOnly(true)
                .path("/")
                .maxAge(maxAgeSecs)
                .sameSite("Lax")
                .secure(cookieSecure)
                .build();
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
