package com.cim.reqflow.ticket.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.cim.reqflow.common.enums.DataScopeTypeEnum;
import com.cim.reqflow.system.entity.SysDataScopeRuleDO;
import com.cim.reqflow.system.entity.SysDeptDO;
import com.cim.reqflow.system.entity.SysUserDeptDO;
import com.cim.reqflow.system.mapper.SysDataScopeRuleMapper;
import com.cim.reqflow.system.mapper.SysDeptMapper;
import com.cim.reqflow.system.mapper.SysUserDeptMapper;
import com.cim.reqflow.system.service.UserAccessService;
import com.cim.reqflow.ticket.entity.ReqTicketDO;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * 单据可见范围求值：把「角色 × 数据范围规则」翻译成查询条件。
 * <p>
 * 规则表（{@code sys_data_scope_rule}）只描述「哪个角色能看多大范围」，
 * 真正的条件在这里拼。之所以不让前端传范围：那等于把可见性交给客户端，
 * 改一个请求参数就能看到别人的单。
 * <p>
 * 多角色取**并集**：一个人同时是 Leader 和提出人，两边能看的都要能看。
 * 同一角色对同一单据类型只取一条规则，精确匹配优先于 {@code *} 兜底。
 * <p>
 * 五类范围的落地口径（对应 03 4.2.3）：
 * <ul>
 *   <li>{@code SELF}：我提的 + 我是 Owner 的</li>
 *   <li>{@code ASSIGNED}：派给我的 + 本部门尚未认领的</li>
 *   <li>{@code DEPT}：本部门的单</li>
 *   <li>{@code DEPT_AND_SUB}：本部门及下级的单</li>
 *   <li>{@code GROUP}：我作为负责人的部门（含下级）+ 全部待分配单</li>
 * </ul>
 *
 * @author Haijie Xiao
 * @date 2026-11-04
 */
@Service
public class TicketScopeResolver {

    /** 标记位：是 */
    private static final int FLAG_YES = 1;

    /** 规则表的通配单据类型 */
    private static final String WILDCARD = "*";

    /** 待分配状态码，Leader 必须能看到全部待分配单，否则跨部门提的需求派不出去 */
    private static final String STATUS_PENDING_ASSIGN = "PENDING_ASSIGN";

    /** 查不到任何人时用的假 ID，比返回「无条件」安全 */
    private static final String NOBODY = "__NOBODY__";

    private final UserAccessService userAccessService;

    private final SysDataScopeRuleMapper sysDataScopeRuleMapper;

    private final SysUserDeptMapper sysUserDeptMapper;

    private final SysDeptMapper sysDeptMapper;

    public TicketScopeResolver(UserAccessService userAccessService,
                               SysDataScopeRuleMapper sysDataScopeRuleMapper,
                               SysUserDeptMapper sysUserDeptMapper,
                               SysDeptMapper sysDeptMapper) {
        this.userAccessService = userAccessService;
        this.sysDataScopeRuleMapper = sysDataScopeRuleMapper;
        this.sysUserDeptMapper = sysUserDeptMapper;
        this.sysDeptMapper = sysDeptMapper;
    }

    /**
     * 把可见范围条件追加到查询上。
     *
     * @param wrapper    查询条件
     * @param userId     当前用户
     * @param ticketType 单据类型，用于选规则
     */
    public void applyReadableScope(LambdaQueryWrapper<ReqTicketDO> wrapper, String userId,
                                   String ticketType) {
        if (userId == null || userId.isEmpty()) {
            wrapper.eq(ReqTicketDO::getId, NOBODY);
            return;
        }

        Set<String> scopes = resolveScopes(userId, ticketType);
        if (scopes.contains(DataScopeTypeEnum.ALL.getCode())) {
            return;
        }
        // 一条规则都没配时兜底到「只看自己的」。
        // 反过来（没配就全放开）是安全事故，宁可让人看不到再去提配置。
        if (scopes.isEmpty()) {
            scopes = Set.of(DataScopeTypeEnum.SELF.getCode());
        }

        String primaryDeptId = resolvePrimaryDeptId(userId);
        List<Consumer<LambdaQueryWrapper<ReqTicketDO>>> conditions = new ArrayList<>();
        for (String scope : scopes) {
            Consumer<LambdaQueryWrapper<ReqTicketDO>> condition =
                    buildCondition(scope, userId, primaryDeptId);
            if (condition != null) {
                conditions.add(condition);
            }
        }

        if (conditions.isEmpty()) {
            wrapper.eq(ReqTicketDO::getId, NOBODY);
            return;
        }
        wrapper.and(outer -> {
            for (int index = 0; index < conditions.size(); index++) {
                if (index > 0) {
                    outer.or();
                }
                conditions.get(index).accept(outer);
            }
        });
    }

