package com.cim.reqflow;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 需求全生命周期管理系统后端入口。
 * 包结构按技术方案 7.2 节划分：common / config / auth / system，后续业务模块按
 * ticket / analysis / notify / report 平级扩展。
 *
 * @author Haijie Xiao
 * @date 2026-11-03
 */
@SpringBootApplication
@MapperScan({"com.cim.reqflow.system.mapper", "com.cim.reqflow.ticket.mapper"})
public class ReqFlowApplication {

    /**
     * 应用启动入口。
     *
     * @param args 启动参数
     */
    public static void main(String[] args) {
        SpringApplication.run(ReqFlowApplication.class, args);
    }
}
