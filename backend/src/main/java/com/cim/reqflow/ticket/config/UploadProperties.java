package com.cim.reqflow.ticket.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 附件上传配置。
 * <p>
 * 上传根目录从 application.yml 的 {@code cim.upload-dir} 读取，不用类路径：
 * 打成 jar 后类路径是只读的，附件必须落在外部目录。
 * 库里只存相对路径，根目录换机器只改一处配置。
 *
 * @author Haijie Xiao
 * @date 2026-11-04
 */
@Component
@ConfigurationProperties(prefix = "cim")
public class UploadProperties {

    /** 单文件上限（MB）。超过直接拒绝，不做截断 —— 截断的文件等于损坏的证据 */
    private static final int DEFAULT_MAX_SIZE_MB = 50;

    /** 附件存放根目录，相对路径的起点 */
    private String uploadDir = "./data/attachments";

    /** 单文件大小上限，单位 MB */
    private Integer uploadMaxMb = DEFAULT_MAX_SIZE_MB;

    public String getUploadDir() {
        return uploadDir;
    }

    public void setUploadDir(String uploadDir) {
        this.uploadDir = uploadDir;
    }

    public Integer getUploadMaxMb() {
        return uploadMaxMb == null ? DEFAULT_MAX_SIZE_MB : uploadMaxMb;
    }

    public void setUploadMaxMb(Integer uploadMaxMb) {
        this.uploadMaxMb = uploadMaxMb;
    }

    /**
     * 输出配置值，排查「文件存哪去了」时先看这一行。
     *
     * @return 关键字段字符串
     */
    @Override
    public String toString() {
        return "UploadProperties{uploadDir=" + uploadDir + ", uploadMaxMb=" + getUploadMaxMb() + "}";
    }
}
