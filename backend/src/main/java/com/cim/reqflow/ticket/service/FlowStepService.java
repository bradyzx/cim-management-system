package com.cim.reqflow.ticket.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.cim.reqflow.common.BizException;
import com.cim.reqflow.system.entity.SysDictDataDO;
import com.cim.reqflow.system.entity.SysRoleDO;
import com.cim.reqflow.system.entity.SysUserDO;
import com.cim.reqflow.system.entity.SysUserRoleDO;
import com.cim.reqflow.system.mapper.SysDictDataMapper;
import com.cim.reqflow.system.mapper.SysRoleMapper;
import com.cim.reqflow.system.mapper.SysUserMapper;
import com.cim.reqflow.system.mapper.SysUserRoleMapper;
import com.cim.reqflow.ticket.entity.ReqAttachRuleDO;
import com.cim.reqflow.ticket.entity.ReqAttachmentDO;
import com.cim.reqflow.ticket.entity.ReqFlowLogDO;
import com.cim.reqflow.ticket.entity.ReqTicketDO;
import com.cim.reqflow.ticket.flow.FlowRoleSets;
import com.cim.reqflow.ticket.flow.FlowStepCatalog;
import com.cim.reqflow.ticket.flow.StageGuide;
import com.cim.reqflow.ticket.mapper.ReqAttachRuleMapper;
import com.cim.reqflow.ticket.mapper.ReqAttachmentMapper;
import com.cim.reqflow.ticket.mapper.ReqFlowLogMapper;
import com.cim.reqflow.ticket.mapper.ReqTicketMapper;
import com.cim.reqflow.ticket.vo.FlowStepVO;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 步骤条数据：把「这一步谁在处理、交了什么、还缺什么、交给谁」按单据算出来。
 *
 * <b>为什么按单据算而不是给一份固定文案</b>：同样停在第 5 步，A 单是李四在设计、
 * 材料齐了；B 单还是王五没派单。固定文案只能写「由 MIT 开发处理」，用户看完还是不知道
 * 该催谁。这里的人来自流转日志与单据归属，是人名而不是岗位。
 *
 * <b>人是哪来的</b>：
 * <ul>
 *   <li>已走过的步骤 —— 取该步对应状态上的流转日志，谁点的就是谁；</li>
 *   <li>还没走到的步骤 —— 按角色推算：提出人是单据上的提出人，开发是承办人，
 *       Leader/平台/评审/IT 按角色表查人。推算可能和最终接手的不是一个人
 *       （比如派单时改派给别人），所以前端必须区分「处理」与「预计」两种说法。</li>
 * </ul>
 *
 * <b>材料分三份</b>：需要（流程定义上该交什么）、已上传（这单实际传了什么）、
 * 还缺（必填规则里没满足的）。前一份来自 {@link StageGuide}，后两份来自附件表与
 * {@code req_attach_rule} —— 与流转拦截用的是同一张规则表，不会出现「页面说齐了、
 * 保存时被拦」。
 *
 * @author Haijie Xiao
 * @date 2026-11-05
 */
@Service
public class FlowStepService {

    /** 进度：已走过 */
    private static final String STATE_DONE = "DONE";

    /** 进度：停在这里 */
    private static final String STATE_CURRENT = "CURRENT";

    /** 进度：还没到 */
    private static final String STATE_TODO = "TODO";

    /** 系统动作没有操作人，显示这个 */
    private static final String SYSTEM_LABEL = "系统";

    /** 查不到用户时兜底，宁可显示这句也不要露出 32 位 ID */
    private static final String UNKNOWN_USER = "未知用户";

    /** 字典类型：附件类型 */
    private static final String DICT_ATTACH_TYPE = "attach_type";

    /** 通配符：规则里的 `*` 表示所有状态 */
    private static final String WILDCARD = "*";

    /** 启用 / 是 */
    private static final int FLAG_YES = 1;

    /** 附件未删除 */
    private static final int NOT_DELETED = 0;

    private final ReqTicketMapper reqTicketMapper;

    private final ReqFlowLogMapper reqFlowLogMapper;

    private final ReqAttachmentMapper reqAttachmentMapper;

    private final ReqAttachRuleMapper reqAttachRuleMapper;

    private final SysUserMapper sysUserMapper;

    private final SysUserRoleMapper sysUserRoleMapper;

    private final SysRoleMapper sysRoleMapper;

    private final SysDictDataMapper sysDictDataMapper;

