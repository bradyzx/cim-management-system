package com.cim.reqflow.common.util;

import java.util.UUID;

/**
 * 主键生成工具。
 * 全系统主键统一为 32 位无连字符 UUID、由应用层生成，不用数据库自增序列，
 * 目的是换库（SQLite 转 Oracle）时主键策略不变、数据迁移不用重编主键。
 *
 * @author Haijie Xiao
 * @date 2026-11-03
 */
public final class IdUtils {

    /**
     * 生成 32 位无连字符的 UUID 主键。
     *
     * @return 32 位十六进制字符串
     */
    public static String nextId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 工具类禁止实例化。
     */
    private IdUtils() {
    }
}
