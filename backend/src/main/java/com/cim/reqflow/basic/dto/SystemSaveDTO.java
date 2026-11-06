package com.cim.reqflow.basic.dto;

/**
 * 系统新增 / 修改请求。
 * <p>
 * projectId 与 systemCode 只在新增时有意义。系统只属一个项目，改归属会让
 * 历史单据快照的 project_id 与实际归属对不上；编码改动则会让按编码汇总的报表分裂成两行。
 * 修改接口收到不同的值会直接拒绝，要换归属只能新建系统。
 *
 * @author Haijie Xiao
 * @date 2026-11-05
 */
public class SystemSaveDTO {

    /** 所属项目 ID，仅新增时生效 */
    private String projectId;

    /** 系统编码，项目内唯一，如 MES / EAP */
    private String systemCode;

    /** 系统名称 */
    private String systemName;

    /** 系统类型：PURCHASED 外购 / INHOUSE 自研 / RUNNING 在运 */
    private String systemType;

    /** 系统负责人用户 ID */
    private String ownerId;

    /** 状态：BUILDING 建设中 / LIVE 在运 / OFFLINE 已下线 */
    private String status;

    /** 排序号，越小越靠前 */
    private Integer sortNo;

    /** 备注 */
    private String remark;

    /**
     * 输出关键字段，便于日志排查。
     *
     * @return 对象字符串
     */
    @Override
    public String toString() {
        return "SystemSaveDTO{projectId=" + projectId
                + ", systemCode=" + systemCode
                + ", systemName=" + systemName
                + ", systemType=" + systemType
                + ", ownerId=" + ownerId
                + ", status=" + status
                + ", sortNo=" + sortNo + "}";
    }

    public String getProjectId() {
        return projectId;
    }

    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }

    public String getSystemCode() {
        return systemCode;
    }

    public void setSystemCode(String systemCode) {
        this.systemCode = systemCode;
    }

    public String getSystemName() {
        return systemName;
    }

    public void setSystemName(String systemName) {
        this.systemName = systemName;
    }

    public String getSystemType() {
        return systemType;
    }

    public void setSystemType(String systemType) {
        this.systemType = systemType;
    }

    public String getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(String ownerId) {
        this.ownerId = ownerId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getSortNo() {
        return sortNo;
    }

    public void setSortNo(Integer sortNo) {
        this.sortNo = sortNo;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }
}
