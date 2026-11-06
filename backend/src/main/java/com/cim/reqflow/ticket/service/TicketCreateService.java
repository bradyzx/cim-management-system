package com.cim.reqflow.ticket.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.cim.reqflow.common.BizCodeException;
import com.cim.reqflow.common.BizException;
import com.cim.reqflow.common.util.IdUtils;
import com.cim.reqflow.system.entity.ReqSlaConfigDO;
import com.cim.reqflow.system.entity.SysNoSequenceDO;
import com.cim.reqflow.system.entity.SysUserDO;
import com.cim.reqflow.system.mapper.ReqSlaConfigMapper;
import com.cim.reqflow.system.mapper.SysNoSequenceMapper;
import com.cim.reqflow.system.mapper.SysUserMapper;
import com.cim.reqflow.ticket.doc.DocMarkdown;
import com.cim.reqflow.ticket.doc.DocSections;
import com.cim.reqflow.ticket.doc.DocValidator;
import com.cim.reqflow.ticket.dto.TicketCreateDTO;
import com.cim.reqflow.ticket.entity.ReqAcceptanceCriteriaDO;
import com.cim.reqflow.ticket.entity.ReqDocDO;
import com.cim.reqflow.ticket.entity.ReqFlowLogDO;
import com.cim.reqflow.ticket.entity.ReqProjectDO;
import com.cim.reqflow.ticket.entity.ReqSystemDO;
import com.cim.reqflow.ticket.entity.ReqTicketDO;
import com.cim.reqflow.ticket.flow.TicketStatus;
import com.cim.reqflow.ticket.mapper.ReqAcceptanceCriteriaMapper;
import com.cim.reqflow.ticket.mapper.ReqDocMapper;
import com.cim.reqflow.ticket.mapper.ReqFlowLogMapper;
import com.cim.reqflow.ticket.mapper.ReqProjectMapper;
import com.cim.reqflow.ticket.mapper.ReqSystemMapper;
import com.cim.reqflow.ticket.mapper.ReqTicketMapper;
import com.cim.reqflow.ticket.sla.SlaCalculator;
import com.cim.reqflow.ticket.vo.TicketCreatedVO;
import com.cim.reqflow.ticket.vo.TicketDetailVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 新建单据。
 * <p>
 * 提交即完成三件事（02 第三节的流程，一期 AI 为规则版）：
 * <ol>
 *   <li><b>查重</b>：字符二元组算相似度，最高加权值 &gt; 0.5 且提出人没勾
 *       「仍要新建 + 原因」时拒绝建单，把相似需求列表带回给前端展示 ——
 *       用户看完列表再决定，比事后翻重单强；</li>
 *   <li><b>建单</b>：发号器取号，状态直接到 {@code REQ_DOC_DRAFTED}，
 *       时间线写 SUBMIT 与 AI_ANALYZE 两条，与演示数据的形状一致；</li>
 *   <li><b>生成需求文档</b>：规则模板从表单字段填充 12 节骨架，
 *       验收标准同步拆条落子表 —— 提出人只写核心字段，文档不是他的活。</li>
 * </ol>
 * <p>
 * 「一个项目 → 多个系统 → 多个需求」的层级在这里落死：
 * system_id 必须属于所选 project_id，system_code 只作快照。
 *
 * @author Haijie Xiao
 * @date 2026-11-04
 */
@Service
public class TicketCreateService {

    private static final Logger log = LoggerFactory.getLogger(TicketCreateService.class);

    /** 查重命中的业务码，前端据此展示相似列表 */
    public static final String BIZ_DUP_FOUND = "DUP_FOUND";

    /** 一天的开始时刻，给只有日期的 expectDueDate 补齐 */
    private static final String DAY_START = " 00:00:00";

    /** 时间格式，与全库一致 */
    private static final DateTimeFormatter 时间格式 = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** AI 规则模板的模型标识，写进 req_doc 便于区分二期接入的真模型 */
    private static final String 规则模板模型 = "RULE_TEMPLATE_V1";

    /** 构造 12 节 JSON，线程安全 */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** SLA 未配置时的时限（不计时） */
    private static final double 无时限 = 0.0;

    private final ReqTicketMapper reqTicketMapper;

    private final ReqFlowLogMapper reqFlowLogMapper;

    private final ReqProjectMapper reqProjectMapper;

    private final ReqSystemMapper reqSystemMapper;

    private final ReqDocMapper reqDocMapper;

    private final ReqAcceptanceCriteriaMapper reqAcceptanceCriteriaMapper;

