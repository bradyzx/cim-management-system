package com.cim.reqflow.ticket.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.cim.reqflow.common.BizException;
import com.cim.reqflow.common.util.IdUtils;
import com.cim.reqflow.system.entity.SysUserDO;
import com.cim.reqflow.system.mapper.SysUserMapper;
import com.cim.reqflow.ticket.doc.DocMarkdown;
import com.cim.reqflow.ticket.doc.DocSections;
import com.cim.reqflow.ticket.doc.DocValidator;
import com.cim.reqflow.ticket.dto.TicketDocSaveDTO;
import com.cim.reqflow.ticket.entity.ReqAcceptanceCriteriaDO;
import com.cim.reqflow.ticket.entity.ReqDocDO;
import com.cim.reqflow.ticket.entity.ReqTicketDO;
import com.cim.reqflow.ticket.flow.TicketStatus;
import com.cim.reqflow.ticket.mapper.ReqAcceptanceCriteriaMapper;
import com.cim.reqflow.ticket.mapper.ReqDocMapper;
import com.cim.reqflow.ticket.mapper.ReqTicketMapper;
import com.cim.reqflow.ticket.vo.TicketDocVO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 需求文档的读、写与确认回填。
 * <p>
 * 三条边界：
 * <ul>
 *   <li>只有提出人能改（与 {@code DOC_CONFIRM} 的归属规则一致），否则任何登录用户
 *       都能改别人的需求文档；</li>
 *   <li>只有「需求文档待确认」且尚未确认时可改，确认后版本锁定；</li>
 *   <li>第 10 节验收标准是唯一真相，改完要同步投影到 {@code req_acceptance_criteria}
 *       —— 覆盖率的分母在那里。</li>
 * </ul>
 *
 * @author Haijie Xiao
 * @date 2026-11-05
 */
@Service
public class TicketDocService {

    /** 解析 docJson 用，线程安全 */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 库里统一存字符串时间，与流转日志同格式 */
    private static final DateTimeFormatter 时间格式 = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 标记位：是 */
    private static final int FLAG_YES = 1;

    private final ReqDocMapper reqDocMapper;

    private final ReqTicketMapper reqTicketMapper;

    private final ReqAcceptanceCriteriaMapper reqAcceptanceCriteriaMapper;

    private final SysUserMapper sysUserMapper;

    public TicketDocService(ReqDocMapper reqDocMapper,
                            ReqTicketMapper reqTicketMapper,
                            ReqAcceptanceCriteriaMapper reqAcceptanceCriteriaMapper,
                            SysUserMapper sysUserMapper) {
        this.reqDocMapper = reqDocMapper;
        this.reqTicketMapper = reqTicketMapper;
        this.reqAcceptanceCriteriaMapper = reqAcceptanceCriteriaMapper;
        this.sysUserMapper = sysUserMapper;
    }

    /**
     * 读当前版本文档。同时算出「现在点确认会挡住哪些节」，供前端直接标红。
     *
     * @param ticketId 单据 ID
     * @return 文档视图
     */
    public TicketDocVO get(String ticketId) {
        ReqTicketDO ticket = 取单据(ticketId);
        ReqDocDO doc = 取当前版本(ticketId);
        if (doc == null) {
            throw new BizException("该单据还没有需求文档，请先在提单页提交需求");
        }
        return 组装(doc, ticket);
    }

    /**
     * 保存人工编辑后的文档。保存不校验完整性 —— 写到一半要能存，确认才是门槛。
     *
     * @param ticketId 单据 ID
     * @param dto      文档内容
     * @param userId   操作人，必须是提出人
     * @return 保存后的文档视图
     */
    @Transactional(rollbackFor = Exception.class)
    public TicketDocVO save(String ticketId, TicketDocSaveDTO dto, String userId) {
        ReqTicketDO ticket = 取单据(ticketId);
        校验可编辑(ticket, userId);

        if (dto.getDocJson() == null || !dto.getDocJson().path("sections").isObject()) {
            throw new BizException("需求文档结构不合法：缺少 sections");
        }
        ReqDocDO doc = 取当前版本(ticketId);
        if (doc == null) {
            throw new BizException("该单据还没有需求文档，无法保存");
        }

        String now = LocalDateTime.now().format(时间格式);
        // 完整度是「必填节填了几项」的派生值，必须按最新内容重算：
        // 只保留生成时的分数，用户补齐功能清单后页面还显示 60 分，看着像没改成功
        JsonNode 节 = dto.getDocJson().path("sections");
        if (dto.getDocJson() instanceof ObjectNode 根 && 节.path(DocSections.AI_REVIEW).isObject()) {
            ((ObjectNode) 节.path(DocSections.AI_REVIEW))
                    .put("completeness", DocValidator.completeness(节));
        }
        String 新docJson = dto.getDocJson().toString();
        doc.setDocJson(新docJson);
        // 正文一律现算，不接受前端传的 Markdown，否则两份内容会各改各的
        doc.setContentMd(DocMarkdown.render(新docJson));
        // 人补过之后就不再是「降级空壳」，确认时的校验才能按正常规则走
        doc.setAiDegraded(0);
        doc.setUpdatedAt(now);
        reqDocMapper.updateById(doc);

        同步验收标准(ticketId, dto.getDocJson(), now);
        return 组装(doc, ticket);
    }

