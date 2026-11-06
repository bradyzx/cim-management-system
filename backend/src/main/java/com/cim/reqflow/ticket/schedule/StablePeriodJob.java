package com.cim.reqflow.ticket.schedule;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.cim.reqflow.common.constant.CommonConstants;
import com.cim.reqflow.ticket.entity.ReqTicketDO;
import com.cim.reqflow.ticket.flow.FlowAction;
import com.cim.reqflow.ticket.flow.TicketStatus;
import com.cim.reqflow.ticket.mapper.ReqTicketMapper;
import com.cim.reqflow.ticket.service.TicketFlowService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 稳定期定时器：点检通过的单据满 7×24 小时后自动转「待评价」。
 * <p>
 * 这是全流程里唯一没人操作的节点（`02` 的 N26），必须有定时任务 ——
 * 没有它，所有单子会永远停在「IT 点检」，评价环节永远走不到，闭环断在最后一步。
 * <p>
 * 两个口径细节（`02` 已经拍过，别改）：
 * <ul>
 *   <li><b>从 `release_at`（上线时刻）起算</b>，不从验收通过日或点检通过日算。
 *       业务口径是「上线即开始观察」，否则提出人不点验收就能无限延长观察期；</li>
 *   <li>一期按自然 7 天，不做 INC 占位时长扣除。二期接关联故障单后，未关闭的故障期间要暂停计时。</li>
 * </ul>
 * 每分钟扫一次：7 天为粒度的规则本身不需要秒级精度，但一分钟一次只是
 * 一条带索引的 COUNT 查询，换来的是「满 7 天后最多一分钟就转」，演示与联调时不用干等。
 *
 * @author Haijie Xiao
 * @date 2026-11-05
 */
@Component
public class StablePeriodJob {

    private static final Logger log = LoggerFactory.getLogger(StablePeriodJob.class);

    /** 稳定观察期天数 */
    private static final int STABLE_DAYS = 7;

    private final ReqTicketMapper reqTicketMapper;

    private final TicketFlowService ticketFlowService;

    public StablePeriodJob(ReqTicketMapper reqTicketMapper,
                           TicketFlowService ticketFlowService) {
        this.reqTicketMapper = reqTicketMapper;
        this.ticketFlowService = ticketFlowService;
    }

    /**
     * 扫描满 7 天的点检单并推进。
     * <p>
     * 单条失败只记日志不中断整批：一张单的数据有问题，不该让其他单也卡住。
     * 并发安全靠条件更新兜底，同一张单被扫两次时第二次会失败，属正常。
     */
    @Scheduled(cron = "0 * * * * ?")
    public void 推进满七天的单() {
        String 截止 = LocalDateTime.now().minusDays(STABLE_DAYS)
                .format(CommonConstants.DATE_TIME_FORMATTER);

        List<ReqTicketDO> 待转 = reqTicketMapper.selectList(
                Wrappers.<ReqTicketDO>lambdaQuery()
                        .eq(ReqTicketDO::getStatus, TicketStatus.IT_INSPECTION)
                        .isNotNull(ReqTicketDO::getReleaseAt)
                        .le(ReqTicketDO::getReleaseAt, 截止));

        if (待转.isEmpty()) {
            return;
        }

        log.info("稳定期扫描：{} 张单已满 {} 天，准备转待评价", 待转.size(), STABLE_DAYS);
        for (ReqTicketDO 单 : 待转) {
            try {
                ticketFlowService.executeSystem(单.getId(), FlowAction.STABLE_7D,
                        "上线满 " + STABLE_DAYS + " 天（自 " + 单.getReleaseAt() + " 起算），系统自动转待评价");
                log.info("单据 {} 已转待评价", 单.getTicketNo());
            } catch (Exception 异常) {
                // 常见原因是这张单刚被人工处理过（状态已变），第二天的扫描不会重复报
                log.warn("单据 {} 自动转待评价失败：{}", 单.getTicketNo(), 异常.getMessage());
            }
        }
    }
}