    public FlowStepService(ReqTicketMapper reqTicketMapper,
                           ReqFlowLogMapper reqFlowLogMapper,
                           ReqAttachmentMapper reqAttachmentMapper,
                           ReqAttachRuleMapper reqAttachRuleMapper,
                           SysUserMapper sysUserMapper,
                           SysUserRoleMapper sysUserRoleMapper,
                           SysRoleMapper sysRoleMapper,
                           SysDictDataMapper sysDictDataMapper) {
        this.reqTicketMapper = reqTicketMapper;
        this.reqFlowLogMapper = reqFlowLogMapper;
        this.reqAttachmentMapper = reqAttachmentMapper;
        this.reqAttachRuleMapper = reqAttachRuleMapper;
        this.sysUserMapper = sysUserMapper;
        this.sysUserRoleMapper = sysUserRoleMapper;
        this.sysRoleMapper = sysRoleMapper;
        this.sysDictDataMapper = sysDictDataMapper;
    }

    /**
     * 组装一张单的步骤条数据。
     *
     * @param ticketId 单据 ID
     * @return 10 个步骤，按流转顺序
     */
    public List<FlowStepVO> build(String ticketId) {
        ReqTicketDO ticket = reqTicketMapper.selectById(ticketId);
        if (ticket == null) {
            throw new BizException("单据不存在或已删除");
        }

        // 四份数据一次取回：日志按时间升序（后面按顺序取人，不排序会得到乱序人名）
        List<ReqFlowLogDO> logs = reqFlowLogMapper.selectList(
                Wrappers.<ReqFlowLogDO>lambdaQuery()
                        .eq(ReqFlowLogDO::getTicketId, ticketId)
                        .orderByAsc(ReqFlowLogDO::getCreatedAt));
        List<ReqAttachmentDO> attachments = reqAttachmentMapper.selectList(
                Wrappers.<ReqAttachmentDO>lambdaQuery()
                        .eq(ReqAttachmentDO::getTicketId, ticketId)
                        .eq(ReqAttachmentDO::getDeleted, NOT_DELETED));
        Map<String, String> userLabels = 载入用户标签();
        Set<String> 管理员 = 载入管理员用户();
        Map<String, List<String>> roleUsers = 载入角色用户(userLabels, 管理员);
        Map<String, String> attachTypeLabels = 载入附件类型名();
        List<ReqAttachRuleDO> rules = 载入附件规则(ticket.getTicketType());

        // 挂起的单停在旁支上，主链位置要按挂起前的状态算，否则整条步骤条都灰掉
        String 定位状态 = ticket.getStatus();
        if (FlowStepCatalog.indexOfState(定位状态) < 0 && ticket.getSuspendedFrom() != null) {
            定位状态 = ticket.getSuspendedFrom();
        }
        int 当前步 = FlowStepCatalog.indexOfState(定位状态);

        List<FlowStepVO> result = new ArrayList<>();
        for (int i = 0; i < FlowStepCatalog.STEPS.size(); i++) {
            FlowStepCatalog step = FlowStepCatalog.STEPS.get(i);
            FlowStepVO vo = new FlowStepVO();
            vo.setKey(step.key());
            vo.setLabel(step.label());
            vo.setNodeCodes(step.nodeCodes());
            vo.setState(进度(i, 当前步, ticket.getStatus()));
            vo.setHandlers(实际处理人(logs, step, userLabels, 管理员));
            vo.setPlannedHandlers(预计处理人(step, ticket, userLabels, roleUsers, 管理员));
            vo.setNeed(需要材料(step));
            vo.setUploaded(已上传(step, attachments, attachTypeLabels));
            vo.setMissing(还缺(step, attachments, rules, attachTypeLabels,
                    STATE_CURRENT.equals(vo.getState())));
            result.add(vo);
        }

        // 接手人要等十步都算完才填：下一步的实际处理人可能还没算出来
        回填接手人(result);
        return result;
    }

    /**
     * 这一步在整条链上的位置。
     * <p>
     * 已关闭的单十步全算走完，包括最后一步 —— 否则关闭单的最后一步会显示成「进行中」。
     *
     * @param index   步骤下标
     * @param current 当前步下标，-1 表示状态不在主链上
     * @param status  单据当前状态
     * @return DONE / CURRENT / TODO
     */
    private String 进度(int index, int current, String status) {
        if ("CLOSED".equals(status)) {
            return STATE_DONE;
        }
        if (current < 0) {
            return STATE_TODO;
        }
        if (index < current) {
            return STATE_DONE;
        }
        return index == current ? STATE_CURRENT : STATE_TODO;
    }

