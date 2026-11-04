package com.cim.reqflow.ticket.vo;

/**
 * 下拉选项：项目或系统。
 * <p>
 * 两个接口共用一个形状：系统比项目多一个 projectId，
 * 前端据此做「选了项目再筛系统」的两级联动 ——
 * 项目 → 系统 → 需求 的层级归属从提单那一刻就落死。
 *
 * @author Haijie Xiao
 * @date 2026-11-04
 */
public class BasicOptionVO {

    /** ID，提单时作为 projectId / systemId 提交 */
    private String id;

    /** 编码：项目编码或系统编码 */
    private String code;

    /** 名称 */
    private String name;

    /** 所属项目（仅系统选项有），联动筛选用 */
    private String projectId;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getProjectId() {
        return projectId;
    }

    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }

    /**
     * 只输出编码与名称。
     *
     * @return 关键字段字符串
     */
    @Override
    public String toString() {
        return "BasicOptionVO{code=" + code + ", name=" + name + "}";
    }
}
