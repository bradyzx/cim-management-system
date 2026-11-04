package com.cim.reqflow.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 单据编号发号器。按「seq_code + period_key」一段一行，CAS 自增 current_no 取号。
 *
 * @author Haijie Xiao
 * @date 2026-11-04
 */
@TableName("sys_no_sequence")
public class SysNoSequenceDO {

    @TableId(type = IdType.INPUT)
    private String id;

    /** 段码：TICKET_REQ / TICKET_BUG / TICKET_INC */
    private String seqCode;

    /** 编号前缀：REQ / BUG / INC */
    private String prefix;

    /** 切段方式：YEAR / MONTH / NONE */
    private String periodType;

    /** 段键：2026 或 202611，NONE 时为 * */
    private String periodKey;

    /** 本段已发出的最大序号 */
    private Integer currentNo;

    /** 补零位数 */
    private Integer padding;

    /** 编号模板 */
    private String noPattern;

    /** 是否启用 */
    private Integer enabled;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private String createdAt;

    /** 更新时间 */
    private String updatedAt;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getSeqCode() {
        return seqCode;
    }

    public void setSeqCode(String seqCode) {
        this.seqCode = seqCode;
    }

    public String getPrefix() {
        return prefix;
    }

    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }

    public String getPeriodType() {
        return periodType;
    }

    public void setPeriodType(String periodType) {
        this.periodType = periodType;
    }

    public String getPeriodKey() {
        return periodKey;
    }

    public void setPeriodKey(String periodKey) {
        this.periodKey = periodKey;
    }

    public Integer getCurrentNo() {
        return currentNo;
    }

    public void setCurrentNo(Integer currentNo) {
        this.currentNo = currentNo;
    }

    public Integer getPadding() {
        return padding;
    }

    public void setPadding(Integer padding) {
        this.padding = padding;
    }

    public String getNoPattern() {
        return noPattern;
    }

    public void setNoPattern(String noPattern) {
        this.noPattern = noPattern;
    }

    public Integer getEnabled() {
        return enabled;
    }

    public void setEnabled(Integer enabled) {
        this.enabled = enabled;
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

    /**
     * 只输出关键字段。
     *
     * @return 关键字段字符串
     */
    @Override
    public String toString() {
        return "SysNoSequenceDO{" + "seqCode=" + seqCode + ", " + "prefix=" + prefix + ", " + "periodType=" + periodType + "}";
    }
}
