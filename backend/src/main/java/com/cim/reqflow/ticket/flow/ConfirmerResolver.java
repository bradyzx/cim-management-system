package com.cim.reqflow.ticket.flow;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.cim.reqflow.system.entity.SysDeptDO;
import com.cim.reqflow.system.entity.SysUserDeptDO;
import com.cim.reqflow.system.mapper.SysDeptMapper;
import com.cim.reqflow.system.mapper.SysUserDeptMapper;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 三方确认人解析：MIT 确认人、平台确认人、上级确认人。
 * <p>
 * 规则见 03 4.2.4，这里实现的是 v0.12 修正后的版本。原写法「公司级 MIT 负责人 =
 * dept_type='MIT' 且 parent_id 为空的那一条」在真实组织里找不到 —— MIT 开发组一定挂在
 * IT 中心下面，parent_id 永远不为空，照原写法制造部提的每一张单都会 mit_skipped=1，
 * MIT 确认这道关等于不存在。现改为「主部门类型匹配 → 否则取全公司该类型中层级最浅的」。
 * <p>
 * 上级确认人不同：它沿 parent_id 上溯最多 3 层，因为部门负责人本来就有缺位的情况，
 * 上溯是业务上认可的兜底。
 *
 * @author Haijie Xiao
 * @date 2026-11-04
 */
@Component
public class ConfirmerResolver {

    /** 部门类型：MIT */
    private static final String DEPT_TYPE_MIT = "MIT";

    /** 部门类型：平台 */
    private static final String DEPT_TYPE_PLATFORM = "PLATFORM";

    /** 部门状态：启用 */
    private static final String DEPT_STATUS_ACTIVE = "ACTIVE";

    /** 上级确认人最多上溯层级，防止脏数据成环导致死循环 */
    private static final int MAX_SUPERIOR_LEVELS = 3;

    /** 用户-部门关系表 */
    private final SysUserDeptMapper sysUserDeptMapper;

    /** 部门表 */
    private final SysDeptMapper sysDeptMapper;

    public ConfirmerResolver(SysUserDeptMapper sysUserDeptMapper, SysDeptMapper sysDeptMapper) {
        this.sysUserDeptMapper = sysUserDeptMapper;
        this.sysDeptMapper = sysDeptMapper;
    }

    /**
     * 解析三类确认人。
     *
     * @param reporterId 提出人 ID
     * @return 三个确认人 ID，任一无法解析时为 null（调用方据此写 *_skipped = 1）
     */
    public ConfirmerSet resolve(String reporterId) {
        SysDeptDO mainDept = findMainDept(reporterId);
        String mitUserId = findLeaderByDeptType(mainDept, DEPT_TYPE_MIT);
        String platformUserId = findLeaderByDeptType(mainDept, DEPT_TYPE_PLATFORM);
        String superiorUserId = findSuperior(mainDept, reporterId);
        return new ConfirmerSet(mitUserId, platformUserId, superiorUserId);
    }

    /**
     * 查提出人的主部门。主部门缺失时返回 null，不抛异常 ——
     * 组织数据不全不该让提单直接失败，后面的跳过逻辑会兜住。
     *
     * @param userId 用户 ID
     * @return 主部门；找不到返回 null
     */
    private SysDeptDO findMainDept(String userId) {
        List<SysUserDeptDO> relations = sysUserDeptMapper.selectList(
                Wrappers.<SysUserDeptDO>lambdaQuery()
                        .eq(SysUserDeptDO::getUserId, userId)
                        .eq(SysUserDeptDO::getIsPrimary, 1));
        if (relations.isEmpty()) {
            return null;
        }
        return sysDeptMapper.selectById(relations.get(0).getDeptId());
    }

    /**
     * 按部门类型找负责人：先看主部门本身是不是该类型，否则取全公司该类型中层级最浅的一条。
     * <p>
     * 不用 selectOne：多人多部门时可能查出多条，MyBatis-Plus 的 selectOne 会直接抛异常。
     * 也不用 last("limit 1")：那是 MySQL/SQLite 语法，换 Oracle 就失效。
     *
     * @param mainDept 提出人主部门，可为 null
     * @param deptType 目标部门类型
     * @return 负责人 ID；无匹配或负责人为空时返回 null
     */
    private String findLeaderByDeptType(SysDeptDO mainDept, String deptType) {
        if (mainDept != null && deptType.equals(mainDept.getDeptType())) {
            return mainDept.getLeaderUserId();
        }

        List<SysDeptDO> candidates = sysDeptMapper.selectList(
                Wrappers.<SysDeptDO>lambdaQuery()
                        .eq(SysDeptDO::getDeptType, deptType)
                        .eq(SysDeptDO::getStatus, DEPT_STATUS_ACTIVE)
                        .orderByAsc(SysDeptDO::getDeptLevel)
                        .orderByAsc(SysDeptDO::getDeptCode));
        if (candidates.isEmpty()) {
            return null;
        }
        return candidates.get(0).getLeaderUserId();
    }

    /**
     * 找上级确认人：主部门负责人 → 为空则沿 parent_id 上溯，最多 3 层。
     * <p>
     * 若找到的负责人就是提出人本人，直接返回 null（业务上「跳过该环节」），
     * 不再往上找 —— 本人已经是这个部门的负责人，再往上找就变成跨级越权了。
     *
     * @param mainDept   提出人主部门
     * @param reporterId 提出人 ID
     * @return 上级确认人；跳过或找不到时返回 null
     */
    private String findSuperior(SysDeptDO mainDept, String reporterId) {
        SysDeptDO current = mainDept;
        for (int level = 0; level < MAX_SUPERIOR_LEVELS; level++) {
            if (current == null) {
                return null;
            }
            String leaderUserId = current.getLeaderUserId();
            if (isNotBlank(leaderUserId)) {
                if (leaderUserId.equals(reporterId)) {
                    return null;
                }
                return leaderUserId;
            }
            if (current.getParentId() == null) {
                return null;
            }
            current = sysDeptMapper.selectById(current.getParentId());
        }
        return null;
    }

    private boolean isNotBlank(String text) {
        return text != null && !text.trim().isEmpty();
    }

    /**
     * 三类确认人的解析结果。
     * <p>
     * 用 record 而不是普通类：三个值只读、无需修改，省掉一套 getter/setter。
     * 为 null 表示该环节应跳过，调用方据此写 mit_skipped / platform_skipped / superior_skipped。
     *
     * @param mitUserId      MIT 确认人，可为 null
     * @param platformUserId 平台确认人，可为 null
     * @param superiorUserId 上级确认人，可为 null
     */
    public record ConfirmerSet(String mitUserId, String platformUserId, String superiorUserId) {
    }
}
