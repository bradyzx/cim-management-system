package com.cim.reqflow.ticket.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cim.reqflow.ticket.entity.ReqTestCaseDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 测试用例表。
 *
 * @author Haijie Xiao
 * @date 2026-11-05
 */
@Mapper
public interface ReqTestCaseMapper extends BaseMapper<ReqTestCaseDO> {
}
