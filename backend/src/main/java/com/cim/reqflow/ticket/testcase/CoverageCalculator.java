package com.cim.reqflow.ticket.testcase;

import com.cim.reqflow.ticket.entity.ReqAcceptanceCriteriaDO;
import com.cim.reqflow.ticket.entity.ReqTestCaseDO;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 用例覆盖率计算：验收标准 → 用例的映射矩阵（`06` 第五节）。
 * <p>
 * 做成不依赖 Spring 的静态类，是因为有两个调用方：用例模块要给页面看，
 * 前置校验要在 UAT 通过时拦。前置校验在 flow 包、用例服务在 service 包，
 * 让前者依赖后者会形成构造器循环依赖，启动就报错。
 * <p>
 * 「覆盖率 &lt; 100% 不许 UAT 通过」是本系统唯一硬拦截的 AI 环节（`06` 第五节），
 * 所以这个数字必须只有一处算 —— 两处算必然会不一致，而不一致的那次一定出现在
 * 用户点「UAT 通过」的时候。
 *
 * @author Haijie Xiao
 * @date 2026-11-05
 */
public final class CoverageCalculator {

    /** 两位小数，避免前端显示 66.66666666666667% */
    private static final int SCALE = 2;

    /**
     * 覆盖率结果。
     *
     * @param criteriaTotal   验收标准总数，分母
     * @param covered         已被至少一条用例覆盖的标准数
     * @param rate            覆盖率，0~1
     * @param missingSeqs     未覆盖标准的序号
     * @param missingContents 未覆盖标准的原文
     */
    public record Result(int criteriaTotal, int covered, double rate,
                         List<Integer> missingSeqs, List<String> missingContents) {

        /**
         * 是否已全覆盖。
         *
         * @return 分母为 0 或全部覆盖时返回 true
         */
        public boolean full() {
            return covered >= criteriaTotal;
        }
    }

    private CoverageCalculator() {
        // 纯工具类
    }

    /**
     * 计算覆盖率。
     * <p>
     * 分母为 0（还没填验收标准）时按满覆盖处理：否则一张没写验收标准的单会永远卡在 UAT，
     * 而提单规则本身已经要求填写验收标准。真出现空标准，问题是提单校验漏了，不该让 UAT 背。
     *
     * @param criteria 本单的验收标准，按 seq 升序
     * @param cases    参与计算的用例（调用方负责只传最新一轮）
     * @return 覆盖率结果
     */
    public static Result calculate(List<ReqAcceptanceCriteriaDO> criteria,
                                   List<ReqTestCaseDO> cases) {
        if (criteria == null || criteria.isEmpty()) {
            return new Result(0, 0, 1.0, List.of(), List.of());
        }

        Set<Integer> 用例指向的标准 = new HashSet<>();
        if (cases != null) {
            for (ReqTestCaseDO 用例 : cases) {
                if (用例.getCriteriaIndex() != null) {
                    用例指向的标准.add(用例.getCriteriaIndex());
                }
            }
        }

        int 覆盖数 = 0;
        List<Integer> 缺失序号 = new ArrayList<>();
        List<String> 缺失内容 = new ArrayList<>();
        for (ReqAcceptanceCriteriaDO 标准 : criteria) {
            if (标准.getSeq() != null && 用例指向的标准.contains(标准.getSeq())) {
                覆盖数++;
                continue;
            }
            缺失序号.add(标准.getSeq());
            缺失内容.add(标准.getContent());
        }

        double 比率 = (double) 覆盖数 / criteria.size();
        return new Result(criteria.size(), 覆盖数, round(比率), 缺失序号, 缺失内容);
    }

    /**
     * 保留两位小数。
     *
     * @param 值 原值
     * @return 四舍五入后的值
     */
    private static double round(double 值) {
        double 倍数 = Math.pow(10, SCALE);
        return Math.round(值 * 倍数) / 倍数;
    }
}
