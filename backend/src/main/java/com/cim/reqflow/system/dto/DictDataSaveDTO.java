package com.cim.reqflow.system.dto;

/**
 * 字典数据新增 / 修改请求。
 * 新增时 dictCode、label、value 必填；color 是前端语义色（success / error / warning）。
 *
 * @author Haijie Xiao
 * @date 2026-11-03
 */
public class DictDataSaveDTO {

    /** 所属字典编码 */
    private String dictCode;

    /** 显示标签 */
    private String label;

    /** 实际值 */
    private String value;

    /** 排序号 */
    private Integer sort;

    /** 状态 */
    private String status;

    /** 前端语义色 */
    private String color;

    /** 备注 */
    private String remark;

    /**
     * 输出关键字段，便于日志排查。
     *
     * @return 对象字符串
     */
    @Override
    public String toString() {
        return "DictDataSaveDTO{dictCode=" + dictCode
                + ", label=" + label
                + ", value=" + value
                + ", sort=" + sort
                + ", status=" + status
                + ", color=" + color
                + ", remark=" + remark + "}";
    }

    public String getDictCode() {
        return dictCode;
    }

    public void setDictCode(String dictCode) {
        this.dictCode = dictCode;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
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

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }
}
