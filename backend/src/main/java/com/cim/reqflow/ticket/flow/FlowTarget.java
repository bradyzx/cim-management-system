package com.cim.reqflow.ticket.flow;

/**
 * 目标状态占位符。
 * <p>
 * 绝大多数动作的目标状态是固定的，直接写在 {@link FlowAction} 常量里。
 * 只有两个动作的目标要运行时才知道：
 * <p>
 * - 恢复：回到挂起前的状态（req_ticket.suspended_from）
 * - 评审结论「继续」：必须由 Leader 指定回到哪一步，不允许回原状态（02 6.2）
 * <p>
 * 单独成类而不是写进 FlowAction，因为枚举常量先于静态字段初始化，
 * 在常量里引用本类的静态字段会触发「非法前向引用」，编译期就报错。
 *
 * @author Haijie Xiao
 * @date 2026-11-04
 */
public final class FlowTarget {

    private FlowTarget() {
    }

    /** 恢复到挂起前状态，由服务层填 req_ticket.suspended_from */
    public static final String SUSPENDED_FROM = "@suspended_from";

    /** 由调用方指定目标状态（评审结论「继续」） */
    public static final String TARGET_STATUS = "@target_status";
}
