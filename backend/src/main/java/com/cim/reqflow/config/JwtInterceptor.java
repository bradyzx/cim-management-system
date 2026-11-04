package com.cim.reqflow.config;

import com.cim.reqflow.auth.JwtService;
import com.cim.reqflow.common.BizException;
import com.cim.reqflow.common.R;
import com.cim.reqflow.common.constant.CommonConstants;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;

/**
 * JWT 登录拦截器。
 * 不引入 Spring Security 过滤链：本系统只有一条规则「带合法 accessToken 才放行」，
 * 一个拦截器写清楚比整套 SecurityConfig 更容易读懂和维护。
 *
 * @author Haijie Xiao
 * @date 2026-11-03
 */
@Component
public class JwtInterceptor implements HandlerInterceptor {

    /** 跨域预检请求方法名，此类请求不带自定义头，必须放行 */
    private static final String METHOD_OPTIONS = "OPTIONS";

    /** 令牌签发与解析服务 */
    private final JwtService jwtService;
    /** JSON 序列化器，用于手工输出响应体 */
    private final ObjectMapper objectMapper;

    public JwtInterceptor(JwtService jwtService, ObjectMapper objectMapper) {
        this.jwtService = jwtService;
        this.objectMapper = objectMapper;
    }

    /**
     * 校验访问令牌，通过后把当前用户写入请求域，供 Controller 直接取用。
     *
     * @param request  HTTP 请求
     * @param response HTTP 响应
     * @param handler  处理器
     * @return true 放行，false 拦截
     * @throws IOException 写响应体失败
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                             Object handler) throws IOException {
        // 跨域预检请求不带自定义请求头，必须放行，否则前端联调第一步就被卡死
        if (METHOD_OPTIONS.equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String header = request.getHeader(CommonConstants.TOKEN_HEADER);
        if (header == null || !header.startsWith(CommonConstants.TOKEN_PREFIX)) {
            writeUnauthorized(response, "未登录或登录已过期");
            return false;
        }

        String token = header.substring(CommonConstants.TOKEN_PREFIX.length());
        try {
            Claims claims = jwtService.parseAccessToken(token);
            request.setAttribute(CommonConstants.ATTR_CURRENT_USER_ID, claims.getSubject());
            request.setAttribute(CommonConstants.ATTR_CURRENT_USERNAME,
                    claims.get("username", String.class));
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            // 令牌过期、签名不符、格式错误都归为未授权，前端收到 401 后自行走刷新流程
            writeUnauthorized(response, "登录状态无效，请重新登录");
            return false;
        }
    }

    /**
     * 输出 401 响应，响应体仍使用统一结构，前端无需为拦截器单独写解析逻辑。
     *
     * @param response HTTP 响应
     * @param message  提示语
     * @throws IOException 写响应体失败
     */
    private void writeUnauthorized(HttpServletResponse response, String message)
            throws IOException {
        response.setStatus(BizException.STATUS_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(R.fail(message)));
    }
}
