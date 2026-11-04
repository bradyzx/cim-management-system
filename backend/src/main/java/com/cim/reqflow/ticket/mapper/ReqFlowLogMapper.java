package com.cim.reqflow.ticket.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cim.reqflow.ticket.entity.ReqFlowLogDO;

/**
 * 流转时间线数据访问接口。退回计次靠聚合查询本表。
 *
 * @author Haijie Xiao
 * @date 2026-11-04
 */
public interface ReqFlowLogMapper extends BaseMapper<ReqFlowLogDO> {
}
