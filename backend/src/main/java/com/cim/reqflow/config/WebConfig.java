package com.cim.reqflow.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web 配置：跨域策略与登录拦截器注册。
 *
 * @author Haijie Xiao
 * @date 2026-11-03
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    /** 预检结果缓存秒数 */
    private static final long CORS_MAX_AGE_SECONDS = 3600L;

    /** 登录拦截器 */
    private final JwtInterceptor jwtInterceptor;

    public WebConfig(JwtInterceptor jwtInterceptor) {
        this.jwtInterceptor = jwtInterceptor;
    }

    /**
     * 注册登录拦截器。登录与刷新必须放行：刷新走 Cookie，没有访问令牌可验。
     *
     * @param registry 拦截器注册表
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(jwtInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns("/auth/login", "/auth/refresh");
    }

    /**
     * 跨域配置。允许携带凭证，否则 /auth/refresh 拿不到 jwt Cookie。
     *
     * @param registry 跨域注册表
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(CORS_MAX_AGE_SECONDS);
    }
}
