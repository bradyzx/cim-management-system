package com.cim.reqflow.ticket.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.cim.reqflow.common.BizException;
import com.cim.reqflow.common.constant.CommonConstants;
import com.cim.reqflow.common.util.IdUtils;
import com.cim.reqflow.ticket.dto.CaseExecDTO;
import com.cim.reqflow.ticket.dto.CaseGenerateDTO;
import com.cim.reqflow.ticket.dto.CaseSaveDTO;
import com.cim.reqflow.ticket.entity.ReqAcceptanceCriteriaDO;
import com.cim.reqflow.ticket.entity.ReqCaseResultDO;
import com.cim.reqflow.ticket.entity.ReqTestCaseDO;
import com.cim.reqflow.ticket.entity.ReqTicketDO;
import com.cim.reqflow.ticket.mapper.ReqAcceptanceCriteriaMapper;
import com.cim.reqflow.ticket.mapper.ReqCaseResultMapper;
import com.cim.reqflow.ticket.mapper.ReqTestCaseMapper;
import com.cim.reqflow.ticket.mapper.ReqTicketMapper;
import com.cim.reqflow.ticket.testcase.CaseTemplate;
import com.cim.reqflow.ticket.testcase.CoverageCalculator;
import com.cim.reqflow.ticket.vo.CaseGenerateVO;
import com.cim.reqflow.ticket.vo.CaseListVO;
import com.cim.reqflow.ticket.vo.CoverageVO;
import com.cim.reqflow.ticket.vo.TestCaseVO;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 测试用例：生成、查询、编辑、执行登记、覆盖率。
 * <p>
 * 一期用规则模板生成（`06` 第九节），不接模型。三条边界：
 * <ul>
 *   <li>只有验收标准锁定后（设计评审全过）才能生成 —— 标准会变的时候生成等于白做；</li>
 *   <li>编号、来源标准、批次生成后不可改：编号是 BUG 单的引用键，来源标准是覆盖率的连接键，
 *       放开这两项等于让覆盖率可以随手造假；</li>
 *   <li>每次执行写一条 {@code req_case_result}，不覆盖历史 —— 多轮回归要能看出
 *       「这条上次过了这次挂了」。</li>
 * </ul>
 *
 * @author Haijie Xiao
 * @date 2026-11-05
 */
@Service
public class TicketCaseService {

    /** 解析与序列化步骤 JSON */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 库里统一存字符串时间 */
    private static final DateTimeFormatter 时间格式 = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 未执行 */
    private static final String STATUS_NOT_RUN = "NOT_RUN";
    /** 通过 */
    private static final String STATUS_PASS = "PASS";
    /** 不通过 */
    private static final String STATUS_FAIL = "FAIL";
    /** 跳过 */
    private static final String STATUS_SKIP = "SKIP";

    /** 允许登记的三种执行结果 */
    private static final Set<String> 允许的执行状态 = Set.of(STATUS_PASS, STATUS_FAIL, STATUS_SKIP);

    /** 用例来源：规则模板生成 */
    private static final String ORIGIN_AI = "AI";
    /** 用例来源：人工编写或修改 */
    private static final String ORIGIN_HUMAN = "HUMAN";

    /** 标记位：是 */
    private static final int FLAG_YES = 1;

    /** 单需求用例数上限，超了给出提示（不会截断，截断会让覆盖率永远到不了 100%） */
    private static final int CASE_WARN_LIMIT = 15;

    private final ReqTestCaseMapper reqTestCaseMapper;

    private final ReqCaseResultMapper reqCaseResultMapper;

    private final ReqAcceptanceCriteriaMapper reqAcceptanceCriteriaMapper;

    private final ReqTicketMapper reqTicketMapper;

    public TicketCaseService(ReqTestCaseMapper reqTestCaseMapper,
                             ReqCaseResultMapper reqCaseResultMapper,
                             ReqAcceptanceCriteriaMapper reqAcceptanceCriteriaMapper,
                             ReqTicketMapper reqTicketMapper) {
        this.reqTestCaseMapper = reqTestCaseMapper;
        this.reqCaseResultMapper = reqCaseResultMapper;
        this.reqAcceptanceCriteriaMapper = reqAcceptanceCriteriaMapper;
        this.reqTicketMapper = reqTicketMapper;
    }

