package com.cim.reqflow.ticket.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 上线点检项：一条对应需求文档第 5 节的一个功能点。
 * <p>
 * 清单允许为空 —— 新建系统在清单没维护前，所有单子都得能走完点检，
 * 所以「空清单不卡流程」是硬规则（`02` 4.5），不是容错。
 *
 * @author Haijie Xiao
 * @date 2026-11-05
 */
@TableName("req_check_item")
public class ReqCheckItemDO {

    @TableId(type = IdType.INPUT)
    private String id;

    /** 所属单据 */
    private String ticketId;

    /** 冗余系统 ID，供「按系统看重点功能」统计 */
    private String systemId;

    /** 功能点名称，如「批次过站后 WIP 自动扣减」 */
    private String itemName;

    /** 怎么验，一句话 */
    private String itemDesc;

    /** 期望结果，判定通过与否的依据 */
    private String expectResult;

    /** 1 = 重点功能，必须点检 */
    private Integer isKey;

    /** 排序号 */
    private Integer sortNo;

    /** MANUAL 人工维护 / AI_SUGGEST 从需求文档第 5 节带出 */
    private String source;

    /** PASS / FAIL / NA，点检时填 */
    private String checkResult;

    /** 不通过说明 */
    private String checkRemark;

    /** 点检人 */
    private String checkedBy;

    /** 点检时间 */
    private String checkedAt;

    private String createdAt;

    private String updatedAt;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTicketId() {
        return ticketId;
    }

    public void setTicketId(String ticketId) {
        this.ticketId = ticketId;
    }

    public String getSystemId() {
        return systemId;
    }

    public void setSystemId(String systemId) {
        this.systemId = systemId;
    }

    public String getItemName() {
        return itemName;
    }

    public void setItemName(String itemName) {
        this.itemName = itemName;
    }

    public String getItemDesc() {
        return itemDesc;
    }

    public void setItemDesc(String itemDesc) {
        this.itemDesc = itemDesc;
    }

    public String getExpectResult() {
        return expectResult;
    }

    public void setExpectResult(String expectResult) {
        this.expectResult = expectResult;
    }

    public Integer getIsKey() {
        return isKey;
    }

    public void setIsKey(Integer isKey) {
        this.isKey = isKey;
    }

    public Integer getSortNo() {
        return sortNo;
    }

    public void setSortNo(Integer sortNo) {
        this.sortNo = sortNo;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getCheckResult() {
        return checkResult;
    }

    public void setCheckResult(String checkResult) {
        this.checkResult = checkResult;
    }

    public String getCheckRemark() {
        return checkRemark;
    }

    public void setCheckRemark(String checkRemark) {
        this.checkRemark = checkRemark;
    }

    public String getCheckedBy() {
        return checkedBy;
    }

    public void setCheckedBy(String checkedBy) {
        this.checkedBy = checkedBy;
    }

    public String getCheckedAt() {
        return checkedAt;
    }

    public void setCheckedAt(String checkedAt) {
        this.checkedAt = checkedAt;
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
}