    /**
     * 按范围类型构造一个条件块。返回 null 表示该范围在当前配置下无意义，直接跳过。
     *
     * @param scope        范围类型
     * @param userId       当前用户
     * @param primaryDeptId 主部门
     * @return 条件块
     */
    private Consumer<LambdaQueryWrapper<ReqTicketDO>> buildCondition(String scope, String userId,
                                                                     String primaryDeptId) {
        switch (scope) {
            case "SELF":
                // 提出人与 Owner 都算「自己的」：代录场景下两者不是同一个人
                return outer -> outer.nested(w -> w
                        .eq(ReqTicketDO::getReporterId, userId)
                        .or()
                        .eq(ReqTicketDO::getOwnerId, userId));
            case "ASSIGNED":
                if (primaryDeptId == null) {
                    return outer -> outer.eq(ReqTicketDO::getAssigneeId, userId);
                }
                return outer -> outer.nested(w -> w
                        .eq(ReqTicketDO::getAssigneeId, userId)
                        .or(inner -> inner
                                .isNull(ReqTicketDO::getAssigneeId)
                                .eq(ReqTicketDO::getDeptId, primaryDeptId)));
            case "DEPT":
                if (primaryDeptId == null) {
                    return null;
                }
                return outer -> outer.eq(ReqTicketDO::getDeptId, primaryDeptId);
            case "DEPT_AND_SUB":
                if (primaryDeptId == null) {
                    return null;
                }
                Set<String> selfTree = collectDeptTree(primaryDeptId);
                return outer -> outer.in(ReqTicketDO::getDeptId, selfTree);
            case "GROUP":
                Set<String> leaderTree = collectLeaderDeptTree(userId);
                Set<String> memberIds = collectDeptMemberIds(leaderTree);
                // 三类条件并集：本组提出的、本组成员承办的、全部待分配单。
                // 第三类不能少：需求常由业务部门提出，只按提出部门过滤会让跨部门提的单派不出去。
                // 第二类也不能少：Leader 要盯组员进度，只看「本组提的」会导致组员手上的单他一张都看不到。
                return outer -> outer.nested(w -> {
                    if (!leaderTree.isEmpty()) {
                        w.in(ReqTicketDO::getDeptId, leaderTree).or();
                    }
                    if (!memberIds.isEmpty()) {
                        w.in(ReqTicketDO::getAssigneeId, memberIds).or();
                    }
                    w.isNull(ReqTicketDO::getAssigneeId)
                            .eq(ReqTicketDO::getStatus, STATUS_PENDING_ASSIGN);
                });
            default:
                // CUSTOM 一期没有配置入口，NONE 表示看不到，两者都不产生条件
                return null;
        }
    }

