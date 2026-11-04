package com.cim.reqflow.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 工作日历实体。只登记例外（法定假日与调休），未登记的日期按自然周推断。
 * <p>
 * SLA 的「2 个工作日」必须靠它折算：直接把小时数加到当前时间上会跨周末，
 * 国庆期间全量误报超期。见 09 第四节第 4 条。
 *
 * @author Haijie Xiao
 * @date 2026-11-04
 */
@TableName("sys_work_calendar")
public class SysWorkCalendarDO {

    @TableId(type = IdType.INPUT)
    private String id;

    /** 日期，yyyy-MM-dd。用字符串存，ISO 字典序等于时间序，两库比较行为一致 */
    private String calDate;

    /** 年份，便于按年批量维护 */
    private Integer yearNo;

    /** 日期类型：WORKDAY 工作日 / WEEKEND 休息日 / HOLIDAY 法定假日 / MAKEUP 调休上班 */
    private String dayType;

    /** 名称，如「元旦」 */
    private String dayName;

    /** 当日工时，假日为 0，工作日为 8 */
    private Double workHours;

    /** 上班时间 */
    private String startTime;

    /** 下班时间 */
    private String endTime;

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

    public String getCalDate() {
        return calDate;
    }

    public void setCalDate(String calDate) {
        this.calDate = calDate;
    }

    public Integer getYearNo() {
        return yearNo;
    }

    public void setYearNo(Integer yearNo) {
        this.yearNo = yearNo;
    }

    public String getDayType() {
        return dayType;
    }

    public void setDayType(String dayType) {
        this.dayType = dayType;
    }

    public String getDayName() {
        return dayName;
    }

    public void setDayName(String dayName) {
        this.dayName = dayName;
    }

    public Double getWorkHours() {
        return workHours;
    }

    public void setWorkHours(Double workHours) {
        this.workHours = workHours;
    }

    public String getStartTime() {
        return startTime;
    }

    public void setStartTime(String startTime) {
        this.startTime = startTime;
    }

    public String getEndTime() {
        return endTime;
    }

    public void setEndTime(String endTime) {
        this.endTime = endTime;
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
     * 只输出日期与类型，避免备注刷屏。
     *
     * @return 关键字段字符串
     */
    @Override
    public String toString() {
        return "SysWorkCalendarDO{calDate=" + calDate + ", dayType=" + dayType
                + ", workHours=" + workHours + "}";
    }
}