    /**
     * 某一轮的用例 + 覆盖率。不传轮次时取最新一轮。
     *
     * @param ticketId 单据 ID
     * @param roundNo  轮次，可空
     * @return 用例列表
     */
    public CaseListVO list(String ticketId, Integer roundNo) {
        ReqTicketDO ticket = 取单据(ticketId);
        List<Integer> 轮次表 = 所有轮次(ticketId);

        Integer 目标轮 = roundNo;
        if (目标轮 == null && !轮次表.isEmpty()) {
            目标轮 = 轮次表.get(0);
        }

        List<ReqTestCaseDO> cases = 目标轮 == null
                ? List.of() : 按轮次查(ticketId, 目标轮);

        CaseListVO vo = new CaseListVO();
        vo.setRoundNo(目标轮);
        vo.setRounds(轮次表);
        vo.setItems(toVOList(cases));
        vo.setCoverage(组装覆盖率(ticket));
        vo.setExecutedCount(计数已执行(cases));
        vo.setPassedCount(计数(cases, STATUS_PASS));
        vo.setFailedCount(计数(cases, STATUS_FAIL));
        return vo;
    }

    /**
     * 按验收标准生成用例。每条标准出一条，保证覆盖率能到 100%。
     *
     * @param ticketId 单据 ID
     * @param dto      轮次，可空
     * @param userId   操作人
     * @return 生成结果（带覆盖率）
     */
    @Transactional(rollbackFor = Exception.class)
    public CaseGenerateVO generate(String ticketId, CaseGenerateDTO dto, String userId) {
        ReqTicketDO ticket = 取单据(ticketId);
        if (ticket.getAcceptanceLocked() == null || ticket.getAcceptanceLocked() != FLAG_YES) {
            throw new BizException("验收标准尚未锁定：设计评审全部通过后才能生成用例");
        }

        List<ReqAcceptanceCriteriaDO> criteria = 标准列表(ticketId);
        if (criteria.isEmpty()) {
            throw new BizException("本单没有验收标准，无法生成用例。请先补全需求文档第 10 节");
        }

        int 轮次 = dto == null || dto.getRoundNo() == null
                ? 下一轮次(ticketId) : dto.getRoundNo();
        List<ReqTestCaseDO> 已存在 = 按轮次查(ticketId, 轮次);
        if (!已存在.isEmpty()) {
            throw new BizException("第 " + 轮次 + " 轮用例已存在（" + 已存在.size()
                    + " 条）。如需按最新标准重来，请用新一轮次");
        }

        String now = LocalDateTime.now().format(时间格式);
        int 序号 = 0;
        for (ReqAcceptanceCriteriaDO 标准 : criteria) {
            if (标准.getSeq() == null || 标准.getContent() == null
                    || 标准.getContent().trim().isEmpty()) {
                continue;
            }
            序号++;
            CaseTemplate.Draft 草稿 = CaseTemplate.build(标准.getSeq(), 标准.getContent());

            ReqTestCaseDO 用例 = new ReqTestCaseDO();
            用例.setId(IdUtils.nextId());
            用例.setTicketId(ticketId);
            用例.setCaseNo(用例编号(ticket, 轮次, 序号));
            用例.setTitle(草稿.title());
            用例.setPrecond(草稿.precond());
            用例.setSteps(序列化步骤(草稿.steps()));
            用例.setExpected(草稿.expected());
            用例.setPriority(草稿.priority());
            用例.setSourceCriteria(草稿.sourceCriteria());
            用例.setCriteriaIndex(草稿.criteriaIndex());
            用例.setFocusFlag(草稿.focusFlag());
            用例.setFocusReason(草稿.focusReason());
            用例.setOrigin(ORIGIN_AI);
            用例.setExecStatus(STATUS_NOT_RUN);
            用例.setRoundNo(轮次);
            用例.setCreatedBy(userId);
            用例.setCreatedAt(now);
            用例.setUpdatedAt(now);
            reqTestCaseMapper.insert(用例);
        }

        CaseGenerateVO vo = new CaseGenerateVO();
        vo.setRoundNo(轮次);
        vo.setGenerated(序号);
        vo.setSkipped(criteria.size() - 序号);
        vo.setTotal(序号);
        vo.setCoverage(组装覆盖率(ticket));
        if (序号 > CASE_WARN_LIMIT) {
            // 不截断：截断会让「每条标准至少一条用例」不成立，覆盖率永远到不了 100%
            vo.setWarning("本轮生成 " + 序号 + " 条，超过建议上限 " + CASE_WARN_LIMIT
                    + " 条。用例太多没人会执行，建议人工合并同标准下的重复项");
        }
        return vo;
    }

