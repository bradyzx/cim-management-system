package com.cim.reqflow.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 数据库初始化：首次启动时库是空的，自动执行建表脚本与种子数据。
 * 判据是 sys_user 表是否存在；已有库则跳过，绝不重复执行，
 * 因为重复执行会主键冲突，宁可报错也不能覆盖已有数据。
 * <p>
 * 必须用 ScriptUtils 而不是 JdbcTemplate.execute(String)：后者只执行第一条语句，
 * 几十张表只建得出来一张且不报错，这是实际踩过的坑。
 *
 * @author Haijie Xiao
 * @date 2026-11-03
 */
@Component
@Order(1)
public class SchemaInitRunner implements CommandLineRunner {

    /** 日志句柄 */
    private static final Logger LOGGER = LoggerFactory.getLogger(SchemaInitRunner.class);

    /** 判断系统表是否已存在 */
    private static final String SQL_CHECK_SYS_USER =
            "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='sys_user'";

    /** 读取 sys_user 列信息 */
    private static final String SQL_PRAGMA_SYS_USER = "PRAGMA table_info(sys_user)";

    /** 补 must_change_password 列 */
    private static final String SQL_ADD_MUST_CHANGE_PASSWORD =
            "ALTER TABLE sys_user ADD COLUMN must_change_password INTEGER NOT NULL DEFAULT 0";

    /** 列名：必须改密标志 */
    private static final String COLUMN_MUST_CHANGE_PASSWORD = "must_change_password";

    /** 建表脚本 */
    private static final String SCRIPT_SCHEMA = "db/schema-sqlite.sql";

    /** 种子数据脚本 */
    private static final String SCRIPT_SEED = "db/seed-sqlite.sql";

    /** 库类型关键字 */
    private static final String DB_PRODUCT_SQLITE = "sqlite";

    /** JDBC 模板，用于执行建表脚本与列增量升级 */
    private final JdbcTemplate jdbcTemplate;

    public SchemaInitRunner(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 启动时判断库是否已初始化：空库执行建表与种子数据，已有库只做增量列升级。
     *
     * @param args 启动参数
     * @throws Exception 执行脚本失败
     */
    @Override
    public void run(String... args) throws Exception {
        Integer tableCount = jdbcTemplate.queryForObject(SQL_CHECK_SYS_USER, Integer.class);
        if (tableCount != null && tableCount > 0) {
            LOGGER.info("数据库已初始化，跳过建表与种子数据");
            upgradeExistingSchema();
            return;
        }

        LOGGER.info("检测到空库，开始执行建表脚本与种子数据");
        long startMillis = System.currentTimeMillis();
        try (Connection connection = Objects.requireNonNull(jdbcTemplate.getDataSource())
                .getConnection()) {
            ScriptUtils.executeSqlScript(connection, new EncodedResource(
                    new ClassPathResource(SCRIPT_SCHEMA), StandardCharsets.UTF_8));
            ScriptUtils.executeSqlScript(connection, new EncodedResource(
                    new ClassPathResource(SCRIPT_SEED), StandardCharsets.UTF_8));
        }
        LOGGER.info("建表与种子数据完成，耗时 {} ms", System.currentTimeMillis() - startMillis);
    }

    /**
     * 对已有库做增量升级。只做「加列」这类幂等操作：先读表结构判断列是否存在，缺了才 ALTER，
     * 绝不重复执行、绝不改已有数据。非 SQLite 库（Oracle 尚未上线）直接跳过。
     */
    private void upgradeExistingSchema() {
        String databaseProduct;
        try (Connection connection = Objects.requireNonNull(jdbcTemplate.getDataSource())
                .getConnection()) {
            databaseProduct = connection.getMetaData().getDatabaseProductName();
        } catch (SQLException e) {
            LOGGER.warn("读取数据库类型失败，跳过列升级检查", e);
            return;
        }
        if (databaseProduct == null
                || !databaseProduct.toLowerCase().contains(DB_PRODUCT_SQLITE)) {
            return;
        }
        if (hasSysUserColumn(COLUMN_MUST_CHANGE_PASSWORD)) {
            return;
        }
        LOGGER.info("检测到 sys_user 缺少 {} 列，开始补列", COLUMN_MUST_CHANGE_PASSWORD);
        jdbcTemplate.execute(SQL_ADD_MUST_CHANGE_PASSWORD);
        LOGGER.info("补列完成");
    }

    /**
     * 判断 sys_user 是否已有指定列。SQLite 用 pragma table_info 读取列清单。
     *
     * @param columnName 列名
     * @return true 表示已存在
     */
    private boolean hasSysUserColumn(String columnName) {
        List<Map<String, Object>> columns = jdbcTemplate.queryForList(SQL_PRAGMA_SYS_USER);
        for (Map<String, Object> column : columns) {
            Object name = column.get("name");
            if (name != null && columnName.equalsIgnoreCase(String.valueOf(name))) {
                return true;
            }
        }
        return false;
    }
}
