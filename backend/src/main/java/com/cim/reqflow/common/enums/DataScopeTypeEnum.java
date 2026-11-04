package com.cim.reqflow.common.enums;

/**
 * 数据范围类型。一条数据范围规则由「角色 × 单据类型 × 范围类型」构成，
 * 取值范围固定且与前端下拉框一一对应，因此用枚举约束。
 *
 * @author Haijie Xiao
 * @date 2026-11-04
 */
public enum DataScopeTypeEnum {

    /** 仅本人：只能看自己提的单据 */
    SELF("SELF", "仅本人"),

    /** 仅指派给我：只能看当前流转到自己手上的单据 */
    ASSIGNED("ASSIGNED", "仅指派给我"),

    /** 用户组：按用户组维度可见 */
    GROUP("GROUP", "用户组"),

    /** 本部门：本部门内可见 */
    DEPT("DEPT", "本部门"),

    /** 本部门及下级：含整棵部门子树 */
    DEPT_AND_SUB("DEPT_AND_SUB", "本部门及下级"),

    /** 全部：不限制 */
    ALL("ALL", "全部"),

    /** 自定义：可见范围由 extraJson 描述 */
    CUSTOM("CUSTOM", "自定义"),

    /** 无权限：显式禁止访问 */
    NONE("NONE", "无权限");

    /** 落库编码 */
    private final String code;

    /** 中文说明 */
    private final String description;

    DataScopeTypeEnum(String code, String description) {
        this.code = code;
        this.description = description;
    }

    /**
     * 按落库编码查枚举。
     *
     * @param code 落库编码
     * @return 匹配的枚举，非法编码返回 null
     */
    public static DataScopeTypeEnum of(String code) {
        for (DataScopeTypeEnum item : values()) {
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
