package com.cim.reqflow.system.vo;

/**
 * 角色授权树用的菜单节点。
 * 与路由树不同：这里必须带菜单 ID 做勾选键，且平铺返回，树由前端自行组装。
 *
 * @author Haijie Xiao
 * @date 2026-11-03
 */
public class MenuAuthNodeVO {

    /** 菜单 ID */
    private String id;

    /** 上级菜单 ID */
    private String parentId;

    /** 菜单名称 */
    private String menuName;

    /** 菜单类型 */
    private String menuType;

    /** 权限码 */
    private String authCode;

    /** 排序号 */
    private Integer sort;

    /** 状态 */
    private String status;

    /**
     * 输出关键字段，便于日志排查。
     *
     * @return 对象字符串
     */
    @Override
    public String toString() {
        return "MenuAuthNodeVO{id=" + id
                + ", parentId=" + parentId
                + ", menuName=" + menuName
                + ", menuType=" + menuType
                + ", authCode=" + authCode
                + ", sort=" + sort
                + ", status=" + status + "}";
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getParentId() {
        return parentId;
    }

    public void setParentId(String parentId) {
        this.parentId = parentId;
    }

    public String getMenuName() {
        return menuName;
    }

    public void setMenuName(String menuName) {
        this.menuName = menuName;
    }

    public String getMenuType() {
        return menuType;
    }

    public void setMenuType(String menuType) {
        this.menuType = menuType;
    }

    public String getAuthCode() {
        return authCode;
    }

    public void setAuthCode(String authCode) {
        this.authCode = authCode;
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
}
