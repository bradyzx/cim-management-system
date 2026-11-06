package com.cim.reqflow.ticket.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.cim.reqflow.common.BizException;
import com.cim.reqflow.common.util.IdUtils;
import com.cim.reqflow.system.entity.SysUserDO;
import com.cim.reqflow.system.mapper.SysUserMapper;
import com.cim.reqflow.ticket.dto.CheckItemSaveDTO;
import com.cim.reqflow.ticket.entity.ReqCheckItemDO;
import com.cim.reqflow.ticket.entity.ReqDocDO;
import com.cim.reqflow.ticket.entity.ReqTicketDO;
import com.cim.reqflow.ticket.mapper.ReqCheckItemMapper;
import com.cim.reqflow.ticket.mapper.ReqDocMapper;
import com.cim.reqflow.ticket.mapper.ReqTicketMapper;
import com.cim.reqflow.ticket.vo.CheckItemListVO;
import com.cim.reqflow.ticket.vo.CheckItemVO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
 * 上线点检：按重点功能清单逐项核对（`02` 4.5）。
 * <p>
 * 两条硬规则：
 * <ul>
 *   <li><b>清单为空不卡流程</b>：新建系统在清单没维护前，所有单子都得能走完点检，
 *       否则上线流程直接死锁。空清单时由流转层在日志里标 `check_items_empty=1`；</li>
 *   <li><b>清单非空就要真点</b>：重点项没有结果就点「点检通过」，等于把点检做成走过场，
 *       所以拦在 {@code INSPECT_PASS} 前面。</li>
 * </ul>
 * 草稿从需求文档第 5 节功能清单带出（`source=AI_SUGGEST`）：AI 出文档时已经有 F-01、
 * F-02…，从这里生成比让人重新录一遍强。人确认后由前端改为 MANUAL。
 *
 * @author Haijie Xiao
 * @date 2026-11-05
 */
@Service
public class CheckItemService {

    /** 解析需求文档用 */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 库里统一存字符串时间 */
    private static final DateTimeFormatter 时间格式 = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 需求文档第 5 节的键名，与 DocSections 保持一致 */
    private static final String 功能清单节 = "s5_functions";

    /** 来源：从需求文档带出 */
    private static final String SOURCE_AI = "AI_SUGGEST";
    /** 来源：人工维护 */
    private static final String SOURCE_MANUAL = "MANUAL";

    /** 结果：通过 */
    private static final String CHECK_PASS = "PASS";
    /** 结果：不通过 */
    private static final String CHECK_FAIL = "FAIL";
    /** 结果：不适用 */
    private static final String CHECK_NA = "NA";

    /** 允许填写的三种结果 */
    private static final Set<String> 允许的结果 = Set.of(CHECK_PASS, CHECK_FAIL, CHECK_NA);

    /** 标记位：是 */
    private static final int FLAG_YES = 1;

    private final ReqCheckItemMapper reqCheckItemMapper;

    private final ReqDocMapper reqDocMapper;

    private final ReqTicketMapper reqTicketMapper;

    private final SysUserMapper sysUserMapper;

    public CheckItemService(ReqCheckItemMapper reqCheckItemMapper,
                            ReqDocMapper reqDocMapper,
                            ReqTicketMapper reqTicketMapper,
                            SysUserMapper sysUserMapper) {
        this.reqCheckItemMapper = reqCheckItemMapper;
        this.reqDocMapper = reqDocMapper;
        this.reqTicketMapper = reqTicketMapper;
        this.sysUserMapper = sysUserMapper;
    }

