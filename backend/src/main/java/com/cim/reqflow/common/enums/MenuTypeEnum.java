package com.cim.reqflow.common.enums;

/**
 * 菜单类型。
 * 取值集合固定（数据库与前端共用同一批编码），因此按手册建议用枚举而非常量类维护。
 * 只有 DIR 与 MENU 会下发到前端路由树，BUTTON 仅作权限码使用。
 *
 * @author Haijie Xiao
 * @date 2026-11-04
 */
public enum MenuTypeEnum {

    /** 目录：只做分组，本身不对应页面 */
    DIR("DIR", "目录"),

    /** 菜单：对应一个可访问页面 */
    MENU("MENU", "菜单"),

    /** 按钮：只提供权限码，没有路由 */
    BUTTON("BUTTON", "按钮"),

    /** 内嵌页：以外嵌 iframe 方式展示 */
    EMBED("EMBED", "内嵌页"),

    /** 外链：跳转到外部地址 */
    LINK("LINK", "外链");

    /** 落库编码 */
    private final String code;

    /** 中文说明 */
    private final String description;

    MenuTypeEnum(String code, String description) {
        this.code = code;
        this.description = description;
    }

    /**
     * 按落库编码查枚举。
     *
     * @param code 落库编码
     * @return 匹配的枚举，非法编码返回 null
     */
    public static MenuTypeEnum of(String code) {
        for (MenuTypeEnum item : values()) {
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
