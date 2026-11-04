package com.cim.reqflow.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 字典数据表实体。
 * cssClass 存前端的语义色（success / error / warning），字段名沿用表结构，
 * 对前端输出时映射为 color，避免前端感知数据库命名。
 *
 * @author Haijie Xiao
 * @date 2026-11-03
 */
@TableName("sys_dict_data")
public class SysDictDataDO {

    @TableId(type = IdType.INPUT)
    private String id;

    /** 所属字典编码 */
    private String dictType;

    /** 显示标签 */
    private String dictLabel;

    /** 实际值 */
    private String dictValue;

    /** 前端语义色 */
    private String cssClass;

    /** 同字典内排序号 */
    private Integer sort;

    /** 状态：ACTIVE 启用、DISABLED 停用 */
    private String status;

    /** 备注 */
    private String remark;

    /** 创建时间，格式 yyyy-MM-dd HH:mm:ss */
    private String createdAt;

    /** 更新时间，格式 yyyy-MM-dd HH:mm:ss */
    private String updatedAt;

    /** 逻辑删除标志：1 已删除、0 正常 */
    private Integer isDeleted;

    /**
     * 输出关键字段，便于日志排查。
     *
     * @return 对象字符串
     */
    @Override
    public String toString() {
        return "SysDictDataDO{id=" + id
                + ", dictType=" + dictType
                + ", dictLabel=" + dictLabel
                + ", dictValue=" + dictValue
                + ", cssClass=" + cssClass
                + ", sort=" + sort
                + ", status=" + status
                + ", remark=" + remark
                + ", createdAt=" + createdAt
                + ", updatedAt=" + updatedAt
                + ", isDeleted=" + isDeleted + "}";
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getDictType() {
        return dictType;
    }

    public void setDictType(String dictType) {
        this.dictType = dictType;
    }

    public String getDictLabel() {
        return dictLabel;
    }

    public void setDictLabel(String dictLabel) {
        this.dictLabel = dictLabel;
    }

    public String getDictValue() {
        return dictValue;
    }

    public void setDictValue(String dictValue) {
        this.dictValue = dictValue;
    }

    public String getCssClass() {
        return cssClass;
    }

    public void setCssClass(String cssClass) {
        this.cssClass = cssClass;
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

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    public String getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(String updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Integer getIsDeleted() {
        return isDeleted;
    }

    public void setIsDeleted(Integer isDeleted) {
        this.isDeleted = isDeleted;
    }
}
