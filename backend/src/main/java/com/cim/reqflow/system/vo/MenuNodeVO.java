package com.cim.reqflow.system.vo;

import java.util.ArrayList;
import java.util.List;

/**
 * 路由树节点，结构与前端契约一致：
 * { name, path, component, redirect, meta: { title, icon, order, keepAlive }, children }
 * <p>
 * name 是路由名，必须全局唯一，由 path 推导（如 /req/todo 推导为 ReqTodo）。
 * children 为空时保持 null 而不给空数组：前端靠 children 是否为 null 判断叶子节点。
 *
 * @author Haijie Xiao
 * @date 2026-11-03
 */
public class MenuNodeVO {

    /** 路由名，全局唯一 */
    private String name;

    /** 路由路径 */
    private String path;

    /** 前端组件路径 */
    private String component;

    /** 重定向路径，目录节点指向第一个子页面 */
    private String redirect;

    /** 路由元信息 */
    private Meta meta;

    /** 子节点，叶子节点为 null */
    private List<MenuNodeVO> children;

    /**
     * 路由元信息。
     */
    public static class Meta {

        /** 菜单标题 */
        private String title;

        /** 图标 */
        private String icon;

        /** 排序号 */
        private Integer order;

        /** 是否缓存页面 */
        private Boolean keepAlive;

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getIcon() {
            return icon;
        }

        public void setIcon(String icon) {
            this.icon = icon;
        }

        public Integer getOrder() {
            return order;
        }

        public void setOrder(Integer order) {
            this.order = order;
        }

        public Boolean getKeepAlive() {
            return keepAlive;
        }

        public void setKeepAlive(Boolean keepAlive) {
            this.keepAlive = keepAlive;
        }
    }

    /**
     * 追加子节点，调用方不必判空。
     *
     * @param child 子节点
     */
    public void addChild(MenuNodeVO child) {
        if (children == null) {
            children = new ArrayList<>();
        }
        children.add(child);
    }

    /**
     * 输出关键字段，便于日志排查。
     *
     * @return 对象字符串
     */
    @Override
    public String toString() {
        return "MenuNodeVO{name=" + name
                + ", path=" + path
                + ", component=" + component
                + ", redirect=" + redirect
                + ", meta=" + meta
                + ", children=" + children + "}";
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getComponent() {
        return component;
    }

    public void setComponent(String component) {
        this.component = component;
    }

    public String getRedirect() {
        return redirect;
    }

    public void setRedirect(String redirect) {
        this.redirect = redirect;
    }

    public Meta getMeta() {
        return meta;
    }

    public void setMeta(Meta meta) {
        this.meta = meta;
    }

    public List<MenuNodeVO> getChildren() {
        return children;
    }

    public void setChildren(List<MenuNodeVO> children) {
        this.children = children;
    }
}
