package com.cim.reqflow.system.dto;

/**
 * 部门新增 / 修改请求。
 * 新增时 deptCode 与 deptName 必填；修改不支持改上级部门，
 * 因为改父级要重算整棵子树的 ancestors 与层级，容易写错，等真有需求再做。
 *
 * @author Haijie Xiao
 * @date 2026-11-03
 */
public class DeptSaveDTO {

    /** 部门编码 */
    private String deptCode;

    /** 部门名称 */
    private String deptName;

    /** 上级部门 ID，仅新增时生效 */
    private String parentId;

    /** 部门负责人用户 ID */
    private String leaderUserId;

    /** 排序号 */
    private Integer sort;

    /** 状态 */
    private String status;

    /** 备注 */
    private String remark;

    /**
     * 输出关键字段，便于日志排查。
     *
     * @return 对象字符串
     */
    @Override
    public String toString() {
        return "DeptSaveDTO{deptCode=" + deptCode
                + ", deptName=" + deptName
                + ", parentId=" + parentId
                + ", leaderUserId=" + leaderUserId
                + ", sort=" + sort
                + ", status=" + status
                + ", remark=" + remark + "}";
    }

    public String getDeptCode() {
        return deptCode;
    }

    public void setDeptCode(String deptCode) {
        this.deptCode = deptCode;
    }

    public String getDeptName() {
        return deptName;
    }

    public void setDeptName(String deptName) {
        this.deptName = deptName;
    }

    public String getParentId() {
        return parentId;
    }

    public void setParentId(String parentId) {
        this.parentId = parentId;
    }

    public String getLeaderUserId() {
        return leaderUserId;
    }

    public void setLeaderUserId(String leaderUserId) {
        this.leaderUserId = leaderUserId;
    }

    public Integer getSort() {
        return sort;
    }

    public void setSort(Integer sort) {
        this.sort = sort;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }
}
