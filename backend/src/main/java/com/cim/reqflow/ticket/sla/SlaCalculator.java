package com.cim.reqflow.ticket.sla;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.cim.reqflow.common.constant.CommonConstants;
import com.cim.reqflow.system.entity.SysWorkCalendarDO;
import com.cim.reqflow.system.mapper.SysWorkCalendarMapper;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * SLA 到期时间计算。工时不等于自然小时，必须按工作日历逐日累减。
 * <p>
 * 为什么不能直接「当前时间 + limit_hours」：`limit_hours = 16` 表示两个工作日，
 * 周五下午提交的单直接加 16 小时会落在周六，得到的是自然时长；国庆期间更是全量误报超期。
 * 09 第四节第 4 条把这条列成硬缺口，本类就是它的唯一实现点 ——
 * 其它任何地方都不许再自己算到期时间。
 * <p>
 * 口径：1 个工作日 = 8 小时 = 480 分钟，上班时间 09:00，当日工作截止 17:00。
 * 日历表只存例外（法定假日与调休），未登记的日期按自然周推断。
 *
 * @author Haijie Xiao
 * @date 2026-11-04
 */
@Component
public class SlaCalculator {

    /** 上班时间 */
    private static final LocalTime WORK_START = LocalTime.of(9, 0);

    /** 每个工作日的可用工时（分钟）：8 小时 */
    private static final int DAILY_CAPACITY_MINUTES = 8 * 60;

    /** 最多向后推进的自然日，防止日历配置异常导致死循环 */
    private static final int MAX_ADVANCE_DAYS = 120;

    /** 日历类型：法定假日 */
    private static final String DAY_TYPE_HOLIDAY = "HOLIDAY";

    /** 日历类型：休息日 */
    private static final String DAY_TYPE_WEEKEND = "WEEKEND";

    /** 工作日历数据访问 */
    private final SysWorkCalendarMapper sysWorkCalendarMapper;

    public SlaCalculator(SysWorkCalendarMapper sysWorkCalendarMapper) {
        this.sysWorkCalendarMapper = sysWorkCalendarMapper;
    }

    /**
     * 计算到期时间。limitHours 为 0 或负数表示该状态不计时（如 SUSPENDED、DEVELOPING
     * 按预估工时另算），返回 null。
     *
     * @param startText  起算时间，格式 yyyy-MM-dd HH:mm:ss
     * @param limitHours 时限，单位为工作日折算后的小时数
     * @return 到期时间；不计时或无法计算时为 null
     */
    public String calculateDueAt(String startText, double limitHours) {
        if (startText == null || limitHours <= 0) {
            return null;
        }
        LocalDateTime start = LocalDateTime.parse(startText, CommonConstants.DATE_TIME_FORMATTER);
        Map<LocalDate, SysWorkCalendarDO> calendar = loadCalendar(start.toLocalDate());
        LocalDateTime dueAt = advance(start, limitHours * 60, calendar);
        if (dueAt == null) {
            return null;
        }
        return dueAt.format(CommonConstants.DATE_TIME_FORMATTER);
    }

    /**
     * 计算已超期小时数，按自然小时计（超期时长本身不需要按工作日折算）。
     *
     * @param dueText 到期时间
     * @param now     当前时间
     * @return 超期小时数，未超期为 0
     */
    public double overdueHours(String dueText, LocalDateTime now) {
        if (dueText == null || now == null) {
            return 0;
        }
        LocalDateTime dueAt = LocalDateTime.parse(dueText, CommonConstants.DATE_TIME_FORMATTER);
        if (!now.isAfter(dueAt)) {
            return 0;
        }
        long minutes = Duration.between(dueAt, now).toMinutes();
        return minutes / 60.0;
    }

    /**
     * 载入起算日之后一段时间内的日历例外。
     * 只取需要的区间而不是全表：日历每年维护几十条，全表也就几百条，
     * 但按区间取可以让 SQL 走 cal_date 索引，且换 Oracle 时行为一致。
     *
     * @param from 起算日期
     * @return 日期 → 日历行
     */
    private Map<LocalDate, SysWorkCalendarDO> loadCalendar(LocalDate from) {
        LocalDate to = from.plusDays(MAX_ADVANCE_DAYS);
        List<SysWorkCalendarDO> rows = sysWorkCalendarMapper.selectList(
                Wrappers.<SysWorkCalendarDO>lambdaQuery()
                        .ge(SysWorkCalendarDO::getCalDate, from.toString())
                        .le(SysWorkCalendarDO::getCalDate, to.toString()));

        Map<LocalDate, SysWorkCalendarDO> calendar = new HashMap<>();
        for (SysWorkCalendarDO row : rows) {
            calendar.put(LocalDate.parse(row.getCalDate()), row);
        }
        return calendar;
    }

    /**
     * 逐日推进剩余工时，直到用尽。
     *
     * @param start    起算时间
     * @param minutes  需要的总工时（分钟）
     * @param calendar 日历例外
     * @return 到期时间；超出推进上限时为 null
     */
    private LocalDateTime advance(LocalDateTime start, double minutes,
                                  Map<LocalDate, SysWorkCalendarDO> calendar) {
        double remaining = minutes;
        LocalDateTime cursor = start;

        for (int day = 0; day <= MAX_ADVANCE_DAYS; day++) {
            LocalDate date = cursor.toLocalDate();
            if (!isWorkday(date, calendar)) {
                cursor = date.plusDays(1).atTime(WORK_START);
                continue;
            }

            LocalDateTime dayStart = date.atTime(WORK_START);
            if (cursor.isBefore(dayStart)) {
                cursor = dayStart;
            }
            int usedMinutes = (int) Duration.between(dayStart, cursor).toMinutes();
            int availableMinutes = DAILY_CAPACITY_MINUTES - usedMinutes;

            // 当天已经下班：直接挪到次日上午，不用负的可用工时参与计算
            if (availableMinutes <= 0) {
                cursor = date.plusDays(1).atTime(WORK_START);
                continue;
            }

            if (remaining <= availableMinutes) {
                return cursor.plusMinutes((long) Math.ceil(remaining));
            }

            remaining -= availableMinutes;
            cursor = date.plusDays(1).atTime(WORK_START);
        }
        return null;
    }

    /**
     * 判断是否工作日：日历有例外按例外，没有按自然周（周一至周五）。
     *
     * @param date     日期
     * @param calendar 日历例外
     * @return true 表示工作日
     */
    private boolean isWorkday(LocalDate date, Map<LocalDate, SysWorkCalendarDO> calendar) {
        SysWorkCalendarDO exception = calendar.get(date);
        if (exception != null && exception.getDayType() != null) {
            return !DAY_TYPE_HOLIDAY.equals(exception.getDayType())
                    && !DAY_TYPE_WEEKEND.equals(exception.getDayType());
        }
        DayOfWeek dayOfWeek = date.getDayOfWeek();
        return dayOfWeek != DayOfWeek.SATURDAY && dayOfWeek != DayOfWeek.SUNDAY;
    }
}