    /**
     * 该步实际处理过的人，按操作时间先后去重。
     * <p>
     * 归属按<b>动作发生时的状态</b>（from_status）判断，不按到达状态：
     * 「用户设计评审通过」这条日志的 to_status 是 DEVELOPING，按到达状态会把提出人算成
     * 开发这一步的处理人 —— 他只是评审，活还没开始。按 from_status 归到设计评审，
     * 才是他真正干的事。开发那一步因此会显示「还没人处理，预计李四」，这才是实情。
     *
     * @param logs       全部流转日志
     * @param step       步骤定义
     * @param userLabels 用户 ID → 姓名
     * @param 管理员     系统管理员的用户 ID，展示时整体跳过
     * @return 人名列表，可能是空（还没人到过）
     */
    private List<String> 实际处理人(List<ReqFlowLogDO> logs, FlowStepCatalog step,
                                    Map<String, String> userLabels, Set<String> 管理员) {
        LinkedHashSet<String> 结果 = new LinkedHashSet<>();
        for (ReqFlowLogDO 一条 : logs) {
            if (!step.states().contains(一条.getFromStatus())) {
                continue;
            }
            // 管理员代点的操作不进卡片：演示数据里上级确认是 admin 代签的，
            // 写进「处理」「交给」会让人以为流程真流到了运维手上
            if (管理员.contains(一条.getOperatorId())) {
                continue;
            }
            结果.add(人名(userLabels, 一条.getOperatorId()));
        }
        return new ArrayList<>(结果);
    }

    /**
     * 还没走到时，预计由谁处理。
     * <p>
     * 提出人取单据上的提出人，开发取承办人，其余按角色表查。
     * 上级确认优先用单据上记的 superior_id —— 三方确认走的是这位，角色表里查出来的
     * 部门负责人不一定是他。
     *
     * @param step       步骤定义
     * @param ticket     单据
     * @param userLabels 用户 ID → 姓名
     * @param roleUsers  角色码 → 人名列表
     * @param 管理员     系统管理员的用户 ID，展示时整体跳过
     * @return 人名列表，去重后保持角色声明顺序
     */
    private List<String> 预计处理人(FlowStepCatalog step, ReqTicketDO ticket,
                                    Map<String, String> userLabels,
                                    Map<String, List<String>> roleUsers,
                                    Set<String> 管理员) {
        LinkedHashSet<String> 结果 = new LinkedHashSet<>();
        for (String role : step.plannedRoles()) {
            if (FlowRoleSets.REPORTER.equals(role)) {
                加人名(结果, userLabels, ticket.getReporterId(), 管理员);
                continue;
            }
            if (FlowRoleSets.MIT_DEV.equals(role)) {
                加人名(结果, userLabels, ticket.getAssigneeId(), 管理员);
                continue;
            }
            if (FlowRoleSets.DEPT_MANAGER.equals(role) && ticket.getSuperiorId() != null) {
                加人名(结果, userLabels, ticket.getSuperiorId(), 管理员);
                continue;
            }
            结果.addAll(roleUsers.getOrDefault(role, List.of()));
        }
        return new ArrayList<>(结果);
    }

    /**
     * 往结果里加一个人，单据字段为空或用户查不到时直接跳过。
     * <p>
     * 不能复用 {@link #人名}：那里是给「已经发生的事」兜底（没人就是系统），
     * 这里是「预计谁来」，人没定就该什么都不显示，写「系统」会让人以为系统会自动处理。
     *
     * @param 结果       收集容器
     * @param userLabels 用户 ID → 姓名
     * @param userId     用户 ID，可为空
     * @param 管理员     系统管理员的用户 ID，展示时整体跳过
     */
    private void 加人名(LinkedHashSet<String> 结果, Map<String, String> userLabels,
                       String userId, Set<String> 管理员) {
        if (userId == null || userId.isEmpty() || 管理员.contains(userId)) {
            return;
        }
        String 人名 = userLabels.get(userId);
        if (人名 != null) {
            结果.add(人名);
        }
    }

    /**
     * 流程定义上这一步要交什么。
     *
     * @param step 步骤定义
     * @return 材料说明，同一步的多个状态可能给出多条
     */
    private List<String> 需要材料(FlowStepCatalog step) {
        LinkedHashSet<String> 结果 = new LinkedHashSet<>();
        for (String status : step.states()) {
            StageGuide 指引 = StageGuide.of(status);
            if (指引 != null && 指引.need() != null && !指引.need().isEmpty()) {
                结果.add(指引.need());
            }
        }
        return new ArrayList<>(结果);
    }