    /**
     * 点检清单。`empty=1` 表示未配置，前端据此显示「本单未配置点检项」并允许直接通过。
     *
     * @param ticketId 单据 ID
     * @return 清单
     */
    public CheckItemListVO list(String ticketId) {
        取单据(ticketId);
        List<ReqCheckItemDO> items = reqCheckItemMapper.selectList(
                Wrappers.<ReqCheckItemDO>lambdaQuery()
                        .eq(ReqCheckItemDO::getTicketId, ticketId)
                        .orderByAsc(ReqCheckItemDO::getSortNo));

        Map<String, String> 人名 = 批量取姓名(items);

        int 重点总数 = 0;
        int 重点已检 = 0;
        int 已检 = 0;
        List<CheckItemVO> 视图 = new ArrayList<>(items.size());
        for (ReqCheckItemDO 项 : items) {
            boolean 已点检 = 项.getCheckResult() != null && !项.getCheckResult().isEmpty();
            boolean 是重点 = 项.getIsKey() != null && 项.getIsKey() == FLAG_YES;
            if (是重点) {
                重点总数++;
                if (已点检) {
                    重点已检++;
                }
            }
            if (已点检) {
                已检++;
            }
            视图.add(toVO(项, 人名));
        }

        CheckItemListVO vo = new CheckItemListVO();
        vo.setItems(视图);
        vo.setEmpty(items.isEmpty() ? 1 : 0);
        vo.setKeyTotal(重点总数);
        vo.setKeyChecked(重点已检);
        vo.setTotal(items.size());
        vo.setChecked(已检);
        return vo;
    }

    /**
     * 从需求文档第 5 节功能清单生成点检项草稿。
     * <p>
     * 不覆盖已有的：人已经确认过清单之后再生成一次，会把人工补充的项冲掉，
     * 而人工补充的项往往是「AI 想不到的业务约束」。
     *
     * @param ticketId 单据 ID
     * @param userId   操作人
     * @return 生成后的清单
     */
    @Transactional(rollbackFor = Exception.class)
    public CheckItemListVO fromDoc(String ticketId, String userId) {
        ReqTicketDO ticket = 取单据(ticketId);

        Long 已有 = reqCheckItemMapper.selectCount(
                Wrappers.<ReqCheckItemDO>lambdaQuery()
                        .eq(ReqCheckItemDO::getTicketId, ticketId));
        if (已有 != null && 已有 > 0) {
            throw new BizException("本单已有点检项（" + 已有 + " 条），不重复生成。"
                    + "如需按最新文档重来，请先清空现有清单");
        }

        ReqDocDO doc = reqDocMapper.selectOne(Wrappers.<ReqDocDO>lambdaQuery()
                .eq(ReqDocDO::getTicketId, ticketId)
                .orderByDesc(ReqDocDO::getVersion)
                .last("limit 1"));
        if (doc == null) {
            throw new BizException("本单还没有需求文档，无法生成点检项草稿");
        }

        JsonNode 功能项 = 解析(doc.getDocJson())
                .path("sections").path(功能清单节).path("items");
        if (!功能项.isArray() || 功能项.isEmpty()) {
            throw new BizException("需求文档第 5 节功能清单为空，请先把功能清单补齐");
        }

        String now = LocalDateTime.now().format(时间格式);
        int 序号 = 0;
        for (JsonNode 项 : 功能项) {
            String 名称 = 项.path("text").asText("").trim();
            if (名称.isEmpty()) {
                continue;
            }
            序号++;

            ReqCheckItemDO 点检项 = new ReqCheckItemDO();
            点检项.setId(IdUtils.nextId());
            点检项.setTicketId(ticketId);
            点检项.setSystemId(ticket.getSystemId());
            点检项.setItemName(取短名(名称));
            // 「怎么验」写具体的核对动作，不写「验证功能是否正常」——那等于没说
            点检项.setItemDesc("在生产环境按该功能点的正常业务路径操作一次，核对实际表现");
            点检项.setExpectResult(名称);
            // 从功能清单带出的一律先当重点：AI 分不清哪些是边角，让人来降级比让人漏掉安全
            点检项.setIsKey(FLAG_YES);
            点检项.setSortNo(序号);
            点检项.setSource(SOURCE_AI);
            点检项.setCreatedAt(now);
            点检项.setUpdatedAt(now);
            reqCheckItemMapper.insert(点检项);
        }

        return list(ticketId);
    }

