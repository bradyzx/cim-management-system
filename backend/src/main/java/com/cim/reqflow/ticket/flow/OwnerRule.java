package com.cim.reqflow.ticket.flow;

/**
 * 动作归属判定规则。
 * <p>
 * 角色白名单（{@link FlowAction#getRoles()}）只回答「这类人能不能点这类按钮」，
 * 回答不了「这张单该不该他点」。本枚举补上后半句。
 * <p>
 * 判决顺序固定：角色白名单 → 归属判定 → 实习生禁令 → 前置校验。
 * 顺序颠倒会出两种错：先判归属会把管理员挡在门外（管理员不满足 assigneeId = 我）；
 * 先判禁令会让实习生看到按钮但点不动，用户以为是 bug。
 *
 * @author Haijie Xiao
 * @date 2026-11-04
 */
public enum OwnerRule {

    /**
     * 只看角色白名单，不判归属。
     * 用在建单、系统动作这类「不针对某张单的某个人」的场景。
     */
    NONE,

    /**
     * 提出人或需求 Owner。判定：reporter_id = 我 或 owner_id = 我。
     * 用在文档确认、用户评审、UAT、验收、评价、重开。
     */
    REPORTER,

    /**
     * 承办人。判定：assignee_id = 我。
     * 用在接收、设计、自测、运行监控。
     */
    ASSIGNEE,

    /**
     * 三方确认人。按 03 4.2.4 现算（MIT / 平台 / 上级三类），不看数据范围的 writable。
     * <p>
     * 这里最容易写错：DEPT_MANAGER 是只读角色（writable = 0），
     * 而部门负责人恰恰是最常见的上级确认人 —— 用 writable 判会让单子永远卡在确认这一步。
     */
    CONFIRMER,

    /**
     * 团队 Leader。判定：本团队 Leader；PENDING_ASSIGN 状态下全员可派。
     * 用在派单、驳回、上线审批、挂起、评审结论。
     */
    LEADER
}
