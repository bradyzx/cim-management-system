package com.cim.reqflow.common.enums;

/**
 * 通用启停状态。用户、角色、菜单、字典、部门共用这一套编码。
 *
 * @author Haijie Xiao
 * @date 2026-11-04
 */
public enum RecordStatusEnum {

    /** 启用：正常参与业务 */
    ACTIVE("ACTIVE", "启用"),

    /** 停用：等价于软删除，历史数据引用不断 */
    DISABLED("DISABLED", "停用");

    /** 落库编码 */
    private final String code;

    /** 中文说明 */
    private final String description;

    RecordStatusEnum(String code, String description) {
        this.code = code;
        this.description = description;
    }

    /**
     * 按落库编码查枚举。
     *
     * @param code 落库编码
     * @return 匹配的枚举，非法编码返回 null
     */
    public static RecordStatusEnum of(String code) {
        for (RecordStatusEnum item : values()) {
            if (item.code.equals(code)) {
                return item;
            }
        }
        return null;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }
}
