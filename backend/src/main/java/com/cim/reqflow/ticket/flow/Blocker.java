package com.cim.reqflow.ticket.flow;

import java.util.Map;

/**
 * 前置校验的阻塞项。
 * <p>
 * 一个动作可能同时命中多个阻塞项（既缺附件、又缺字段）。接口必须一次把这些全返回，
 * 让用户一轮补齐；只返回第一条会导致「补完附件再点，又被字段拦住」的反复体验。
 *
 * @param bizCode 错误码，前端据此定位交互（ATTACH_REQUIRED 定位附件区、FIELD_REQUIRED 定位字段）
 * @param message 可直接展示给用户的提示
 * @param details 附加数据，如缺失的附件类型与份数
 * @author Haijie Xiao
 * @date 2026-11-04
 */
public record Blocker(String bizCode, String message, Map<String, Object> details) {

    /** 缺少必填字段 */
    public static final String FIELD_REQUIRED = "FIELD_REQUIRED";
    /** 必传附件不足 */
    public static final String ATTACH_REQUIRED = "ATTACH_REQUIRED";
    /** 退回次数已达上限 */
    public static final String RETURN_LIMIT = "RETURN_LIMIT";
    /** 实习生禁令 */
    public static final String BAN_INTERN = "BAN_INTERN";
    /** 用例覆盖率不足 */
    public static final String COVERAGE_INSUFFICIENT = "COVERAGE_INSUFFICIENT";
    /** 豁免权限不足或缺少理由 */
    public static final String WAIVER_FORBIDDEN = "WAIVER_FORBIDDEN";
    /** 需求文档不完整，未达确认条件 */
    public static final String DOC_INCOMPLETE = "DOC_INCOMPLETE";
    /** 验收标准为空，未达设计评审通过（锁定）条件 */
    public static final String CRITERIA_EMPTY = "CRITERIA_EMPTY";
    /** 点检清单里的重点功能还有没点的 */
    public static final String CHECK_ITEMS_PENDING = "CHECK_ITEMS_PENDING";

    /**
     * 无附加数据的阻塞项。
     *
     * @param bizCode 错误码
     * @param message 提示
     * @return 阻塞项
     */
    public static Blocker of(String bizCode, String message) {
        return new Blocker(bizCode, message, Map.of());
    }
}
