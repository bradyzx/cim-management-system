package com.cim.reqflow.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.cim.reqflow.common.BizException;
import com.cim.reqflow.common.constant.CommonConstants;
import com.cim.reqflow.common.constant.SystemConstants;
import com.cim.reqflow.common.enums.DataScopeTypeEnum;
import com.cim.reqflow.common.util.IdUtils;
import com.cim.reqflow.system.dto.DataScopeRuleSaveDTO;
import com.cim.reqflow.system.entity.SysDataScopeRuleDO;
import com.cim.reqflow.system.mapper.SysDataScopeRuleMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 数据范围规则服务：角色 × 单据类型 的可见 / 可写范围维护。
 * 唯一键是 (roleCode, ticketType)，同一角色对同一单据类型只能有一条规则；
 * ticketType 为 * 表示对该角色全部单据类型的兜底规则。
 *
 * @author Haijie Xiao
 * @date 2026-11-03
 */
@Service
public class DataScopeService {

    /** 数据范围规则表数据访问 */
    private final SysDataScopeRuleMapper sysDataScopeRuleMapper;

    public DataScopeService(SysDataScopeRuleMapper sysDataScopeRuleMapper) {
        this.sysDataScopeRuleMapper = sysDataScopeRuleMapper;
    }

    /**
     * 全量规则列表。规则总量小（角色数 × 单据类型数），不分页，前端整表渲染。
     *
     * @param roleCode   角色编码，可为空
     * @param ticketType 单据类型，可为空
     * @return 规则列表
     */
    public List<SysDataScopeRuleDO> listRules(String roleCode, String ticketType) {
        LambdaQueryWrapper<SysDataScopeRuleDO> wrapper =
                Wrappers.lambdaQuery(SysDataScopeRuleDO.class);
        if (isNotBlank(roleCode)) {
            wrapper.eq(SysDataScopeRuleDO::getRoleCode, roleCode);
        }
        if (isNotBlank(ticketType)) {
            wrapper.eq(SysDataScopeRuleDO::getTicketType, ticketType);
        }
        wrapper.orderByAsc(SysDataScopeRuleDO::getRoleCode);
        wrapper.orderByAsc(SysDataScopeRuleDO::getTicketType);
        return sysDataScopeRuleMapper.selectList(wrapper);
    }

    /**
     * 新增规则。同一角色同一单据类型只允许一条。
     *
     * @param request 规则信息
     */
    @Transactional(rollbackFor = Exception.class)
    public void saveRule(DataScopeRuleSaveDTO request) {
        checkScopeType(request.getScopeType());
        if (countRules(request.getRoleCode(), request.getTicketType(), null) > 0) {
            throw new BizException("该角色下已存在此单据类型的规则："
                    + request.getRoleCode() + " / " + request.getTicketType());
        }

        SysDataScopeRuleDO rule = new SysDataScopeRuleDO();
        rule.setId(IdUtils.nextId());
        rule.setRoleCode(request.getRoleCode());
        rule.setTicketType(request.getTicketType());
        rule.setScopeType(request.getScopeType());
        rule.setReadable(request.getReadable() == null
                ? SystemConstants.FLAG_YES : request.getReadable());
        rule.setWritable(request.getWritable() == null
                ? SystemConstants.FLAG_YES : request.getWritable());
        rule.setEnabled(request.getEnabled() == null
                ? SystemConstants.FLAG_YES : request.getEnabled());
        rule.setExtraJson(request.getExtraJson());
        rule.setRemark(request.getRemark());
        String now = LocalDateTime.now().format(CommonConstants.DATE_TIME_FORMATTER);
        rule.setCreatedAt(now);
        rule.setUpdatedAt(now);
        sysDataScopeRuleMapper.insert(rule);
    }

    /**
     * 更新规则。roleCode 是唯一键的一部分但不允许改：要换角色就删了重建，避免误操作打乱权限。
     *
     * @param id      规则 ID
     * @param request 规则信息
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateRule(String id, DataScopeRuleSaveDTO request) {
        SysDataScopeRuleDO rule = sysDataScopeRuleMapper.selectById(id);
        if (rule == null) {
            throw new BizException("数据范围规则不存在");
        }
        checkScopeType(request.getScopeType());

        boolean ticketTypeChanged = isNotBlank(request.getTicketType())
                && !request.getTicketType().equals(rule.getTicketType());
        if (ticketTypeChanged
                && countRules(rule.getRoleCode(), request.getTicketType(), id) > 0) {
            throw new BizException("该角色下已存在此单据类型的规则："
                    + rule.getRoleCode() + " / " + request.getTicketType());
        }
        if (ticketTypeChanged) {
            rule.setTicketType(request.getTicketType());
        }
        if (isNotBlank(request.getScopeType())) {
            rule.setScopeType(request.getScopeType());
        }
        if (request.getReadable() != null) {
            rule.setReadable(request.getReadable());
        }
        if (request.getWritable() != null) {
            rule.setWritable(request.getWritable());
        }
        if (request.getEnabled() != null) {
            rule.setEnabled(request.getEnabled());
        }
        if (request.getExtraJson() != null) {
            rule.setExtraJson(request.getExtraJson());
        }
        if (request.getRemark() != null) {
            rule.setRemark(request.getRemark());
        }
        rule.setUpdatedAt(LocalDateTime.now().format(CommonConstants.DATE_TIME_FORMATTER));
        sysDataScopeRuleMapper.updateById(rule);
    }

    /**
     * 删除规则。本表无逻辑删除字段，做物理删除。
     *
     * @param id 规则 ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteRule(String id) {
        SysDataScopeRuleDO rule = sysDataScopeRuleMapper.selectById(id);
        if (rule == null) {
            throw new BizException("数据范围规则不存在");
        }
        sysDataScopeRuleMapper.deleteById(id);
    }

    /**
     * 校验数据范围类型合法性。
     *
     * @param scopeType 范围类型
     */
    private void checkScopeType(String scopeType) {
        if (!isNotBlank(scopeType)) {
            throw new BizException("scopeType 必填");
        }
        if (DataScopeTypeEnum.of(scopeType) == null) {
            throw new BizException("scopeType 非法：" + scopeType);
        }
    }

    /**
     * 判断同一角色下是否已有该单据类型的规则。
     *
     * @param roleCode   角色编码
     * @param ticketType 单据类型
     * @param excludeId  编辑场景传自身 ID
     * @return 命中记录数，大于 0 表示已存在
     */
    private long countRules(String roleCode, String ticketType, String excludeId) {
        LambdaQueryWrapper<SysDataScopeRuleDO> wrapper =
                Wrappers.lambdaQuery(SysDataScopeRuleDO.class);
        wrapper.eq(SysDataScopeRuleDO::getRoleCode, roleCode);
        wrapper.eq(SysDataScopeRuleDO::getTicketType, ticketType);
        if (isNotBlank(excludeId)) {
            wrapper.ne(SysDataScopeRuleDO::getId, excludeId);
        }
        return sysDataScopeRuleMapper.selectCount(wrapper);
    }

    /**
     * 判断字符串是否非空白。
     *
     * @param text 待判断字符串
     * @return true 表示非 null 且含非空白字符
     */
    private boolean isNotBlank(String text) {
        return text != null && !text.isBlank();
    }
}
