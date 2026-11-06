package com.cim.reqflow.ticket.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cim.reqflow.ticket.entity.ReqCheckItemDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 上线点检项表。
 *
 * @author Haijie Xiao
 * @date 2026-11-05
 */
@Mapper
public interface ReqCheckItemMapper extends BaseMapper<ReqCheckItemDO> {
}