    /**
     * 确认时回填 {@code req_doc} 的确认时间与确认人。
     * <p>
     * 由 {@code TicketFlowService} 在 {@code DOC_CONFIRM} 落库后调用：
     * 单据上的 {@code doc_confirmed_at} 与文档上的 {@code confirmed_at} 是两处记录，
     * 前者用于列表与统计，后者用于「这份文档是哪个版本被谁认下的」。
     *
     * @param ticketId 单据 ID
     * @param userId   确认人
     * @param now      确认时间
     */
    public void markConfirmed(String ticketId, String userId, String now) {
        ReqDocDO doc = 取当前版本(ticketId);
        if (doc == null) {
            return;
        }
        doc.setConfirmedAt(now);
        doc.setConfirmedBy(userId);
        doc.setUpdatedAt(now);
        reqDocMapper.updateById(doc);
    }

    /**
     * 取一张单的最新版本文档。
     *
     * @param ticketId 单据 ID
     * @return 文档，无则 null
     */
    private ReqDocDO 取当前版本(String ticketId) {
        return reqDocMapper.selectOne(Wrappers.<ReqDocDO>lambdaQuery()
                .eq(ReqDocDO::getTicketId, ticketId)
                .orderByDesc(ReqDocDO::getVersion)
                .last("limit 1"));
    }

    /**
     * 取单据，不存在直接报错。
     *
     * @param ticketId 单据 ID
     * @return 单据
     */
    private ReqTicketDO 取单据(String ticketId) {
        ReqTicketDO ticket = reqTicketMapper.selectById(ticketId);
        if (ticket == null) {
            throw new BizException("单据不存在");
        }
        return ticket;
    }

    /**
     * 校验当前状态与身份是否允许编辑。
     *
     * @param ticket 单据
     * @param userId 操作人
     */
    private void 校验可编辑(ReqTicketDO ticket, String userId) {
        if (!userId.equals(ticket.getReporterId())) {
            throw new BizException("只有需求提出人才能编辑需求文档");
        }
        if (!TicketStatus.REQ_DOC_DRAFTED.equals(ticket.getStatus())) {
            throw new BizException("当前环节不允许编辑需求文档，只有「需求文档待确认」时可以改");
        }
        if (ticket.getDocConfirmedAt() != null && !ticket.getDocConfirmedAt().trim().isEmpty()) {
            throw new BizException("需求文档已确认锁定，如需变更请退回后重新生成");
        }
        if (ticket.getAcceptanceLocked() != null && ticket.getAcceptanceLocked() == FLAG_YES) {
            // 验收标准锁定后不允许再动：用例覆盖率的对应键已经发布出去了
            throw new BizException("验收标准已锁定，不允许再修改需求文档");
        }
    }