    private final ReqSlaConfigMapper reqSlaConfigMapper;

    private final SysUserMapper sysUserMapper;

    private final SysNoSequenceMapper sysNoSequenceMapper;

    private final TicketNoGenerator ticketNoGenerator;

    private final TicketDupChecker ticketDupChecker;

    private final SlaCalculator slaCalculator;

    public TicketCreateService(ReqTicketMapper reqTicketMapper,
                               ReqFlowLogMapper reqFlowLogMapper,
                               ReqProjectMapper reqProjectMapper,
                               ReqSystemMapper reqSystemMapper,
                               ReqDocMapper reqDocMapper,
                               ReqAcceptanceCriteriaMapper reqAcceptanceCriteriaMapper,
                               ReqSlaConfigMapper reqSlaConfigMapper,
                               SysUserMapper sysUserMapper,
                               SysNoSequenceMapper sysNoSequenceMapper,
                               TicketNoGenerator ticketNoGenerator,
                               TicketDupChecker ticketDupChecker,
                               SlaCalculator slaCalculator) {
        this.reqTicketMapper = reqTicketMapper;
        this.reqFlowLogMapper = reqFlowLogMapper;
        this.reqProjectMapper = reqProjectMapper;
        this.reqSystemMapper = reqSystemMapper;
        this.reqDocMapper = reqDocMapper;
        this.reqAcceptanceCriteriaMapper = reqAcceptanceCriteriaMapper;
        this.reqSlaConfigMapper = reqSlaConfigMapper;
        this.sysUserMapper = sysUserMapper;
        this.sysNoSequenceMapper = sysNoSequenceMapper;
        this.ticketNoGenerator = ticketNoGenerator;
        this.ticketDupChecker = ticketDupChecker;
        this.slaCalculator = slaCalculator;
    }