    /**
     * 编辑用例内容。改过就标 HUMAN —— AI 生成、人工修改的比例是回看生成质量的关键指标。
     *
     * @param caseId 用例 ID
     * @param dto    可编辑字段
     * @return 更新后的用例
     */
    @Transactional(rollbackFor = Exception.class)
    public TestCaseVO update(String caseId, CaseSaveDTO dto) {
        ReqTestCaseDO 用例 = 取用例(caseId);
        if (dto.getTitle() != null) {
            用例.setTitle(dto.getTitle().trim());
        }
        if (dto.getPrecond() != null) {
            用例.setPrecond(dto.getPrecond());
        }
        if (dto.getSteps() != null) {
            用例.setSteps(序列化步骤(dto.getSteps()));
        }
        if (dto.getExpected() != null) {
            用例.setExpected(dto.getExpected());
        }
        if (dto.getPriority() != null) {
            用例.setPriority(dto.getPriority());
        }
        if (dto.getFocusFlag() != null) {
            用例.setFocusFlag(dto.getFocusFlag());
        }
        if (dto.getFocusReason() != null) {
            用例.setFocusReason(dto.getFocusReason());
        }
        用例.setOrigin(ORIGIN_HUMAN);
        用例.setUpdatedAt(LocalDateTime.now().format(时间格式));
        reqTestCaseMapper.updateById(用例);
        return toVO(用例);
    }

    /**
     * 执行登记：更新用例当前状态，同时追加一条执行记录。
     *
     * @param caseId 用例 ID
     * @param dto    执行结果
     * @param userId 执行人
     * @return 更新后的用例
     */
    @Transactional(rollbackFor = Exception.class)
    public TestCaseVO exec(String caseId, CaseExecDTO dto, String userId) {
        String 状态 = dto.getExecStatus() == null ? "" : dto.getExecStatus().trim();
        if (!允许的执行状态.contains(状态)) {
            throw new BizException("执行结果只能是通过、不通过或跳过");
        }
        if (STATUS_FAIL.equals(状态)
                && (dto.getActualResult() == null || dto.getActualResult().trim().isEmpty())) {
            throw new BizException("不通过必须填写实际结果，否则复盘时不知道当时哪里不对");
        }

        ReqTestCaseDO 用例 = 取用例(caseId);
        String now = LocalDateTime.now().format(时间格式);
        用例.setExecStatus(状态);
        用例.setBugId(dto.getBugId());
        用例.setUpdatedAt(now);
        reqTestCaseMapper.updateById(用例);

        ReqCaseResultDO 记录 = new ReqCaseResultDO();
        记录.setId(IdUtils.nextId());
        记录.setCaseId(用例.getId());
        记录.setTicketId(用例.getTicketId());
        记录.setRoundNo(用例.getRoundNo());
        记录.setExecStatus(状态);
        记录.setActualResult(dto.getActualResult());
        记录.setExecutorId(userId);
        记录.setExecutedAt(now);
        记录.setBugId(dto.getBugId());
        记录.setRemark(dto.getRemark());
        记录.setCreatedAt(now);
        reqCaseResultMapper.insert(记录);

        return toVO(用例);
    }

    /**
     * 覆盖率。UAT 通过时后端会用自己的这份再算一次，不信前端传的数。
     *
     * @param ticketId 单据 ID
     * @return 覆盖率
     */
    public CoverageVO coverage(String ticketId) {
        return 组装覆盖率(取单据(ticketId));
    }

