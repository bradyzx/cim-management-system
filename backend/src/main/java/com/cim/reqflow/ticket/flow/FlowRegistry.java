package com.cim.reqflow.ticket.flow;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 动作注册表：把 {@link FlowAction} 的枚举变成两张查表。
 * <p>
 * 一张按动作码查（执行入口用），一张按前置状态查（渲染按钮用）。
 * 手写 if-else 判断「这个状态能做什么」是最容易漏分支的写法 ——
 * 27 个状态、60 多条边，改一处流程就要翻十处代码，漏掉的那处只在生产上才暴露。
 * <p>
 * 全局动作（SUSPEND / ESCALATE）的 from 是 *，不落在按状态分组的表里，
 * 查询时单独合并进来，避免在 24 个状态的列表里各塞一份。
 *
 * @author Haijie Xiao
 * @date 2026-11-04
 */
public final class FlowRegistry {

    /** 动作码 → 动作 */
    private static final Map<String, FlowAction> BY_CODE = new HashMap<>();

    /** 前置状态 → 该状态可出发的动作（不含全局动作与系统动作） */
    private static final Map<String, List<FlowAction>> BY_FROM = new HashMap<>();

    /** 全局动作：from 为 *，任意在途状态可出发 */
    private static final List<FlowAction> GLOBAL = new ArrayList<>();

    static {
        for (FlowAction action : FlowAction.values()) {
            BY_CODE.put(action.getCode(), action);

            if ("*".equals(action.getFromStatus())) {
                GLOBAL.add(action);
                continue;
            }

            BY_FROM.computeIfAbsent(action.getFromStatus(), key -> new ArrayList<>())
                    .add(action);
        }
    }

    private FlowRegistry() {
    }

    /**
     * 按动作码查动作。
     *
     * @param code 动作码
     * @return 对应动作；未知名返回 null，由调用方转成 ACTION_INVALID
     */
    public static FlowAction byCode(String code) {
        return BY_CODE.get(code);
    }

    /**
     * 取某状态下全部可出发的动作，含全局动作，**不含系统动作**。
     * <p>
     * 系统动作不要出现在前端按钮里：AI 取任务、定时器转评价这些没有「人去点」的语义，
     * 混进列表会让前端多一层过滤，也让接口返回值产生歧义。
     *
     * @param status 当前状态
     * @return 可出发动作列表，按枚举声明顺序
     */
    public static List<FlowAction> availableFrom(String status) {
        List<FlowAction> result = new ArrayList<>();
        List<FlowAction> own = BY_FROM.get(status);
        if (own != null) {
            for (FlowAction action : own) {
                if (!action.isSystemOnly()) {
                    result.add(action);
                }
            }
        }
        for (FlowAction action : GLOBAL) {
            if (action.isSystemOnly()) {
                continue;
            }
            if (action.matchesFrom(status)) {
                result.add(action);
            }
        }
        return result;
    }

    /**
     * 取某状态下可出发的全部动作，含系统动作。
     * 定时任务与后台服务用这个，前端不要用。
     *
     * @param status 当前状态
     * @return 可出发动作列表
     */
    public static List<FlowAction> allFrom(String status) {
        List<FlowAction> result = new ArrayList<>();
        List<FlowAction> own = BY_FROM.get(status);
        if (own != null) {
            result.addAll(own);
        }
        for (FlowAction action : GLOBAL) {
            if (action.matchesFrom(status)) {
                result.add(action);
            }
        }
        return result;
    }

    /**
     * 全部动作码 → 动作的只读视图，供接口文档与自检使用。
     *
     * @return 不可变映射
     */
    public static Map<String, FlowAction> allActions() {
        return Collections.unmodifiableMap(BY_CODE);
    }
}