    /**
     * 建单。查重拒绝时抛 {@code DUP_FOUND}，details 带相似列表。
     *
     * @param dto    表单
     * @param userId 提出人（登录用户）
     * @return 建单结果
     */
    @Transactional(rollbackFor = Exception.class)
    public TicketCreatedVO create(TicketCreateDTO dto, String userId) {
        校验必填(dto);

        ReqProjectDO project = reqProjectMapper.selectById(dto.getProjectId());
        if (project == null) {
            throw new BizException("所属项目不存在");
        }
        ReqSystemDO system = reqSystemMapper.selectById(dto.getSystemId());
        if (system == null) {
            throw new BizException("所属系统不存在");
        }
        if (!dto.getProjectId().equals(system.getProjectId())) {
            // 项目 → 系统是层级归属：系统挂错项目说明前端联动失效或有人绕过界面
            throw new BizException("所选系统不属于所选项目，请重新选择");
        }

        // 查重：命中且提出人没有确认「仍要新建」时，把相似列表带回
        String 查重文本 = dto.getTitle() + "\n" + dto.getDescription();
        List<TicketDupChecker.相似项> 相似列表 = ticketDupChecker.查重(
                dto.getTicketType(), dto.getSystemId(), dto.getProjectId(), 查重文本);
        double 最高相似度 = 相似列表.isEmpty() ? 0 : 相似列表.get(0).score();
        boolean 坚持新建 = dto.getDupOverride() != null && dto.getDupOverride() == 1;
        if (ticketDupChecker.命中(相似列表)) {
            if (!坚持新建) {
                throw new BizCodeException(BIZ_DUP_FOUND,
                        "检测到相似需求（最高相似度 " + 最高相似度 + "），请先查看列表再决定",
                        相似列表);
            }
            if (dto.getOverrideReason() == null || dto.getOverrideReason().trim().isEmpty()) {
                throw new BizException("坚持新建必须填写原因，供后续复盘追溯");
            }
        }

        String now = LocalDateTime.now().format(时间格式);
        String 单号 = ticketNoGenerator.next(dto.getTicketType());

        ReqTicketDO ticket = new ReqTicketDO();
        ticket.setId(IdUtils.nextId());
        ticket.setTicketNo(单号);
        ticket.setTicketType(dto.getTicketType());
        ticket.setTitle(dto.getTitle().trim());
        ticket.setDescription(dto.getDescription());
        ticket.setContentAsis(dto.getContentAsis());
        ticket.setContentTobe(dto.getContentTobe());
        // 提交即进入 AI 分析完成后的文档待确认态，草稿不落库（一期没有草稿箱）
        ticket.setStatus(TicketStatus.REQ_DOC_DRAFTED);
        ticket.setPriority(dto.getPriority());
        ticket.setReporterId(userId);
        ticket.setCreatorId(userId);
        ticket.setOwnerId(userId);
        ticket.setDeptId(主部门(userId));
        ticket.setProjectId(dto.getProjectId());
        ticket.setSystemId(dto.getSystemId());
        // system_code 只是快照（报表/导出用），业务判断一律走 system_id
        ticket.setSystemCode(system.getSystemCode());
        ticket.setModuleCode(dto.getModuleCode());
        ticket.setAcceptanceCriteria(dto.getAcceptanceCriteria());
        ticket.setRoiDesc(dto.getRoiDesc());
        if (dto.getExpectDueDate() != null && !dto.getExpectDueDate().isEmpty()) {
            ticket.setExpectDueDate(dto.getExpectDueDate());
            ticket.setProposeDate(dto.getExpectDueDate().length() >= 10
                    ? dto.getExpectDueDate().substring(0, 10) : dto.getExpectDueDate());
        } else {
            ticket.setProposeDate(LocalDate.now().toString());
        }
        ticket.setTicketSource("WEB");
        ticket.setDupOverride(坚持新建 ? 1 : 0);
        ticket.setOverrideReason(坚持新建 ? dto.getOverrideReason() : null);
        ticket.setReturnCount(0);
        ticket.setSubmittedAt(now);
        ticket.setSlaOverdueHours(0.0);
        if (坚持新建) {
            ticket.setSuperiorId(userId);
        }
        ticket.setCreatedAt(now);
        ticket.setUpdatedAt(now);
        ticket.setUpdatedBy(userId);
        reqTicketMapper.insert(ticket);

        // 时间线：提交 + AI 分析（规则版），与演示数据的形状一致
        写流转(ticket.getId(), "DRAFT", TicketStatus.SUBMITTED, "SUBMIT", userId, now,
                "提交需求", null, null);
        String 分析结论 = 相似列表.isEmpty()
                ? "规则版查重：无相似需求，判定 NEW"
                : "规则版查重：最高相似度 " + 最高相似度 + "，"
                        + (坚持新建 ? "提出人确认仍新建" : "判定 NEW");
        写流转(ticket.getId(), TicketStatus.SUBMITTED, TicketStatus.REQ_DOC_DRAFTED,
                "AI_ANALYZE", null, now, 分析结论, null, null);

        // SLA：按「单据类型 + 目标状态」取配置算应完成时间
        ticket.setSlaDueAt(算时限(dto.getTicketType(), ticket.getStatus(), now));
        reqTicketMapper.updateById(ticket);

        // 需求文档：规则模板生成 12 节骨架
        生成需求文档(ticket, dto, 最高相似度, now);

        // 验收标准拆条：有内容才落子表，UAT 覆盖率的分母靠它
        拆验收标准(ticket.getId(), dto.getAcceptanceCriteria(), now);

        log.info("单据创建成功：{}，提出人={}，最高相似度={}", 单号, userId, 最高相似度);

        TicketCreatedVO result = new TicketCreatedVO();
        result.setId(ticket.getId());
        result.setTicketNo(单号);
        result.setStatus(ticket.getStatus());
        result.setStatusLabel("需求文档待确认");
        result.setMaxSimilarity(最高相似度);
        result.setSimilarList(null);
        return result;
    }

    /**
     * 必填校验。一次说清全部缺项，别让用户填一项提交一次。
     *
     * @param dto 表单
     */
    private void 校验必填(TicketCreateDTO dto) {
        List<String> 缺失 = new ArrayList<>();
        if (isBlank(dto.getTicketType())) {
            缺失.add("单据类型");
        }
        if (isBlank(dto.getProjectId())) {
            缺失.add("所属项目");
        }
        if (isBlank(dto.getSystemId())) {
            缺失.add("所属系统");
        }
        if (isBlank(dto.getTitle())) {
            缺失.add("标题");
        }
        if (isBlank(dto.getPriority())) {
            缺失.add("优先级");
        }
        if (isBlank(dto.getDescription())) {
            缺失.add("需求简述");
        }
        if (isBlank(dto.getContentAsis())) {
            缺失.add("现状 As Is");
        }
        if (isBlank(dto.getContentTobe())) {
            缺失.add("期望 To Be");
        }
        if (!缺失.isEmpty()) {
            throw new BizException("请填写：" + String.join("、", 缺失));
        }
    }

    /**
     * 提出人主部门，作为单据上的提出部门快照。
     *
     * @param userId 用户
     * @return 部门 ID，可空
     */
    private String 主部门(String userId) {
        SysUserDO user = sysUserMapper.selectById(userId);
        return user == null ? null : user.getPrimaryDeptId();
    }