    /**
     * 单项登记。不通过必须写清哪里不对 —— 只打叉不写字，复盘时只剩一个「没通过」。
     *
     * @param itemId 点检项 ID
     * @param dto    结果与说明
     * @param userId 点检人
     * @return 更新后的清单
     */
    @Transactional(rollbackFor = Exception.class)
    public CheckItemListVO update(String itemId, CheckItemSaveDTO dto, String userId) {
        String 结果 = dto.getCheckResult() == null ? "" : dto.getCheckResult().trim();
        if (!允许的结果.contains(结果)) {
            throw new BizException("点检结果只能是通过、不通过或不适用");
        }
        if (CHECK_FAIL.equals(结果)
                && (dto.getCheckRemark() == null || dto.getCheckRemark().trim().isEmpty())) {
            throw new BizException("不通过必须填写说明，否则复盘时不知道哪里不对");
        }

        ReqCheckItemDO 项 = reqCheckItemMapper.selectById(itemId);
        if (项 == null) {
            throw new BizException("点检项不存在");
        }

        String now = LocalDateTime.now().format(时间格式);
        项.setCheckResult(结果);
        项.setCheckRemark(dto.getCheckRemark());
        项.setCheckedBy(userId);
        项.setCheckedAt(now);
        项.setUpdatedAt(now);
        reqCheckItemMapper.updateById(项);

        return list(项.getTicketId());
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
     * 批量取点检人姓名，避免逐条查用户表。
     *
     * @param items 点检项
     * @return 用户 ID 到姓名
     */
    private Map<String, String> 批量取姓名(List<ReqCheckItemDO> items) {
        Set<String> ids = new HashSet<>();
        for (ReqCheckItemDO 项 : items) {
            if (项.getCheckedBy() != null && !项.getCheckedBy().isEmpty()) {
                ids.add(项.getCheckedBy());
            }
        }
        Map<String, String> 结果 = new HashMap<>();
        if (ids.isEmpty()) {
            return 结果;
        }
        for (SysUserDO 用户 : sysUserMapper.selectBatchIds(ids)) {
            结果.put(用户.getId(), 用户.getRealName());
        }
        return 结果;
    }

    /**
     * 解析 doc_json。损坏时给空对象，让调用方按「清单为空」处理，不抛异常。
     *
     * @param docJson 结构化字符串
     * @return 节点
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
     * 功能点名称截断：点检项名称列不宽，整段功能描述塞进去会占三行。
     *
     * @param 名称 功能描述
     * @return 短名
     */
    private String 取短名(String 名称) {
        String 去换行 = 名称.replaceAll("\\s+", " ");
        return 去换行.length() <= 40 ? 去换行 : 去换行.substring(0, 40) + "…";
    }

    /**
     * 转视图。
     *
     * @param 项   点检项
     * @param 人名 用户 ID 到姓名
     * @return 视图
     */
    private CheckItemVO toVO(ReqCheckItemDO 项, Map<String, String> 人名) {
        CheckItemVO vo = new CheckItemVO();
        vo.setId(项.getId());
        vo.setItemName(项.getItemName());
        vo.setItemDesc(项.getItemDesc());
        vo.setExpectResult(项.getExpectResult());
        vo.setIsKey(项.getIsKey());
        vo.setSortNo(项.getSortNo());
        vo.setSource(项.getSource());
        vo.setSourceLabel(SOURCE_MANUAL.equals(项.getSource()) ? "人工维护" : "文档带出");
        vo.setCheckResult(项.getCheckResult());
        vo.setCheckResultLabel(结果名(项.getCheckResult()));
        vo.setCheckRemark(项.getCheckRemark());
        vo.setCheckedByName(人名.get(项.getCheckedBy()));
        vo.setCheckedAt(项.getCheckedAt());
        return vo;
    }

    /**
     * 结果码转中文。
     *
     * @param 结果 结果码
     * @return 中文名，未点检返回 null
     */
    private String 结果名(String 结果) {
        if (结果 == null || 结果.isEmpty()) {
            return null;
        }
        return switch (结果) {
            case CHECK_PASS -> "通过";
            case CHECK_FAIL -> "不通过";
            case CHECK_NA -> "不适用";
            default -> 结果;
        };
    }
}
