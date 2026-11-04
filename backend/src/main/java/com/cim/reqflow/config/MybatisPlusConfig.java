package com.cim.reqflow.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.cim.reqflow.common.constant.CommonConstants;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 配置。
 * 分页方言固定为 SQLite；将来换 Oracle 只需把 DbType 改成 ORACLE，
 * 分页语法（ROWNUM / FETCH FIRST）由插件自动切换，这也是选 MyBatis-Plus 的核心理由。
 *
 * @author Haijie Xiao
 * @date 2026-11-03
 */
@Configuration
public class MybatisPlusConfig {

    /**
     * 注册分页插件并设置单页上限。
     *
     * @return MyBatis-Plus 拦截器链
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        PaginationInnerInterceptor pagination = new PaginationInnerInterceptor(DbType.SQLITE);
        pagination.setMaxLimit(CommonConstants.MAX_PAGE_SIZE);
        interceptor.addInnerInterceptor(pagination);
        return interceptor;
    }
}