    /**
     * 从 SLA 配置取目标状态的时限并算应完成时间。
     *
     * @param ticketType 单据类型
     * @param status     目标状态
     * @param now        当前时间串
     * @return 应完成时间；未配置返回 null（不计时）
     */
    private String 算时限(String ticketType, String status, String now) {
        ReqSlaConfigDO config = reqSlaConfigMapper.selectOne(
                Wrappers.<ReqSlaConfigDO>lambdaQuery()
                        .eq(ReqSlaConfigDO::getTicketType, ticketType)
                        .eq(ReqSlaConfigDO::getStatus, status)
                        .eq(ReqSlaConfigDO::getEnabled, 1)
                        .last("limit 1"));
        double 时限 = config == null || config.getLimitHours() == null
                ? 无时限 : config.getLimitHours();
        if (时限 <= 0) {
            return null;
        }
        return slaCalculator.calculateDueAt(now, 时限);
    }

    /**
     * 规则模板生成需求文档：12 节骨架，提单页已经有的字段直接填，其余留空待提出人补。
     * <p>
     * 键名严格照契约 `11` §8.1.1 的定稿，不自己另起名字 —— 前端按 key 渲染卡片、
     * 点检项从第 5 节取功能清单、覆盖率从第 10 节取分母，改一个 key 就断一处，
     * 而且断得没有报错，只是那一节显示空白。
     * <p>
     * Markdown 正文由 {@code DocMarkdown} 从同一份 JSON 渲染，不在这里另写一套，
     * 否则表单改了正文不变。
     *
     * @param ticket   单据
     * @param dto      提单表单
     * @param 最高相似度 查重最高加权相似度，写进第 12 节
     * @param now      当前时间
     */
    private void 生成需求文档(ReqTicketDO ticket, TicketCreateDTO dto,
                              double 最高相似度, String now) {
        ObjectNode sections = MAPPER.createObjectNode();

        // 第 1~4 节：提单页面的四个输入框直接映射
        sections.putObject(DocSections.NAME).put("content", ticket.getTitle());
        sections.putObject(DocSections.ASIS).put("content", nullToEmpty(dto.getContentAsis()));
        // 痛点单独成节：提单只有一个「现状」输入框，影响与量化留给提出人确认前补
        sections.putObject(DocSections.PAIN).put("content", "").putArray("metrics");
        sections.putObject(DocSections.TOBE).put("content", nullToEmpty(dto.getContentTobe()));

        // 第 5 节：功能清单由提出人逐条补，系统不猜。编号规则见契约 §8.1.3
        sections.putObject(DocSections.FUNCTIONS).putArray("items");
        // 第 6 节：非功能要求，同样不猜
        sections.putObject(DocSections.NFR).putArray("items");

        // 第 7 节：联动范围能自动填系统，省一次人工输入；部门与影响说明留空
        ObjectNode scope = sections.putObject(DocSections.SCOPE);
        scope.putArray("systems").addObject()
                .put("systemCode", nullToEmpty(ticket.getSystemCode()))
                .put("impact", "");
        scope.putArray("depts");

        // 第 8 节：优先级取提单选的等级，三项打分留 0，理由待填
        ObjectNode priority = sections.putObject(DocSections.PRIORITY);
        priority.put("level", nullToEmpty(ticket.getPriority()));
        priority.putObject("scores").put("impact", 0).put("urgency", 0).put("compliance", 0);
        priority.put("reason", "");

        // 第 9 节：工作量不估，标记为 AI 估算待 MIT 确认（契约 §8.1.4 的水印规则）
        ObjectNode effort = sections.putObject(DocSections.EFFORT);
        effort.putNull("minDays").putNull("maxDays").putNull("suggestReleaseDate");
        effort.put("aiEstimated", 1);
        effort.putNull("confirmedBy");

        // 第 10 节：提单时填的验收标准按行拆条，seq 从 1 起
        ArrayNode 验收条目 = sections.putObject(DocSections.ACCEPTANCE).putArray("items");
        int 序号 = 0;
        if (dto.getAcceptanceCriteria() != null) {
            for (String 行 : dto.getAcceptanceCriteria().split("\\r?\\n")) {
                String 条 = 行.trim().replaceFirst("^\\d+[.、．]\\s*", "");
                if (条.isEmpty()) {
                    continue;
                }
                序号++;
                验收条目.addObject().put("seq", 序号).put("text", 条);
            }
        }

        // 第 11 节：附件清单由人维护，系统不清楚该交什么材料
        sections.putObject(DocSections.ATTACHMENTS).putArray("items");

        // 第 12 节：规则版预审结论。分数按「必填节填了几项」算，可解释、不瞎编
        ObjectNode review = sections.putObject(DocSections.AI_REVIEW);
        review.put("dupScore", 最高相似度);
        review.put("verdict", 最高相似度 > 0.5 ? "SIMILAR" : "NEW");
        review.put("completeness", DocValidator.completeness(sections));
        ArrayNode 风险 = review.putArray("risks");
        if (是空的(sections.path(DocSections.FUNCTIONS).path("items"))) {
            风险.add("功能清单待补充：点检项草稿与用例覆盖率都依赖它");
        }
        if (是空的(验收条目)) {
            风险.add("验收标准为空：UAT 覆盖率无法计算，确认时会被拦下");
        }
        风险.add("工作量区间待评估：派单的默认工时取自该区间中值");
        review.putArray("unclarified");

        ObjectNode root = MAPPER.createObjectNode();
        root.put("schemaVersion", 1);
        root.set("sections", sections);
        String docJson = root.toString();

        ReqDocDO doc = new ReqDocDO();
        doc.setId(IdUtils.nextId());
        doc.setTicketId(ticket.getId());
        doc.setVersion(1);
        doc.setDocJson(docJson);
        doc.setContentMd(DocMarkdown.render(docJson));
        doc.setAiModel(规则模板模型);
        doc.setAiDegraded(0);
        doc.setCreatedAt(now);
        doc.setUpdatedAt(now);
        reqDocMapper.insert(doc);
    }

