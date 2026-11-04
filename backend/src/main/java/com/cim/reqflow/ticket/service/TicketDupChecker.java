package com.cim.reqflow.ticket.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.cim.reqflow.ticket.entity.ReqTicketDO;
import com.cim.reqflow.ticket.flow.TicketStatus;
import com.cim.reqflow.ticket.mapper.ReqTicketMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 规则版查重：字符二元组（bigram）Dice 相似度，不引分词库。
 * <p>
 * 50 人规模、单量千级，字符二元组对中文足够分辨 —— 「报工看板加良率曲线」
 * 和「报工看板加批次良率」能到 0.6 以上，而两个不相关需求通常在 0.2 以下。
 * 二期接 LLM 后本类换成语义查重，接口形状不变。
 * <p>
 * 权重三档（02 第六节：同系统 &gt; 同项目 &gt; 跨项目）：
 * 相似度 × 1.0 / 0.9 / 0.8。同一句话换个系统提，不该被当成重复。
 * <p>
 * 判定：**最高加权相似度 &gt; 0.5** 视为命中，要求提出人填「仍新建原因」；
 * 阈值以内只提示不拦截。
 *
 * @author Haijie Xiao
 * @date 2026-11-04
 */
@Service
public class TicketDupChecker {

    /** 命中阈值：最高加权相似度超过它就要求确认 */
    public static final double 命中阈值 = 0.5;

    /** 返回给前端看的相似条数 */
    private static final int 返回条数 = 5;

    /** 候选单上限：按创建时间倒序取最近 500 条参与比对，够覆盖一年的量 */
    private static final long 候选上限 = 500;

    /** 权重：同系统 */
    private static final double 权重同系统 = 1.0;

    /** 权重：同项目不同系统 */
    private static final double 权重同项目 = 0.9;

    /** 权重：跨项目 */
    private static final double 权重跨项目 = 0.8;

    private final ReqTicketMapper reqTicketMapper;

    public TicketDupChecker(ReqTicketMapper reqTicketMapper) {
        this.reqTicketMapper = reqTicketMapper;
    }

    /**
     * 一条相似需求。
     *
     * @param ticketId   单据 ID
     * @param ticketNo   单号
     * @param title      标题
     * @param systemCode 系统编码
     * @param score      加权后的相似度
     */
    public record 相似项(String ticketId, String ticketNo, String title,
                         String systemCode, double score) {
    }

    /**
     * 对新单文本做查重。
     *
     * @param ticketType 单据类型
     * @param systemId   待建单的系统（用于算权重）
     * @param projectId  待建单的项目
     * @param 文本        标题 + 简述拼接
     * @return 相似需求列表，按加权相似度降序
     */
    public List<相似项> 查重(String ticketType, String systemId, String projectId, String 文本) {
        Set<String> 基准 = bigram(文本);
        if (基准.isEmpty()) {
            return List.of();
        }
        List<ReqTicketDO> 候选 = reqTicketMapper.selectList(
                Wrappers.<ReqTicketDO>lambdaQuery()
                        .select(ReqTicketDO::getId, ReqTicketDO::getTicketNo,
                                ReqTicketDO::getTitle, ReqTicketDO::getDescription,
                                ReqTicketDO::getSystemId, ReqTicketDO::getProjectId)
                        .eq(ReqTicketDO::getTicketType, ticketType)
                        .ne(ReqTicketDO::getStatus, TicketStatus.CLOSED)
                        .orderByDesc(ReqTicketDO::getCreatedAt)
                        .last("limit " + 候选上限));

        List<相似项> 结果 = new ArrayList<>(候选.size());
        for (ReqTicketDO 候选单 : 候选) {
            String 候选文本 = (候选单.getTitle() == null ? "" : 候选单.getTitle())
                    + (候选单.getDescription() == null ? "" : 候选单.getDescription());
            double 原始相似度 = dice(基准, bigram(候选文本));
            if (原始相似度 <= 0) {
                continue;
            }
            double 加权 = 原始相似度 * 权重(候选单, systemId, projectId);
            结果.add(new 相似项(候选单.getId(), 候选单.getTicketNo(), 候选单.getTitle(),
                    候选单.getSystemCode(), round4(加权)));
        }
        结果.sort(Comparator.comparingDouble(相似项::score).reversed());
        return 结果.subList(0, Math.min(返回条数, 结果.size()));
    }

    /**
     * 是否命中拦截线。
     *
     * @param 列表 查重结果
     * @return 最高相似度超阈值返回 true
     */
    public boolean 命中(List<相似项> 列表) {
        return !列表.isEmpty() && 列表.get(0).score() > 命中阈值;
    }

    /**
     * 权重：同系统 1.0，同项目 0.9，跨项目 0.8。
     *
     * @param 候选单    候选单据
     * @param systemId  新单系统
     * @param projectId 新单项目
     * @return 权重
     */
    private double 权重(ReqTicketDO 候选单, String systemId, String projectId) {
        if (systemId != null && systemId.equals(候选单.getSystemId())) {
            return 权重同系统;
        }
        if (projectId != null && projectId.equals(候选单.getProjectId())) {
            return 权重同项目;
        }
        return 权重跨项目;
    }

    /**
     * 字符二元组集合。先去掉全部空白，标点保留 —— 「导出增加按班次筛选」
     * 和「导出增加按班次筛选。」应视为几乎相同。
     *
     * @param 文本 原文
     * @return 二元组集合
     */
    private Set<String> bigram(String 文本) {
        if (文本 == null) {
            return Set.of();
        }
        String 紧凑 = 文本.replaceAll("\\s+", "");
        if (紧凑.length() < 2) {
            return 紧凑.isEmpty() ? Set.of() : Set.of(紧凑);
        }
        Set<String> 元素 = new HashSet<>(紧凑.length());
        for (int i = 0; i < 紧凑.length() - 1; i++) {
            元素.add(紧凑.substring(i, i + 2));
        }
        return 元素;
    }

    /**
     * Dice 系数：2 × 交集 / 两集合大小之和。
     *
     * @param a 集合一
     * @param b 集合二
     * @return 0~1
     */
    private double dice(Set<String> a, Set<String> b) {
        if (a.isEmpty() || b.isEmpty()) {
            return 0;
        }
        Set<String> 小 = a.size() <= b.size() ? a : b;
        Set<String> 大 = a.size() <= b.size() ? b : a;
        int 交集 = 0;
        for (String 元素 : 小) {
            if (大.contains(元素)) {
                交集++;
            }
        }
        return 2.0 * 交集 / (a.size() + b.size());
    }

    /**
     * 保留四位小数，返回给前端展示与比较都稳定。
     *
     * @param 值 原值
     * @return 四位小数值
     */
    private double round4(double 值) {
        return Math.round(值 * 10000) / 10000.0;
    }
}