    /**
     * 求当前用户对该单据类型的可见范围集合。
     *
     * @param userId     用户
     * @param ticketType 单据类型
     * @return 范围类型集合
     */
    private Set<String> resolveScopes(String userId, String ticketType) {
        List<String> roleCodes = userAccessService.listRoleCodes(userId);
        if (roleCodes.isEmpty()) {
            return Set.of();
        }
        List<SysDataScopeRuleDO> rules = sysDataScopeRuleMapper.selectList(
                Wrappers.<SysDataScopeRuleDO>lambdaQuery()
                        .in(SysDataScopeRuleDO::getRoleCode, roleCodes)
                        .eq(SysDataScopeRuleDO::getEnabled, FLAG_YES)
                        .eq(SysDataScopeRuleDO::getReadable, FLAG_YES)
                        .and(w -> w.eq(SysDataScopeRuleDO::getTicketType, ticketType)
                                .or()
                                .eq(SysDataScopeRuleDO::getTicketType, WILDCARD)));

        // 同一角色可能同时命中「精确」和「*」两条规则，精确的那条优先
        Map<String, SysDataScopeRuleDO> perRoleBest = new HashMap<>(rules.size());
        for (SysDataScopeRuleDO rule : rules) {
            SysDataScopeRuleDO existing = perRoleBest.get(rule.getRoleCode());
            if (existing == null || WILDCARD.equals(existing.getTicketType())) {
                perRoleBest.put(rule.getRoleCode(), rule);
            }
        }

        Set<String> scopes = new HashSet<>(perRoleBest.size());
        for (SysDataScopeRuleDO rule : perRoleBest.values()) {
            if (rule.getScopeType() != null && !rule.getScopeType().isEmpty()) {
                scopes.add(rule.getScopeType());
            }
        }
        return scopes;
    }

    /**
     * 取用户主部门。
     *
     * @param userId 用户
     * @return 部门 ID，没有主部门时返回 null
     */
    private String resolvePrimaryDeptId(String userId) {
        SysUserDeptDO relation = sysUserDeptMapper.selectOne(
                Wrappers.<SysUserDeptDO>lambdaQuery()
                        .eq(SysUserDeptDO::getUserId, userId)
                        .eq(SysUserDeptDO::getIsPrimary, FLAG_YES)
                        .last("limit 1"));
        return relation == null ? null : relation.getDeptId();
    }

    /**
     * 取某部门及其全部下级。
     *
     * @param deptId 部门 ID
     * @return 部门 ID 集合
     */
    private Set<String> collectDeptTree(String deptId) {
        Set<String> deptIds = new HashSet<>();
        deptIds.add(deptId);
        // ancestors 是 "/根/父/当前/" 形式的路径，用它一次查出全部下级，不做递归
        List<SysDeptDO> children = sysDeptMapper.selectList(
                Wrappers.<SysDeptDO>lambdaQuery()
                        .like(SysDeptDO::getAncestors, "/" + deptId + "/"));
        for (SysDeptDO child : children) {
            deptIds.add(child.getId());
        }
        return deptIds;
    }

    /**
     * 取部门集合内的全部成员。
     * <p>
     * 返回空集合时调用方必须跳过这个条件：MyBatis-Plus 对空集合会拼出 {@code IN ()}，
     * SQLite 直接语法错误。
     *
     * @param deptIds 部门 ID 集合
     * @return 用户 ID 集合
     */
    private Set<String> collectDeptMemberIds(Set<String> deptIds) {
        if (deptIds.isEmpty()) {
            return Set.of();
        }
        List<SysUserDeptDO> relations = sysUserDeptMapper.selectList(
                Wrappers.<SysUserDeptDO>lambdaQuery()
                        .in(SysUserDeptDO::getDeptId, deptIds));
        Set<String> userIds = new HashSet<>(relations.size());
        for (SysUserDeptDO relation : relations) {
            if (relation.getUserId() != null && !relation.getUserId().isEmpty()) {
                userIds.add(relation.getUserId());
            }
        }
        return userIds;
    }

    /**
     * 取该用户作为负责人的部门及其全部下级。
     *
     * @param userId 用户
     * @return 部门 ID 集合
     */
    private Set<String> collectLeaderDeptTree(String userId) {
        List<SysDeptDO> ledDepts = sysDeptMapper.selectList(
                Wrappers.<SysDeptDO>lambdaQuery()
                        .eq(SysDeptDO::getLeaderUserId, userId));
        Set<String> deptIds = new HashSet<>();
        for (SysDeptDO dept : ledDepts) {
            deptIds.addAll(collectDeptTree(dept.getId()));
        }
        return deptIds;
    }
}
