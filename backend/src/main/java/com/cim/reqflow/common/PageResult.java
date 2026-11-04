package com.cim.reqflow.common;

import java.util.List;

/**
 * 分页返回体。
 * 字段名必须是 items / total（前端契约），不是 MyBatis-Plus 的 records / list，
 * 因此 MyBatis-Plus 的 Page 需要转换成本类后再返回。
 *
 * @param <T> 行数据类型
 * @author Haijie Xiao
 * @date 2026-11-03
 */
public class PageResult<T> {

    /** 当前页数据 */
    private List<T> items;

    /** 总记录数 */
    private long total;

    public PageResult() {
    }

    public PageResult(List<T> items, long total) {
        this.items = items;
        this.total = total;
    }

    /**
     * 输出关键字段，便于日志排查。
     *
     * @return 对象字符串
     */
    @Override
    public String toString() {
        return "PageResult{items=" + items
                + ", total=" + total + "}";
    }

    public List<T> getItems() {
        return items;
    }

    public void setItems(List<T> items) {
        this.items = items;
    }

    public long getTotal() {
        return total;
    }

    public void setTotal(long total) {
        this.total = total;
    }
}
