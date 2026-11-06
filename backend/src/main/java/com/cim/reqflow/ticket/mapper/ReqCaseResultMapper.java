package com.cim.reqflow.ticket.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cim.reqflow.ticket.entity.ReqCaseResultDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用例执行记录表。
 *
 * @author Haijie Xiao
 * @date 2026-11-05
 */
@Mapper
public interface ReqCaseResultMapper extends BaseMapper<ReqCaseResultDO> {
}