    /**
     * 这一步已经传了什么，按「文件名（类型）」列出。
     *
     * @param step             步骤定义
     * @param attachments      该单全部附件
     * @param attachTypeLabels 附件类型码 → 中文名
     * @return 已上传清单
     */
    private List<String> 已上传(FlowStepCatalog step, List<ReqAttachmentDO> attachments,
                                Map<String, String> attachTypeLabels) {
        List<String> 结果 = new ArrayList<>();
        for (ReqAttachmentDO 附件 : attachments) {
            if (!step.states().contains(附件.getStage())) {
                continue;
            }
            String 类型 = attachTypeLabels.getOrDefault(
                    附件.getAttachType(), 附件.getAttachType());
            结果.add(附件.getOriginalName() + "（" + 类型 + "）");
        }
        return 结果;
    }

    /**
     * 必填但还没交的材料。
     * <p>
     * 规则表按状态配，这里只取落在本步状态里的规则；通配规则（status = `*`，如 BUG 单的截图）
     * 只在当前步提示一次，否则十步里每步都要显示一遍，反而看不出是哪一步要的。
     *
     * @param step             步骤定义
     * @param attachments      该单全部附件
     * @param rules            该单据类型的全部启用规则
     * @param attachTypeLabels 附件类型码 → 中文名
     * @param current          是否当前步
     * @return 还缺的材料类型名
     */
    private List<String> 还缺(FlowStepCatalog step, List<ReqAttachmentDO> attachments,
                              List<ReqAttachRuleDO> rules, Map<String, String> attachTypeLabels,
                              boolean current) {
        LinkedHashSet<String> 结果 = new LinkedHashSet<>();
        for (ReqAttachRuleDO 规则 : rules) {
            boolean 本步规则 = step.states().contains(规则.getStatus());
            boolean 通配规则 = WILDCARD.equals(规则.getStatus()) && current;
            if (!本步规则 && !通配规则) {
                continue;
            }
            if (规则.getRequired() == null || 规则.getRequired() != FLAG_YES) {
                continue;
            }

            int 已传份数 = 0;
            for (ReqAttachmentDO 附件 : attachments) {
                if (附件.getAttachType().equals(规则.getAttachType())) {
                    已传份数++;
                }
            }
            int 需要份数 = 规则.getMinCount() == null ? 1 : 规则.getMinCount();
            if (已传份数 < 需要份数) {
                结果.add(attachTypeLabels.getOrDefault(
                        规则.getAttachType(), 规则.getAttachType()));
            }
        }
        return new ArrayList<>(结果);
    }

    /**
     * 把「交给谁」填上：取下一步的实际处理人，没人处理过就取预计人。
     * <p>
     * 必须十步全算完再填，因为下一步的处理人本身也是算出来的。
     *
     * @param steps 已组装好的步骤
     */
    private void 回填接手人(List<FlowStepVO> steps) {
        for (int i = 0; i < steps.size() - 1; i++) {
            FlowStepVO 下一步 = steps.get(i + 1);
            List<String> 接手 = 下一步.getHandlers().isEmpty()
                    ? 下一步.getPlannedHandlers()
                    : 下一步.getHandlers();
            steps.get(i).setHandoff(new ArrayList<>(接手));
        }
        // 最后一步是终点，不再交给谁
        steps.get(steps.size() - 1).setHandoff(List.of());
    }

    /**
     * 用户 ID 取人话姓名。系统动作没有操作人，统一记「系统」。
     *
     * @param userLabels 用户 ID → 姓名
     * @param userId     用户 ID，可为空
     * @return 人名
     */
    private String 人名(Map<String, String> userLabels, String userId) {
        if (userId == null || userId.isEmpty()) {
            return SYSTEM_LABEL;
        }
        return userLabels.getOrDefault(userId, UNKNOWN_USER);
    }

    /**
     * 载入全部在职用户，ID → 姓名。
     * <p>
     * 只放姓名，不带账号：悬停卡片是给人看的，`张三（zhangsan）` 后半截全是噪声，
     * 卡片还会被撑成两行。重名不做区分 —— 这个系统里同名同姓的人由部门区分，
     * 卡片上挤部门名比挤账号更糟。
     * <p>
     * 全量取回而不是按 ID 逐个查：一屏十步最多十几个不同的人，逐个查要十几次往返；
     * 用户表在这个系统里是小表，一次取回更省事。
     *
     * @return 用户姓名表
     */
    private Map<String, String> 载入用户标签() {
        Map<String, String> 结果 = new HashMap<>();
        List<SysUserDO> users = sysUserMapper.selectList(
                Wrappers.<SysUserDO>lambdaQuery().eq(SysUserDO::getStatus, "ACTIVE"));
        for (SysUserDO 用户 : users) {
            String 姓名 = 用户.getRealName();
            if (姓名 == null || 姓名.isEmpty()) {
                姓名 = 用户.getUsername();
            }
            结果.put(用户.getId(), 姓名);
        }
        return 结果;
    }

