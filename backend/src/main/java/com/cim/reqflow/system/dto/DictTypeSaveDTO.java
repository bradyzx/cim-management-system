package com.cim.reqflow.system.dto;

/**
 * 字典类型新增 / 修改请求。
 * 新增时 code 与 name 必填；修改编码会级联更新其下全部字典项。
 *
 * @author Haijie Xiao
 * @date 2026-11-03
 */
public class DictTypeSaveDTO {

    /** 字典编码 */
    private String code;

    /** 字典名称 */
    private String name;

    /** 备注 */
    private String remark;

    /** 状态 */
    private String status;

    /**
     * 输出关键字段，便于日志排查。
     *
     * @return 对象字符串
     */
    @Override
    public String toString() {
        return "DictTypeSaveDTO{code=" + code
                + ", name=" + name
                + ", remark=" + remark
                + ", status=" + status + "}";
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

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
