package com.cim.reqflow.system.vo;

/**
 * 字典项。前端下拉框、标签渲染的统一结构，字段名与前端契约一致（label / value / color）。
 *
 * @author Haijie Xiao
 * @date 2026-11-03
 */
public class DictItemVO {

    /** 显示标签 */
    private String label;

    /** 实际值 */
    private String value;

    /** 前端语义色，可为空 */
    private String color;

    public DictItemVO() {
    }

    public DictItemVO(String label, String value, String color) {
        this.label = label;
        this.value = value;
        this.color = color;
    }

    /**
     * 输出关键字段，便于日志排查。
     *
     * @return 对象字符串
     */
    @Override
    public String toString() {
        return "DictItemVO{label=" + label
                + ", value=" + value
                + ", color=" + color + "}";
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

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }
}
