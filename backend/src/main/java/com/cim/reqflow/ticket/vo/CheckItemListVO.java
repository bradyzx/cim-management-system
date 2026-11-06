package com.cim.reqflow.ticket.vo;

import java.util.List;

/**
 * 点检清单（契约 §10.1）。
 *
 * @author Haijie Xiao
 * @date 2026-11-05
 */
public class CheckItemListVO {

    /** 点检项 */
    private List<CheckItemVO> items;

    /** 1 = 清单未配置。此时允许直接点检通过 */
    private Integer empty;

    /** 重点项总数 */
    private Integer keyTotal;

    /** 已点检的重点项数 */
    private Integer keyChecked;

    /** 全部项（含非重点）的总数 */
    private Integer total;

    /** 已点检的条数（含非重点） */
    private Integer checked;

    public List<CheckItemVO> getItems() {
        return items;
    }

    public void setItems(List<CheckItemVO> items) {
        this.items = items;
    }

    public Integer getEmpty() {
        return empty;
    }

    public void setEmpty(Integer empty) {
        this.empty = empty;
    }

    public Integer getKeyTotal() {
        return keyTotal;
    }

    public void setKeyTotal(Integer keyTotal) {
        this.keyTotal = keyTotal;
    }

    public Integer getKeyChecked() {
        return keyChecked;
    }

    public void setKeyChecked(Integer keyChecked) {
        this.keyChecked = keyChecked;
    }

    public Integer getTotal() {
        return total;
    }

    public void setTotal(Integer total) {
        this.total = total;
    }

    public Integer getChecked() {
        return checked;
    }

    public void setChecked(Integer checked) {
        this.checked = checked;
    }
}
