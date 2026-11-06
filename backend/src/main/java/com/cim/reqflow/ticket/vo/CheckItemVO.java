package com.cim.reqflow.ticket.vo;

/**
 * 点检项视图（契约 §10.1）。
 *
 * @author Haijie Xiao
 * @date 2026-11-05
 */
public class CheckItemVO {

    private String id;

    /** 功能点名称 */
    private String itemName;

    /** 怎么验 */
    private String itemDesc;

    /** 期望结果 */
    private String expectResult;

    /** 1 = 重点功能，必须点检 */
    private Integer isKey;

    private Integer sortNo;

    /** MANUAL / AI_SUGGEST */
    private String source;

    /** 来源中文名 */
    private String sourceLabel;

    /** PASS / FAIL / NA，未点检为 null */
    private String checkResult;

    /** 结果中文名 */
    private String checkResultLabel;

    /** 不通过说明 */
    private String checkRemark;

    /** 点检人 */
    private String checkedByName;

    /** 点检时间 */
    private String checkedAt;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
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

    public String getSourceLabel() {
        return sourceLabel;
    }

    public void setSourceLabel(String sourceLabel) {
        this.sourceLabel = sourceLabel;
    }

    public String getCheckResult() {
        return checkResult;
    }

    public void setCheckResult(String checkResult) {
        this.checkResult = checkResult;
    }

    public String getCheckResultLabel() {
        return checkResultLabel;
    }

    public void setCheckResultLabel(String checkResultLabel) {
        this.checkResultLabel = checkResultLabel;
    }

    public String getCheckRemark() {
        return checkRemark;
    }

    public void setCheckRemark(String checkRemark) {
        this.checkRemark = checkRemark;
    }

    public String getCheckedByName() {
        return checkedByName;
    }

    public void setCheckedByName(String checkedByName) {
        this.checkedByName = checkedByName;
    }

    public String getCheckedAt() {
        return checkedAt;
    }

    public void setCheckedAt(String checkedAt) {
        this.checkedAt = checkedAt;
    }
}