    /**
     * 数组节点是否为空。
     *
     * @param node 节点
     * @return 空或非数组返回 true
     */
    private boolean 是空的(com.fasterxml.jackson.databind.JsonNode node) {
        return node == null || !node.isArray() || node.isEmpty();
    }

    /**
     * 验收标准按行拆条落子表。行首的「1.」「2.」等序号剥掉，seq 由系统生成。
     *
     * @param ticketId 单据 ID
     * @param 标准     多行文本
     * @param now      当前时间
     */
    private void 拆验收标准(String ticketId, String 标准, String now) {
        if (标准 == null || 标准.trim().isEmpty()) {
            return;
        }
        String[] 行列表 = 标准.split("\\r?\\n");
        int 序号 = 0;
        for (String 行 : 行列表) {
            String 条 = 行.trim().replaceFirst("^\\d+[.、．]\\s*", "");
            if (条.isEmpty()) {
                continue;
            }
            序号++;
            ReqAcceptanceCriteriaDO criteria = new ReqAcceptanceCriteriaDO();
            criteria.setId(IdUtils.nextId());
            criteria.setTicketId(ticketId);
            criteria.setSeq(序号);
            criteria.setContent(条);
            criteria.setLocked(0);
            criteria.setCreatedAt(now);
            criteria.setUpdatedAt(now);
            reqAcceptanceCriteriaMapper.insert(criteria);
        }
    }

    /**
     * 写一条流转时间线。
     *
     * @param ticketId    单据
     * @param fromStatus  前状态，建单首条为 DRAFT
     * @param toStatus    后状态
     * @param action      动作码
     * @param operatorId  操作人，系统动作为 null
     * @param now         时间
     * @param remark      备注
     * @param reasonCode  原因码，建单无
     * @param returnPoint 退回点，建单无
     */
    private void 写流转(String ticketId, String fromStatus, String toStatus, String action,
                        String operatorId, String now, String remark,
                        String reasonCode, String returnPoint) {
        ReqFlowLogDO logDO = new ReqFlowLogDO();
        logDO.setId(IdUtils.nextId());
        logDO.setTicketId(ticketId);
        logDO.setFromStatus(fromStatus);
        logDO.setToStatus(toStatus);
        logDO.setAction(action);
        logDO.setOperatorId(operatorId);
        logDO.setReasonCode(reasonCode);
        logDO.setRemark(remark);
        logDO.setReturnPoint(returnPoint);
        logDO.setCreatedAt(now);
        reqFlowLogMapper.insert(logDO);
    }

    /**
     * 空值兜底。
     *
     * @param 文本 原文
     * @return 空串兜底后的文本
     */
    private String nullToEmpty(String 文本) {
        return 文本 == null ? "" : 文本;
    }

    /**
     * 字符串非空判断。
     *
     * @param value 值
     * @return 非空返回 true
     */
    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