    /**
     * 把第 10 节验收标准同步到子表：按 seq 做 upsert，多余的物理删除。
     * <p>
     * 不整表删了重插：`req_acceptance_criteria` 的行 ID 会被用例的
     * `criteria_index` 之外的其他地方引用，重插会让历史指向漂移。
     *
     * @param ticketId 单据 ID
     * @param docJson  12 节数据
     * @param now      时间
     */
    private void 同步验收标准(String ticketId, JsonNode docJson, String now) {
        JsonNode items = docJson.path("sections").path(DocSections.ACCEPTANCE).path("items");

        List<ReqAcceptanceCriteriaDO> 现有 = reqAcceptanceCriteriaMapper.selectList(
                Wrappers.<ReqAcceptanceCriteriaDO>lambdaQuery()
                        .eq(ReqAcceptanceCriteriaDO::getTicketId, ticketId));
        Map<Integer, ReqAcceptanceCriteriaDO> 按序号 = new HashMap<>();
        for (ReqAcceptanceCriteriaDO 一条 : 现有) {
            按序号.put(一条.getSeq(), 一条);
        }

        Set<Integer> 新序号 = new HashSet<>();
        for (JsonNode 项 : items) {
            int seq = 项.path("seq").asInt(0);
            if (seq <= 0) {
                continue;
            }
            String 内容 = 项.path("text").asText("").trim();
            新序号.add(seq);

            ReqAcceptanceCriteriaDO 已有 = 按序号.get(seq);
            if (已有 == null) {
                ReqAcceptanceCriteriaDO 新增 = new ReqAcceptanceCriteriaDO();
                新增.setId(IdUtils.nextId());
                新增.setTicketId(ticketId);
                新增.setSeq(seq);
                新增.setContent(内容);
                新增.setLocked(0);
                新增.setCreatedAt(now);
                新增.setUpdatedAt(now);
                reqAcceptanceCriteriaMapper.insert(新增);
                continue;
            }
            if (!内容.equals(已有.getContent())) {
                已有.setContent(内容);
                已有.setUpdatedAt(now);
                reqAcceptanceCriteriaMapper.updateById(已有);
            }
        }

        for (ReqAcceptanceCriteriaDO 旧 : 现有) {
            if (!新序号.contains(旧.getSeq())) {
                reqAcceptanceCriteriaMapper.deleteById(旧.getId());
            }
        }
    }

    /**
     * 组装视图，同时把「能不能改」与「确认还差什么」算好。
     *
     * @param doc    文档
     * @param ticket 单据
     * @return 视图
     */
    private TicketDocVO 组装(ReqDocDO doc, ReqTicketDO ticket) {
        TicketDocVO vo = new TicketDocVO();
        vo.setId(doc.getId());
        vo.setTicketId(doc.getTicketId());
        vo.setVersion(doc.getVersion());
        // 正文读时现算，不返回库里那一列：那一列是保存时的快照，历史数据可能没同步。
        // 现算能保证「看到的 Markdown」和「看到的 12 节」永远是同一份内容。
        vo.setContentMd(DocMarkdown.render(doc.getDocJson()));
        vo.setAiModel(doc.getAiModel());
        vo.setAiDegraded(doc.getAiDegraded());
        vo.setConfirmedAt(doc.getConfirmedAt());
        vo.setConfirmedBy(doc.getConfirmedBy());
        vo.setConfirmedByName(取姓名(doc.getConfirmedBy()));
        vo.setTicketStatus(ticket.getStatus());

        // docJson 解析成对象给前端：前端按 12 个 key 直接取，不用二次 JSON.parse
        JsonNode 节点 = 解析(doc.getDocJson());
        vo.setDocJson(节点);

        boolean 已确认 = doc.getConfirmedAt() != null && !doc.getConfirmedAt().trim().isEmpty();
        boolean 可编辑 = TicketStatus.REQ_DOC_DRAFTED.equals(ticket.getStatus()) && !已确认;
        vo.setEditable(可编辑);

        DocValidator.Result 校验 = DocValidator.check(doc.getDocJson());
        vo.setMissingSections(校验.missingSections());
        vo.setMissingMessages(校验.messages());
        return vo;
    }

    /**
     * 解析 docJson。库里存的是字符串，损坏时给空对象而不是抛异常 ——
     * 打不开页面比看不到某一节更糟。
     *
     * @param docJson 字符串
     * @return 节点，损坏时为空对象
     */
    private JsonNode 解析(String docJson) {
        if (docJson == null || docJson.trim().isEmpty()) {
            return MAPPER.createObjectNode();
        }
        try {
            return MAPPER.readTree(docJson);
        } catch (Exception 异常) {
            return MAPPER.createObjectNode();
        }
    }

    /**
     * 用户 ID 转姓名，空 ID 返回 null。
     *
     * @param userId 用户 ID
     * @return 姓名
     */
    private String 取姓名(String userId) {
        if (userId == null || userId.trim().isEmpty()) {
            return null;
        }
        SysUserDO user = sysUserMapper.selectById(userId);
        return user == null ? null : user.getRealName();
    }
}