    /**
     * 组装覆盖率：分母是本单验收标准，分子是最新一轮里被指到的标准。
     * <p>
     * 只认最新一轮：旧轮的用例已经过时，拿它算覆盖率会让「标准改了但用例没刷」永远看不出来。
     *
     * @param ticket 单据
     * @return 覆盖率视图
     */
    private CoverageVO 组装覆盖率(ReqTicketDO ticket) {
        List<ReqAcceptanceCriteriaDO> criteria = 标准列表(ticket.getId());
        List<Integer> 轮次表 = 所有轮次(ticket.getId());
        List<ReqTestCaseDO> 最新轮用例 = 轮次表.isEmpty()
                ? List.of() : 按轮次查(ticket.getId(), 轮次表.get(0));

        CoverageCalculator.Result 结果 = CoverageCalculator.calculate(criteria, 最新轮用例);

        List<CoverageVO.Missing> 缺失 = new ArrayList<>();
        for (int i = 0; i < 结果.missingSeqs().size(); i++) {
            缺失.add(new CoverageVO.Missing(
                    结果.missingSeqs().get(i), 结果.missingContents().get(i)));
        }

        CoverageVO vo = new CoverageVO();
        vo.setCriteriaTotal(结果.criteriaTotal());
        vo.setCovered(结果.covered());
        vo.setRate(结果.rate());
        vo.setMissing(缺失);
        vo.setFull(结果.full());
        vo.setUatWaiver(ticket.getUatWaiver());
        vo.setWaiverReason(ticket.getUatWaiverReason());
        return vo;
    }

    /**
     * 本单的验收标准，按 seq 升序。
     *
     * @param ticketId 单据 ID
     * @return 标准列表
     */
    private List<ReqAcceptanceCriteriaDO> 标准列表(String ticketId) {
        return reqAcceptanceCriteriaMapper.selectList(
                Wrappers.<ReqAcceptanceCriteriaDO>lambdaQuery()
                        .eq(ReqAcceptanceCriteriaDO::getTicketId, ticketId)
                        .orderByAsc(ReqAcceptanceCriteriaDO::getSeq));
    }

    /**
     * 本单已生成过哪些轮次，倒序。
     *
     * @param ticketId 单据 ID
     * @return 轮次列表
     */
    private List<Integer> 所有轮次(String ticketId) {
        List<ReqTestCaseDO> 全部 = reqTestCaseMapper.selectList(
                Wrappers.<ReqTestCaseDO>lambdaQuery()
                        .select(ReqTestCaseDO::getRoundNo)
                        .eq(ReqTestCaseDO::getTicketId, ticketId));
        List<Integer> 结果 = new ArrayList<>();
        for (ReqTestCaseDO 一条 : 全部) {
            if (一条.getRoundNo() != null && !结果.contains(一条.getRoundNo())) {
                结果.add(一条.getRoundNo());
            }
        }
        结果.sort((甲, 乙) -> 乙 - 甲);
        return 结果;
    }

    /**
     * 下一轮次：已有最大 +1，没有则第 1 轮。
     *
     * @param ticketId 单据 ID
     * @return 轮次
     */
    private int 下一轮次(String ticketId) {
        List<Integer> 轮次表 = 所有轮次(ticketId);
        return 轮次表.isEmpty() ? 1 : 轮次表.get(0) + 1;
    }

    /**
     * 按轮次取用例，编号升序。
     *
     * @param ticketId 单据 ID
     * @param 轮次     轮次
     * @return 用例列表
     */
    private List<ReqTestCaseDO> 按轮次查(String ticketId, int 轮次) {
        return reqTestCaseMapper.selectList(
                Wrappers.<ReqTestCaseDO>lambdaQuery()
                        .eq(ReqTestCaseDO::getTicketId, ticketId)
                        .eq(ReqTestCaseDO::getRoundNo, 轮次)
                        .orderByAsc(ReqTestCaseDO::getCaseNo));
    }

    /**
     * 生成用例编号：TC-REQ000001-01，第 2 轮起带轮次标记 TC-REQ000001-R2-01。
     * <p>
     * `case_no` 上有全局唯一索引，第二轮若沿用同一套序号会直接撞库。
     * 带轮次标记比整体重排序号好读 —— 一眼能看出这是同一张单第几轮的用例。
     *
     * @param ticket 单据
     * @param 轮次   轮次
     * @param 序号   本轮内序号
     * @return 用例编号
     */
    private String 用例编号(ReqTicketDO ticket, int 轮次, int 序号) {
        String 单号 = ticket.getTicketNo() == null ? "" : ticket.getTicketNo();
        String 尾号 = 单号.length() > 6 ? 单号.substring(单号.length() - 6) : 单号;
        String 轮标记 = 轮次 <= 1 ? "" : "R" + 轮次 + "-";
        return "TC-" + ticket.getTicketType() + 尾号 + "-" + 轮标记 + String.format("%02d", 序号);
    }