    /**
     * 角色码 → 该角色下的全部人名。
     * <p>
     * 同一角色可能有多人（两个 Leader、两个开发），所以是列表；前端一个个列出来，
     * 不取第一个 —— 猜错人比列出一排更糟。管理员会被剔掉：他持有的是 ADMIN 角色，
     * 但万一以后给某人兼了业务角色，卡片上也不该冒出「系统管理员」。
     *
     * @param userLabels 用户 ID → 姓名
     * @param 管理员     系统管理员的用户 ID
     * @return 角色码到人名的映射
     */
    private Map<String, List<String>> 载入角色用户(Map<String, String> userLabels,
                                                 Set<String> 管理员) {
        Map<String, String> roleIdToCode = new HashMap<>();
        for (SysRoleDO 角色 : sysRoleMapper.selectList(
                Wrappers.<SysRoleDO>lambdaQuery().eq(SysRoleDO::getStatus, "ACTIVE"))) {
            roleIdToCode.put(角色.getId(), 角色.getRoleCode());
        }

        Map<String, List<String>> 结果 = new LinkedHashMap<>();
        for (SysUserRoleDO 关联 : sysUserRoleMapper.selectList(
                Wrappers.<SysUserRoleDO>lambdaQuery())) {
            String 角色码 = roleIdToCode.get(关联.getRoleId());
            String 人名 = userLabels.get(关联.getUserId());
            if (角色码 == null || 人名 == null || 管理员.contains(关联.getUserId())) {
                continue;
            }
            结果.computeIfAbsent(角色码, key -> new ArrayList<>()).add(人名);
        }
        return 结果;
    }

    /**
     * 系统管理员的用户 ID。
     * <p>
     * 他不是流程角色：演示数据里上级确认是 admin 代签的（单据上 superior_id 为空），
     * 若照实显示，卡片上会写「交给 系统管理员」，看的人会以为单子流到了运维手上。
     * 这类代操作对流程判断没有价值，统一在展示层剔掉 —— 审计要看谁点的，
     * 去流转时间线看，那里一条不少。
     *
     * @return 管理员用户 ID 集合
     */
    private Set<String> 载入管理员用户() {
        Set<String> 结果 = new HashSet<>();
        List<SysRoleDO> 管理员角色 = sysRoleMapper.selectList(
                Wrappers.<SysRoleDO>lambdaQuery()
                        .eq(SysRoleDO::getRoleCode, FlowRoleSets.ADMIN)
                        .eq(SysRoleDO::getStatus, "ACTIVE"));
        for (SysRoleDO 角色 : 管理员角色) {
            for (SysUserRoleDO 关联 : sysUserRoleMapper.selectList(
                    Wrappers.<SysUserRoleDO>lambdaQuery()
                            .eq(SysUserRoleDO::getRoleId, 角色.getId()))) {
                结果.add(关联.getUserId());
            }
        }
        return 结果;
    }

    /**
     * 附件类型码 → 中文名，用于把 `FDS` 显示成「功能设计文档」。
     *
     * @return 字典映射
     */
    private Map<String, String> 载入附件类型名() {
        Map<String, String> 结果 = new HashMap<>();
        for (SysDictDataDO 字典 : sysDictDataMapper.selectList(
                Wrappers.<SysDictDataDO>lambdaQuery()
                        .eq(SysDictDataDO::getDictType, DICT_ATTACH_TYPE))) {
            结果.put(字典.getDictValue(), 字典.getDictLabel());
        }
        return 结果;
    }

    /**
     * 该单据类型的全部启用附件规则。
     * <p>
     * 一次取回再按状态筛，而不是每步查一次库：十步就是十次往返，
     * 而这张表通常不到二十行。
     *
     * @param ticketType 单据类型
     * @return 规则列表
     */
    private List<ReqAttachRuleDO> 载入附件规则(String ticketType) {
        return reqAttachRuleMapper.selectList(
                Wrappers.<ReqAttachRuleDO>lambdaQuery()
                        .eq(ReqAttachRuleDO::getEnabled, FLAG_YES)
                        .and(wrapper -> wrapper
                                .eq(ReqAttachRuleDO::getTicketType, ticketType)
                                .or()
                                .eq(ReqAttachRuleDO::getTicketType, WILDCARD)));
    }
}
