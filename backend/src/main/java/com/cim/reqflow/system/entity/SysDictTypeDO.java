package com.cim.reqflow.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 字典类型表实体。dictType 是前端取字典的业务编码（如 req_status），全局唯一。
 *
 * @author Haijie Xiao
 * @date 2026-11-03
 */
@TableName("sys_dict_type")
public class SysDictTypeDO {

    @TableId(type = IdType.INPUT)
    private String id;

    /** 字典编码，全局唯一 */
    private String dictType;

    /** 字典名称 */
    private String dictName;

    /** 备注 */
    private String remark;

    /** 状态：ACTIVE 启用、DISABLED 停用 */
    private String status;

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
        return "SysDictTypeDO{id=" + id
                + ", dictType=" + dictType
                + ", dictName=" + dictName
                + ", remark=" + remark
                + ", status=" + status
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

    public String getDictName() {
        return dictName;
    }

    public void setDictName(String dictName) {
        this.dictName = dictName;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
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