    /**
     * 步骤序列化成 JSON 数组。
     *
     * @param 步骤 步骤列表
     * @return JSON 字符串
     */
    private String 序列化步骤(List<String> 步骤) {
        if (步骤 == null || 步骤.isEmpty()) {
            return "[]";
        }
        try {
            return MAPPER.writeValueAsString(步骤);
        } catch (Exception 异常) {
            // 序列化的是纯字符串数组，正常不会失败；失败也不能让整次保存崩掉
            return "[]";
        }
    }

    /**
     * 步骤 JSON 反解。坏数据返回空数组而不是抛异常 —— 打不开页面比看不到步骤更糟。
     *
     * @param json JSON 字符串
     * @return 步骤列表
     */
    private List<String> 解析步骤(String json) {
        if (json == null || json.trim().isEmpty()) {
            return List.of();
        }
        try {
            return MAPPER.readValue(json, new TypeReference<List<String>>() {
            });
        } catch (Exception 异常) {
            return List.of();
        }
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
     * 取用例，不存在直接报错。
     *
     * @param caseId 用例 ID
     * @return 用例
     */
    private ReqTestCaseDO 取用例(String caseId) {
        ReqTestCaseDO 用例 = reqTestCaseMapper.selectById(caseId);
        if (用例 == null) {
            throw new BizException("用例不存在");
        }
        return 用例;
    }

    /**
     * 批量转视图。
     *
     * @param cases 用例
     * @return 视图列表
     */
    private List<TestCaseVO> toVOList(List<ReqTestCaseDO> cases) {
        List<TestCaseVO> 结果 = new ArrayList<>(cases.size());
        for (ReqTestCaseDO 一条 : cases) {
            结果.add(toVO(一条));
        }
        return 结果;
    }

    /**
     * 单条转视图。
     *
     * @param 用例 用例
     * @return 视图
     */
    private TestCaseVO toVO(ReqTestCaseDO 用例) {
        TestCaseVO vo = new TestCaseVO();
        vo.setId(用例.getId());
        vo.setCaseNo(用例.getCaseNo());
        vo.setTitle(用例.getTitle());
        vo.setPrecond(用例.getPrecond());
        vo.setSteps(解析步骤(用例.getSteps()));
        vo.setExpected(用例.getExpected());
        vo.setPriority(用例.getPriority());
        vo.setSourceCriteria(用例.getSourceCriteria());
        vo.setCriteriaIndex(用例.getCriteriaIndex());
        vo.setFocusFlag(用例.getFocusFlag());
        vo.setFocusReason(用例.getFocusReason());
        vo.setOrigin(用例.getOrigin());
        vo.setExecStatus(用例.getExecStatus());
        vo.setExecStatusLabel(执行状态名(用例.getExecStatus()));
        vo.setBugId(用例.getBugId());
        vo.setRoundNo(用例.getRoundNo());
        vo.setUpdatedAt(用例.getUpdatedAt());
        return vo;
    }

    /**
     * 执行状态中文名。
     *
     * @param 状态 状态码
     * @return 中文名
     */
    private String 执行状态名(String 状态) {
        if (状态 == null) {
            return "未执行";
        }
        return switch (状态) {
            case STATUS_PASS -> "通过";
            case STATUS_FAIL -> "不通过";
            case STATUS_SKIP -> "跳过";
            default -> "未执行";
        };
    }

    /**
     * 按状态计数。
     *
     * @param cases  用例
     * @param 目标状态 状态码
     * @return 条数
     */
    private int 计数(List<ReqTestCaseDO> cases, String 目标状态) {
        int 数量 = 0;
        for (ReqTestCaseDO 一条 : cases) {
            if (目标状态.equals(一条.getExecStatus())) {
                数量++;
            }
        }
        return 数量;
    }

    /**
     * 已执行条数：不看具体结果，只要有结果就算。
     * <p>
     * 不并进 {@link #计数} 是因为「已执行」没有单一状态码，
     * 用哨兵值去比会把逻辑绕成假的。
     *
     * @param cases 用例
     * @return 条数
     */
    private int 计数已执行(List<ReqTestCaseDO> cases) {
        int 数量 = 0;
        for (ReqTestCaseDO 一条 : cases) {
            if (一条.getExecStatus() != null && !STATUS_NOT_RUN.equals(一条.getExecStatus())) {
                数量++;
            }
        }
        return 数量;
    }
}
