package com.cim.reqflow.ticket.flow;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.cim.reqflow.ticket.entity.ReqFlowLogDO;
import com.cim.reqflow.ticket.mapper.ReqFlowLogMapper;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 退回计次：按 {@link CountScope} 的四种维度聚合 req_flow_log。
 * <p>
 * 为什么不在 req_ticket 上放计数字段：`return_count` 只有一个总数，分不清是哪个点退的。
 * 「同一退回点第 3 次升级」是按点判定的，一个总数到不了这个结论 ——
 * 一张单在 ① 退 2 次、③ 退 1 次时，总数是 3 但没有任何点到第 3 次。
 *
 * @author Haijie Xiao
 * @date 2026-11-04
 */
@Component
public class ReturnCounter {

    /** 流转时间线数据访问 */
    private final ReqFlowLogMapper reqFlowLogMapper;

    public ReturnCounter(ReqFlowLogMapper reqFlowLogMapper) {
        this.reqFlowLogMapper = reqFlowLogMapper;
    }

    /**
     * 按单计数：该单在指定退回点上的记录数。
     *
     * @param ticketId    单据 ID
     * @param returnPoint 退回点码
     * @return 次数，无记录为 0
     */
    public int countOnTicket(String ticketId, String returnPoint) {
        Long count = reqFlowLogMapper.selectCount(
                Wrappers.<ReqFlowLogDO>lambdaQuery()
                        .eq(ReqFlowLogDO::getTicketId, ticketId)
                        .eq(ReqFlowLogDO::getReturnPoint, returnPoint));
        return count == null ? 0 : count.intValue();
    }

    /**
     * 按人计数：该单 + 该操作人在指定退回点上的记录数。
     * 用于 ③ 拒接「单人 2 次」，防止同一个人反复拒接同一张单。
     *
     * @param ticketId    单据 ID
     * @param returnPoint 退回点码
     * @param operatorId  操作人 ID
     * @return 次数
     */
    public int countByOperator(String ticketId, String returnPoint, String operatorId) {
        Long count = reqFlowLogMapper.selectCount(
                Wrappers.<ReqFlowLogDO>lambdaQuery()
                        .eq(ReqFlowLogDO::getTicketId, ticketId)
                        .eq(ReqFlowLogDO::getReturnPoint, returnPoint)
                        .eq(ReqFlowLogDO::getOperatorId, operatorId));
        return count == null ? 0 : count.intValue();
    }

    /**
     * 按组计数：该单在一组退回点上的记录数合计。
     * 用于设计评审 ⑫⑬⑭ 的「三步合计 3 次」—— 否则可以在三个点各退 2 次，六轮打转。
     *
     * @param ticketId     单据 ID
     * @param returnPoints 退回点码列表
     * @return 次数合计
     */
    public int countInGroup(String ticketId, List<String> returnPoints) {
        if (returnPoints == null || returnPoints.isEmpty()) {
            return 0;
        }
        Long count = reqFlowLogMapper.selectCount(
                Wrappers.<ReqFlowLogDO>lambdaQuery()
                        .eq(ReqFlowLogDO::getTicketId, ticketId)
                        .in(ReqFlowLogDO::getReturnPoint, returnPoints));
        return count == null ? 0 : count.intValue();
    }

    /**
     * 下一个序号：该单在该退回点上的第几次。
     * 写流转记录时用，保证同一点的 return_seq 连续。
     *
     * @param ticketId    单据 ID
     * @param returnPoint 退回点码
     * @return 本次是第几次，从 1 开始
     */
    public int nextSeq(String ticketId, String returnPoint) {
        return countOnTicket(ticketId, returnPoint) + 1;
    }
}
