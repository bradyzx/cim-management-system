package com.cim.reqflow.system.dto;

/**
 * 用户列表查询条件。
 * 只用包装类型、不设默认值：空值语义是「该条件不参与过滤」，默认值统一由 Service 兜底。
 *
 * @author Haijie Xiao
 * @date 2026-11-03
 */
public class UserQueryDTO {

    /** 页码，从 1 开始 */
    private Integer page;

    /** 每页条数 */
    private Integer pageSize;

    /** 关键字，同时匹配账号与姓名 */
    private String name;

    /** 用户 ID，精确匹配 */
    private String id;

    /** 主部门 ID */
    private String deptId;

    /** 状态 */
    private String status;

    /** 创建时间起，格式 yyyy-MM-dd HH:mm:ss */
    private String startTime;

    /** 创建时间止，格式 yyyy-MM-dd HH:mm:ss */
    private String endTime;

    /**
     * 输出关键字段，便于日志排查。
     *
     * @return 对象字符串
     */
    @Override
    public String toString() {
        return "UserQueryDTO{page=" + page
                + ", pageSize=" + pageSize
                + ", name=" + name
                + ", id=" + id
                + ", deptId=" + deptId
                + ", status=" + status
                + ", startTime=" + startTime
                + ", endTime=" + endTime + "}";
    }

    public Integer getPage() {
        return page;
    }

    public void setPage(Integer page) {
        this.page = page;
    }

    public Integer getPageSize() {
        return pageSize;
    }

    public void setPageSize(Integer pageSize) {
        this.pageSize = pageSize;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getDeptId() {
        return deptId;
    }

    public void setDeptId(String deptId) {
        this.deptId = deptId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getStartTime() {
        return startTime;
    }

    public void setStartTime(String startTime) {
        this.startTime = startTime;
    }

    public String getEndTime() {
        return endTime;
    }

    public void setEndTime(String endTime) {
        this.endTime = endTime;
    }
}
