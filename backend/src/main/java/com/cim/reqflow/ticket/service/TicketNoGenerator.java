package com.cim.reqflow.ticket.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.cim.reqflow.common.BizException;
import com.cim.reqflow.common.util.IdUtils;
import com.cim.reqflow.system.entity.SysNoSequenceDO;
import com.cim.reqflow.system.mapper.SysNoSequenceMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 单据编号发号器。
 * <p>
 * 取号用 CAS：{@code UPDATE ... SET current_no = 旧值 + 1 WHERE current_no = 旧值}，
 * 更新行数为 0 说明有并发竞争，重试即可。SQLite 单写库竞争窗口极小，
 * 5 次重试足够；真扛不住高并发的场景也不该用 SQLite。
 * <p>
 * 跨年自动建新段：按「seq_code + 当前年」找不到行时初始化一段，
 * 初始化也可能撞并发（两个人同时跨年第一秒提单），捕获重复键后重试走正常取号。
 *
 * @author Haijie Xiao
 * @date 2026-11-04
 */
@Service
public class TicketNoGenerator {

    /** 发号段码前缀，与种子数据的 seq_code 一致 */
    private static final String SEQ_CODE_PREFIX = "TICKET_";

    /** 按年切段 */
    private static final String PERIOD_YEAR = "YEAR";

    /** 段键占位（NONE 段用） */
    private static final String PERIOD_NONE_KEY = "*";

    /** CAS 冲突最大重试次数 */
    private static final int 最大重试 = 5;

    /** 默认补零位数，与种子一致 */
    private static final int 默认补零 = 6;

    private static final DateTimeFormatter 年格式 = DateTimeFormatter.ofPattern("yyyy");

    private final SysNoSequenceMapper sysNoSequenceMapper;

    public TicketNoGenerator(SysNoSequenceMapper sysNoSequenceMapper) {
        this.sysNoSequenceMapper = sysNoSequenceMapper;
    }

    /**
     * 取下一个单据编号，如 REQ-2026-000007。
     *
     * @param ticketType 单据类型 REQ / BUG / INC
     * @return 单据编号
     */
    public String next(String ticketType) {
        String seqCode = SEQ_CODE_PREFIX + ticketType;
        String periodKey = LocalDate.now().format(年格式);

        for (int 尝试 = 0; 尝试 < 最大重试; 尝试++) {
            SysNoSequenceDO seq = findSegment(seqCode, periodKey);
            if (seq == null) {
                seq = initSegment(seqCode, ticketType, periodKey);
                if (seq == null) {
                    // 初始化撞了并发（别人先建了段），重试走正常取号
                    continue;
                }
            }

            int 旧值 = seq.getCurrentNo() == null ? 0 : seq.getCurrentNo();
            int 新值 = 旧值 + 1;
            int 更新行数 = sysNoSequenceMapper.update(null,
                    Wrappers.<SysNoSequenceDO>lambdaUpdate()
                            .eq(SysNoSequenceDO::getId, seq.getId())
                            .eq(SysNoSequenceDO::getCurrentNo, 旧值)
                            .set(SysNoSequenceDO::getCurrentNo, 新值)
                            .set(SysNoSequenceDO::getUpdatedAt, now()));
            if (更新行数 > 0) {
                return formatNo(seq, periodKey, 新值);
            }
            // 更新行数为 0 = CAS 失败，别人刚取过号，重试
        }
        throw new BizException("单据编号获取失败：并发冲突，请重试");
    }

    /**
     * 查当前段。
     *
     * @param seqCode  段码
     * @param periodKey 段键
     * @return 段记录，不存在返回 null
     */
    private SysNoSequenceDO findSegment(String seqCode, String periodKey) {
        return sysNoSequenceMapper.selectOne(
                Wrappers.<SysNoSequenceDO>lambdaQuery()
                        .eq(SysNoSequenceDO::getSeqCode, seqCode)
                        .eq(SysNoSequenceDO::getPeriodKey, periodKey)
                        .last("limit 1"));
    }

    /**
     * 初始化新段（跨年或首次使用）。
     *
     * @param seqCode   段码
     * @param ticketType 单据类型
     * @param periodKey  段键
     * @return 新段；撞并发（别人已建）返回 null
     */
    private SysNoSequenceDO initSegment(String seqCode, String ticketType, String periodKey) {
        SysNoSequenceDO seq = new SysNoSequenceDO();
        seq.setId(IdUtils.nextId());
        seq.setSeqCode(seqCode);
        seq.setPrefix(ticketType);
        seq.setPeriodType(PERIOD_YEAR);
        seq.setPeriodKey(periodKey);
        seq.setCurrentNo(0);
        seq.setPadding(默认补零);
        seq.setNoPattern("${prefix}-${period}-${seq}");
        seq.setEnabled(1);
        seq.setRemark("自动创建：" + periodKey + " 年度段");
        seq.setCreatedAt(now());
        seq.setUpdatedAt(now());
        try {
            sysNoSequenceMapper.insert(seq);
            return seq;
        } catch (RuntimeException exception) {
            // 段被并发抢先创建：返回 null 让调用方重试读段
            return null;
        }
    }

    /**
     * 按模板拼编号。
     *
     * @param seq       段配置
     * @param periodKey 段键
     * @param 序号      序号
     * @return 如 REQ-2026-000007
     */
    private String formatNo(SysNoSequenceDO seq, String periodKey, int 序号) {
        String 补零 = String.format("%0" + (seq.getPadding() == null ? 默认补零 : seq.getPadding()) + "d", 序号);
        String 段 = PERIOD_NONE_KEY.equals(periodKey) ? "" : periodKey;
        return seq.getNoPattern()
                .replace("${prefix}", seq.getPrefix() == null ? "" : seq.getPrefix())
                .replace("${period}", 段)
                .replace("${seq}", 补零);
    }

    /**
     * 当前时间，格式与全库一致。
     *
     * @return yyyy-MM-dd HH:mm:ss
     */
    private String now() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }
}
