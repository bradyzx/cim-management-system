package com.cim.reqflow;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 需求全生命周期管理系统后端入口。
 * 包结构按技术方案 7.2 节划分：common / config / auth / system，后续业务模块按
 * ticket / analysis / notify / report 平级扩展。
 * <p>
 * `@EnableScheduling`：上线后「满 7 天自动转待评价」没有人工按钮，只能靠定时器
 * （`02` 里 N26 是唯一没人操作的节点）。
 *
 * @author Haijie Xiao
 * @date 2026-11-03
 */
@SpringBootApplication
@EnableScheduling
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
