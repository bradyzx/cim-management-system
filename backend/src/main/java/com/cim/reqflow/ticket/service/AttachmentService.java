package com.cim.reqflow.ticket.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.cim.reqflow.common.BizException;
import com.cim.reqflow.common.util.IdUtils;
import com.cim.reqflow.system.entity.SysDictDataDO;
import com.cim.reqflow.system.entity.SysUserDO;
import com.cim.reqflow.system.mapper.SysDictDataMapper;
import com.cim.reqflow.system.mapper.SysUserMapper;
import com.cim.reqflow.system.service.UserAccessService;
import com.cim.reqflow.ticket.entity.ReqAttachRuleDO;
import com.cim.reqflow.ticket.entity.ReqAttachmentDO;
import com.cim.reqflow.ticket.entity.ReqProjectDO;
import com.cim.reqflow.ticket.entity.ReqTicketDO;
import com.cim.reqflow.ticket.flow.FlowAction;
import com.cim.reqflow.ticket.flow.FlowRegistry;
import com.cim.reqflow.ticket.flow.FlowRoleSets;
import com.cim.reqflow.ticket.flow.FlowTarget;
import com.cim.reqflow.ticket.flow.TicketStatus;
import com.cim.reqflow.ticket.mapper.ReqAttachRuleMapper;
import com.cim.reqflow.ticket.mapper.ReqAttachmentMapper;
import com.cim.reqflow.ticket.mapper.ReqProjectMapper;
import com.cim.reqflow.ticket.mapper.ReqTicketMapper;
import com.cim.reqflow.ticket.storage.AttachmentStorage;
import com.cim.reqflow.ticket.vo.AttachRuleStatusVO;
import com.cim.reqflow.ticket.vo.AttachmentUploadVO;
import com.cim.reqflow.ticket.vo.AttachmentVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 附件服务：上传、列表、下载寻址、软删除、确认版、必填缺口。
 * <p>
 * 三条不可动摇的规则：
 * <ol>
 *   <li><b>stage 由后端取当前状态</b>。接口不接收 stage —— 一旦允许前端指定，
 *       就能把设计文档挂到「上线」节点下，按节点归档的证据链立刻失真。</li>
 *   <li><b>必填缺口与流转拦截同源</b>。都读 {@code req_attach_rule}，
 *       不允许界面自己算一套，否则会出现「页面显示齐了，点按钮被拦」。</li>
 *   <li><b>关闭后不可再改</b>。{@code CLOSED} 单据拒绝上传、删除、确认，
 *       附件是验收证据，结案后再动就等于改账。</li>
 * </ol>
 *
 * @author Haijie Xiao
 * @date 2026-11-04
 */
@Service
public class AttachmentService {

    private static final Logger log = LoggerFactory.getLogger(AttachmentService.class);

    /** 标记位：否 */
    private static final int FLAG_NO = 0;

    /** 标记位：是 */
    private static final int FLAG_YES = 1;

    /** 字典类型：交付物 */
    private static final String DICT_ATTACH_TYPE = "attach_type";

    /** 字典类型：单据状态 */
    private static final String DICT_TICKET_STATUS = "ticket_status";

    /** 规则里的通配符：任意状态 / 任意单据类型 */
    private static final String WILDCARD = "*";

    /** 下载地址前缀，与 Controller 的映射保持一致 */
    private static final String DOWNLOAD_PREFIX = "/ticket/attachment/";

    /** 下载地址后缀 */
    private static final String DOWNLOAD_SUFFIX = "/download";

    /** 项目查不到时的兜底目录名。宁可放在 UNKNOWN 下，也不能让上传失败 */
    private static final String UNKNOWN_PROJECT = "UNKNOWN";

    /** 时间格式，与全库一致 */
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 字节与 KB 的换算基数 */
    private static final double BYTES_PER_KB = 1024.0;

    /** 一 MB 的字节数 */
    private static final double BYTES_PER_MB = 1024.0 * 1024.0;

    /** 进程内禁传的可执行与脚本扩展名。这类文件点开就能运行，作为附件没有任何正当用途 */
    private static final Set<String> BLOCKED_EXTENSIONS = Set.of(
            "exe", "bat", "cmd", "com", "scr", "pif", "msi", "dll", "so",
            "vbs", "vbe", "js", "jse", "wsf", "wsh", "ps1", "sh", "jar");

    private final ReqTicketMapper reqTicketMapper;

    private final ReqAttachmentMapper reqAttachmentMapper;

    private final ReqAttachRuleMapper reqAttachRuleMapper;

    private final ReqProjectMapper reqProjectMapper;

    private final SysDictDataMapper sysDictDataMapper;

    private final SysUserMapper sysUserMapper;

    private final UserAccessService userAccessService;

    private final AttachmentStorage attachmentStorage;

    public AttachmentService(ReqTicketMapper reqTicketMapper,
                             ReqAttachmentMapper reqAttachmentMapper,
                             ReqAttachRuleMapper reqAttachRuleMapper,
                             ReqProjectMapper reqProjectMapper,
                             SysDictDataMapper sysDictDataMapper,
                             SysUserMapper sysUserMapper,
                             UserAccessService userAccessService,
                             AttachmentStorage attachmentStorage) {
        this.reqTicketMapper = reqTicketMapper;
        this.reqAttachmentMapper = reqAttachmentMapper;
        this.reqAttachRuleMapper = reqAttachRuleMapper;
        this.reqProjectMapper = reqProjectMapper;
        this.sysDictDataMapper = sysDictDataMapper;
        this.sysUserMapper = sysUserMapper;
        this.userAccessService = userAccessService;
        this.attachmentStorage = attachmentStorage;
    }

    /**
     * 上传附件。
     * <p>
     * 命中「同单 + 同类型 + 同指纹」时不报错，而是返回已有附件并标记 repeated：
     * 用户重复点击上传是常态，报错会让他以为没传成功，然后继续点第三次。
     *
     * @param ticketId   单据 ID
     * @param attachType 交付物类型码
     * @param file       上传的文件
     * @param userId     上传人
     * @return 上传结果
     */
    public AttachmentUploadVO upload(String ticketId, String attachType, MultipartFile file, String userId) {
        ReqTicketDO ticket = getTicket(ticketId);
        checkModifiable(ticket);
        String type = checkAttachType(attachType);
        checkFile(file);

        String originalName = StringUtils.cleanPath(
                file.getOriginalFilename() == null ? "unnamed" : file.getOriginalFilename());
        String ext = attachmentStorage.extensionOf(originalName);
        if (BLOCKED_EXTENSIONS.contains(ext)) {
            throw new BizException("不允许上传可执行或脚本文件（." + ext + "），请打包后上传");
        }

        String storedName = UUID.randomUUID().toString().replace("-", "")
                + (ext.isEmpty() ? "" : "." + ext);
        String relativePath = attachmentStorage.buildRelativePath(
                resolveProjectCode(ticket), ticket.getTicketNo(), ticket.getStatus(), storedName);

        String sha256;
        try (InputStream inputStream = file.getInputStream()) {
            sha256 = attachmentStorage.save(relativePath, inputStream);
        } catch (IOException exception) {
            throw new BizException("读取上传文件失败，请重试", exception);
        }

        ReqAttachmentDO existing = findSameFile(ticketId, type, sha256);
        if (existing != null) {
            // 同样的文件已经在库里，刚写的那份是多余的，删掉避免磁盘上出现无主副本
            attachmentStorage.delete(relativePath);
            return buildUploadResult(existing, userId, true);
        }

        ReqAttachmentDO attachment = new ReqAttachmentDO();
        attachment.setId(IdUtils.nextId());
        attachment.setTicketId(ticketId);
        attachment.setStage(ticket.getStatus());
        attachment.setAttachType(type);
        attachment.setOriginalName(originalName);
        attachment.setRelativePath(relativePath);
        attachment.setSize(file.getSize());
        attachment.setMimeType(file.getContentType());
        attachment.setSha256(sha256);
        attachment.setExtractedText(attachmentStorage.extractText(relativePath, ext));
        attachment.setUploaderId(userId);
        attachment.setIsConfirmed(FLAG_NO);
        attachment.setDeleted(FLAG_NO);
        attachment.setCreatedAt(now());

        try {
            reqAttachmentMapper.insert(attachment);
        } catch (RuntimeException exception) {
            // 落库失败必须回滚文件，否则磁盘会攒下一堆库里查不到的孤儿文件
            attachmentStorage.delete(relativePath);
            throw exception;
        }

        log.info("附件上传成功：单号={}，类型={}，文件={}，大小={}",
                ticket.getTicketNo(), type, originalName, file.getSize());
        return buildUploadResult(attachment, userId, false);
    }

    /**
     * 列出单据的全部附件，按上传时间升序。前端按 stage 分组展示。
     *
     * @param ticketId 单据 ID
     * @param userId   当前用户，用于计算可删除标记
     * @return 附件列表
     */
    public List<AttachmentVO> list(String ticketId, String userId) {
        ReqTicketDO ticket = getTicket(ticketId);
        List<ReqAttachmentDO> attachments = reqAttachmentMapper.selectList(
                Wrappers.<ReqAttachmentDO>lambdaQuery()
                        .eq(ReqAttachmentDO::getTicketId, ticketId)
                        .eq(ReqAttachmentDO::getDeleted, FLAG_NO)
                        .orderByAsc(ReqAttachmentDO::getCreatedAt)
                        .orderByAsc(ReqAttachmentDO::getId));

        Map<String, String> typeLabels = loadDictLabels(DICT_ATTACH_TYPE);
        Map<String, String> statusLabels = loadDictLabels(DICT_TICKET_STATUS);
        Map<String, String> userNames = loadUserNames(attachments);
        boolean admin = isAdmin(userId);

        List<AttachmentVO> result = new ArrayList<>(attachments.size());
        for (ReqAttachmentDO attachment : attachments) {
            result.add(buildVO(attachment, typeLabels, statusLabels, userNames, userId, admin,
                    ticket.getStatus()));
        }
        return result;
    }

    /**
     * 当前状态往各个后继状态流转时，分别需要哪些附件、还差多少。
     * <p>
     * 规则按到达状态配置，所以查询的目标不是「当前状态」而是「出边的目标状态」：
     * 停在 DESIGNING 时用户关心的是「提交设计评审要交 FDS」，
     * 而 DSGN_REVIEW_IT 正是 SUBMIT_DESIGN 的到达状态。
     * 一个状态有多条出边时（如 UAT 可去 RELEASE_PENDING 或退回 DEVELOPING），
     * 按 targetStatus 分组返回，前端分组展示。
     * <p>
     * 与 {@code PreconditionChecker.checkAttachments} 读同一张表、同一套通配符规则，
     * 那处改了这里必须同步，否则界面与拦截会对不上。
     *
     * @param ticketId 单据 ID
     * @return 按目标状态分组的缺口
     */
    public List<AttachRuleStatusVO> listRuleStatus(String ticketId) {
        ReqTicketDO ticket = getTicket(ticketId);
        Map<String, String> typeLabels = loadDictLabels(DICT_ATTACH_TYPE);
        Map<String, String> statusLabels = loadDictLabels(DICT_TICKET_STATUS);

        List<AttachRuleStatusVO> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String targetStatus : collectTargetStatuses(ticket.getStatus())) {
            List<ReqAttachRuleDO> rules = findRules(ticket.getTicketType(), targetStatus, false);
            for (ReqAttachRuleDO rule : rules) {
                if (!seen.add(targetStatus + "|" + rule.getAttachType())) {
                    continue;
                }
                int uploaded = countUploaded(ticketId, rule.getAttachType());
                int minCount = rule.getMinCount() == null ? 1 : rule.getMinCount();

                AttachRuleStatusVO status = new AttachRuleStatusVO();
                status.setTargetStatus(targetStatus);
                status.setTargetStatusLabel(statusLabels.getOrDefault(targetStatus, targetStatus));
                status.setAttachType(rule.getAttachType());
                status.setAttachTypeLabel(typeLabels.getOrDefault(rule.getAttachType(), rule.getAttachType()));
                status.setRequired(rule.getRequired() != null && rule.getRequired() == FLAG_YES);
                status.setMinCount(minCount);
                status.setUploaded(uploaded);
                status.setSatisfied(uploaded >= minCount);
                result.add(status);
            }
        }
        return result;
    }

    /**
     * 收集当前状态的全部后继状态。
     * <p>
     * 挂起恢复类动作的目标状态由运行时决定（回到挂起前那一步），
     * 不是固定值，无法按它查规则，直接跳过。
     *
     * @param currentStatus 当前状态
     * @return 后继状态集合，保持动作表的原有顺序
     */
    private Set<String> collectTargetStatuses(String currentStatus) {
        Set<String> targets = new LinkedHashSet<>();
        for (FlowAction action : FlowRegistry.availableFrom(currentStatus)) {
            String toStatus = action.getToStatus();
            if (toStatus == null || toStatus.equals(currentStatus)) {
                continue;
            }
            if (FlowTarget.SUSPENDED_FROM.equals(toStatus) || FlowTarget.TARGET_STATUS.equals(toStatus)) {
                continue;
            }
            targets.add(toStatus);
        }
        return targets;
    }

    /**
     * 查某个到达状态下的附件规则。必填项排在前面，可选提示在后。
     *
     * @param ticketType   单据类型
     * @param targetStatus 到达状态
     * @param onlyRequired 是否只取强制项；流转拦截用 true，界面展示用 false
     * @return 规则列表
     */
    private List<ReqAttachRuleDO> findRules(String ticketType, String targetStatus, boolean onlyRequired) {
        return reqAttachRuleMapper.selectList(
                Wrappers.<ReqAttachRuleDO>lambdaQuery()
                        .eq(ReqAttachRuleDO::getEnabled, FLAG_YES)
                        .eq(onlyRequired, ReqAttachRuleDO::getRequired, FLAG_YES)
                        .and(wrapper -> wrapper
                                .eq(ReqAttachRuleDO::getStatus, targetStatus)
                                .or()
                                .eq(ReqAttachRuleDO::getStatus, WILDCARD))
                        .and(wrapper -> wrapper
                                .eq(ReqAttachRuleDO::getTicketType, ticketType)
                                .or()
                                .eq(ReqAttachRuleDO::getTicketType, WILDCARD))
                        .orderByDesc(ReqAttachRuleDO::getRequired)
                        .orderByAsc(ReqAttachRuleDO::getAttachType));
    }

    /**
     * 取可下载的附件。文件名与路径都从这里出，Controller 不碰数据库。
     *
     * @param attachmentId 附件 ID
     * @return 附件记录
     */
    public ReqAttachmentDO getForDownload(String attachmentId) {
        ReqAttachmentDO attachment = reqAttachmentMapper.selectById(attachmentId);
        if (attachment == null || (attachment.getDeleted() != null && attachment.getDeleted() == FLAG_YES)) {
            throw new BizException("附件不存在或已删除");
        }
        if (!attachmentStorage.exists(attachment.getRelativePath())) {
            // 库里有记录、盘上没文件，属于运维事故，必须说清是「丢失」而不是「不存在」
            throw new BizException("附件文件已丢失，请联系管理员从备份恢复：" + attachment.getOriginalName());
        }
        return attachment;
    }

    /**
     * 下载地址的绝对路径，供 Controller 拼响应。
     *
     * @param attachment 附件记录
     * @return 绝对路径
     */
    public Path resolvePhysicalPath(ReqAttachmentDO attachment) {
        return attachmentStorage.resolve(attachment.getRelativePath());
    }

    /**
     * 软删除附件。物理文件保留 —— 附件是审计证据，
     * 误删后管理员还能从目录里找回，硬删就真没了。
     *
     * @param attachmentId 附件 ID
     * @param userId       操作人
     */
    public void delete(String attachmentId, String userId) {
        ReqAttachmentDO attachment = getExisting(attachmentId);
        ReqTicketDO ticket = getTicket(attachment.getTicketId());
        checkModifiable(ticket);
        if (isConfirmed(attachment)) {
            throw new BizException("已标记为确认版的附件不能删除，请先取消确认");
        }
        if (!canModify(attachment, userId)) {
            throw new BizException("只能删除自己上传的附件", BizException.STATUS_FORBIDDEN);
        }

        ReqAttachmentDO update = new ReqAttachmentDO();
        update.setId(attachmentId);
        update.setDeleted(FLAG_YES);
        reqAttachmentMapper.updateById(update);
        log.info("附件软删除：ID={}，单号={}，操作人={}", attachmentId, ticket.getTicketNo(), userId);
    }

    /**
     * 标记为确认版。同单同类型只保留一个确认版。
     *
     * @param attachmentId 附件 ID
     * @param userId       操作人
     * @return 更新后的附件
     */
    @Transactional(rollbackFor = Exception.class)
    public AttachmentVO confirm(String attachmentId, String userId) {
        ReqAttachmentDO attachment = getExisting(attachmentId);
        ReqTicketDO ticket = getTicket(attachment.getTicketId());
        checkModifiable(ticket);
        if (!canModify(attachment, userId)) {
            throw new BizException("只能确认自己上传的附件", BizException.STATUS_FORBIDDEN);
        }

        ReqAttachmentDO clearOld = new ReqAttachmentDO();
        clearOld.setIsConfirmed(FLAG_NO);
        reqAttachmentMapper.update(clearOld,
                Wrappers.<ReqAttachmentDO>lambdaUpdate()
                        .eq(ReqAttachmentDO::getTicketId, attachment.getTicketId())
                        .eq(ReqAttachmentDO::getAttachType, attachment.getAttachType())
                        .eq(ReqAttachmentDO::getIsConfirmed, FLAG_YES)
                        .eq(ReqAttachmentDO::getDeleted, FLAG_NO));

        ReqAttachmentDO update = new ReqAttachmentDO();
        update.setId(attachmentId);
        update.setIsConfirmed(FLAG_YES);
        reqAttachmentMapper.updateById(update);

        attachment.setIsConfirmed(FLAG_YES);
        Map<String, String> typeLabels = loadDictLabels(DICT_ATTACH_TYPE);
        Map<String, String> statusLabels = loadDictLabels(DICT_TICKET_STATUS);
        Map<String, String> userNames = loadUserNames(List.of(attachment));
        return buildVO(attachment, typeLabels, statusLabels, userNames, userId, isAdmin(userId),
                ticket.getStatus());
    }

    /**
     * 取单据，不存在直接报错。
     *
     * @param ticketId 单据 ID
     * @return 单据
     */
    private ReqTicketDO getTicket(String ticketId) {
        ReqTicketDO ticket = reqTicketMapper.selectById(ticketId);
        if (ticket == null) {
            throw new BizException("单据不存在：" + ticketId);
        }
        return ticket;
    }

    /**
     * 取未删除的附件。
     *
     * @param attachmentId 附件 ID
     * @return 附件记录
     */
    private ReqAttachmentDO getExisting(String attachmentId) {
        ReqAttachmentDO attachment = reqAttachmentMapper.selectById(attachmentId);
        if (attachment == null || (attachment.getDeleted() != null && attachment.getDeleted() == FLAG_YES)) {
            throw new BizException("附件不存在或已删除");
        }
        return attachment;
    }

    /**
     * 关闭后的单据不允许改动附件。
     *
     * @param ticket 单据
     */
    private void checkModifiable(ReqTicketDO ticket) {
        if (TicketStatus.CLOSED.equals(ticket.getStatus())) {
            throw new BizException("单据已关闭，附件不可再修改");
        }
    }

    /**
     * 校验交付物类型必须是字典里的启用项。
     * <p>
     * 不校验会让前端随意传值，附件表里就会攒出 `""`、`"FDS "`、`"fds"` 这类脏码，
     * 必填拦截按类型计数时永远对不上。
     *
     * @param attachType 类型码
     * @return 去掉首尾空白后的类型码
     */
    private String checkAttachType(String attachType) {
        if (attachType == null || attachType.trim().isEmpty()) {
            throw new BizException("请选择交付物类型", BizException.STATUS_PARAM_ERROR);
        }
        String type = attachType.trim();
        Long count = sysDictDataMapper.selectCount(
                Wrappers.<SysDictDataDO>lambdaQuery()
                        .eq(SysDictDataDO::getDictType, DICT_ATTACH_TYPE)
                        .eq(SysDictDataDO::getDictValue, type)
                        .eq(SysDictDataDO::getStatus, "ACTIVE"));
        if (count == null || count == 0) {
            throw new BizException("交付物类型不存在或已停用：" + type);
        }
        return type;
    }

    /**
     * 校验文件本身：非空、不超限。
     *
     * @param file 上传文件
     */
    private void checkFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BizException("请选择要上传的文件", BizException.STATUS_PARAM_ERROR);
        }
        long maxBytes = attachmentStorage.getMaxSizeBytes();
        if (file.getSize() > maxBytes) {
            throw new BizException(String.format("文件大小超过上限 %d MB，请压缩后上传",
                    maxBytes / 1024 / 1024));
        }
    }

    /**
     * 查同单同类型同指纹的已有附件。
     *
     * @param ticketId   单据 ID
     * @param attachType 类型
     * @param sha256     指纹
     * @return 已存在的附件，没有则返回 null
     */
    private ReqAttachmentDO findSameFile(String ticketId, String attachType, String sha256) {
        return reqAttachmentMapper.selectOne(
                Wrappers.<ReqAttachmentDO>lambdaQuery()
                        .eq(ReqAttachmentDO::getTicketId, ticketId)
                        .eq(ReqAttachmentDO::getAttachType, attachType)
                        .eq(ReqAttachmentDO::getSha256, sha256)
                        .eq(ReqAttachmentDO::getDeleted, FLAG_NO)
                        .last("limit 1"));
    }

    /**
     * 取项目编码用于拼目录。单据上的 project_id 为空或项目已删除时落到 UNKNOWN。
     *
     * @param ticket 单据
     * @return 项目编码
     */
    private String resolveProjectCode(ReqTicketDO ticket) {
        if (ticket.getProjectId() == null || ticket.getProjectId().isEmpty()) {
            return UNKNOWN_PROJECT;
        }
        ReqProjectDO project = reqProjectMapper.selectById(ticket.getProjectId());
        if (project == null || project.getProjectCode() == null || project.getProjectCode().isEmpty()) {
            return UNKNOWN_PROJECT;
        }
        return project.getProjectCode();
    }

    /**
     * 统计某类型已上传的有效附件数。必填够不够全靠它。
     *
     * @param ticketId   单据 ID
     * @param attachType 类型
     * @return 数量
     */
    private int countUploaded(String ticketId, String attachType) {
        Long count = reqAttachmentMapper.selectCount(
                Wrappers.<ReqAttachmentDO>lambdaQuery()
                        .eq(ReqAttachmentDO::getTicketId, ticketId)
                        .eq(ReqAttachmentDO::getAttachType, attachType)
                        .eq(ReqAttachmentDO::getDeleted, FLAG_NO));
        return count == null ? 0 : count.intValue();
    }

    /**
     * 查询字典标签，type -> 中文名。
     *
     * @param dictType 字典类型
     * @return 值到标签的映射
     */
    private Map<String, String> loadDictLabels(String dictType) {
        List<SysDictDataDO> items = sysDictDataMapper.selectList(
                Wrappers.<SysDictDataDO>lambdaQuery()
                        .eq(SysDictDataDO::getDictType, dictType));
        Map<String, String> labels = new HashMap<>(items.size());
        for (SysDictDataDO item : items) {
            labels.put(item.getDictValue(), item.getDictLabel());
        }
        return labels;
    }

    /**
     * 批量查上传人姓名。逐条查会在附件多的单上打出一串 N+1 查询。
     *
     * @param attachments 附件列表
     * @return 用户 ID 到姓名的映射
     */
    private Map<String, String> loadUserNames(List<ReqAttachmentDO> attachments) {
        Set<String> userIds = new HashSet<>();
        for (ReqAttachmentDO attachment : attachments) {
            if (attachment.getUploaderId() != null) {
                userIds.add(attachment.getUploaderId());
            }
        }
        Map<String, String> names = new HashMap<>(userIds.size());
        if (userIds.isEmpty()) {
            return names;
        }
        List<SysUserDO> users = sysUserMapper.selectBatchIds(userIds);
        for (SysUserDO user : users) {
            names.put(user.getId(), user.getRealName());
        }
        return names;
    }

    /**
     * 判断是否管理员。管理员不受「只能改自己的」限制，否则人一走附件就没人能删。
     *
     * @param userId 用户 ID
     * @return 是管理员返回 true
     */
    private boolean isAdmin(String userId) {
        if (userId == null) {
            return false;
        }
        return userAccessService.listRoleCodes(userId).contains(FlowRoleSets.ADMIN);
    }

    /**
     * 能否改动该附件：本人上传，或管理员。
     *
     * @param attachment 附件
     * @param userId     用户
     * @return 可以改动返回 true
     */
    private boolean canModify(ReqAttachmentDO attachment, String userId) {
        boolean mine = userId != null && userId.equals(attachment.getUploaderId());
        return mine || isAdmin(userId);
    }

    /**
     * 是否确认版。
     *
     * @param attachment 附件
     * @return 确认版返回 true
     */
    private boolean isConfirmed(ReqAttachmentDO attachment) {
        return attachment.getIsConfirmed() != null && attachment.getIsConfirmed() == FLAG_YES;
    }

    /**
     * 组装上传结果。
     *
     * @param attachment 附件
     * @param userId     当前用户
     * @param duplicated 是否命中重复
     * @return 上传结果
     */
    private AttachmentUploadVO buildUploadResult(ReqAttachmentDO attachment, String userId, boolean duplicated) {
        Map<String, String> typeLabels = loadDictLabels(DICT_ATTACH_TYPE);
        Map<String, String> statusLabels = loadDictLabels(DICT_TICKET_STATUS);
        Map<String, String> userNames = loadUserNames(List.of(attachment));
        ReqTicketDO ticket = getTicket(attachment.getTicketId());

        AttachmentUploadVO result = new AttachmentUploadVO();
        result.setAttachment(buildVO(attachment, typeLabels, statusLabels, userNames, userId,
                isAdmin(userId), ticket.getStatus()));
        result.setDuplicated(duplicated);
        return result;
    }

    /**
     * 组装附件视图。当前状态用于判断是否允许删除 —— 已关闭的单据谁都删不了。
     *
     * @param attachment   附件
     * @param typeLabels   类型标签
     * @param statusLabels 状态标签
     * @param userNames    用户名
     * @param userId       当前用户
     * @param admin        是否管理员
     * @param ticketStatus 单据当前状态
     * @return 附件视图
     */
    private AttachmentVO buildVO(ReqAttachmentDO attachment, Map<String, String> typeLabels,
                                 Map<String, String> statusLabels, Map<String, String> userNames,
                                 String userId, boolean admin, String ticketStatus) {
        AttachmentVO vo = new AttachmentVO();
        vo.setId(attachment.getId());
        vo.setTicketId(attachment.getTicketId());
        vo.setStage(attachment.getStage());
        vo.setStageLabel(statusLabels.getOrDefault(attachment.getStage(), attachment.getStage()));
        vo.setAttachType(attachment.getAttachType());
        vo.setAttachTypeLabel(typeLabels.getOrDefault(attachment.getAttachType(), attachment.getAttachType()));
        vo.setOriginalName(attachment.getOriginalName());
        vo.setSize(attachment.getSize());
        vo.setSizeText(formatSize(attachment.getSize()));
        vo.setMimeType(attachment.getMimeType());
        vo.setUploaderId(attachment.getUploaderId());
        vo.setUploaderName(userNames.getOrDefault(attachment.getUploaderId(), ""));
        vo.setConfirmed(isConfirmed(attachment));
        vo.setFileMissing(!attachmentStorage.exists(attachment.getRelativePath()));
        vo.setTextExtracted(attachment.getExtractedText() != null
                && !attachment.getExtractedText().isEmpty());
        vo.setCreatedAt(attachment.getCreatedAt());
        vo.setDownloadUrl(DOWNLOAD_PREFIX + attachment.getId() + DOWNLOAD_SUFFIX);

        boolean closed = TicketStatus.CLOSED.equals(ticketStatus);
        boolean mine = userId != null && userId.equals(attachment.getUploaderId());
        vo.setCanDelete(!closed && !isConfirmed(attachment) && (mine || admin));
        return vo;
    }

    /**
     * 字节数转可读文本。
     *
     * @param size 字节数
     * @return 如 1.2 MB
     */
    private String formatSize(Long size) {
        if (size == null || size <= 0) {
            return "0 B";
        }
        if (size < BYTES_PER_KB) {
            return size + " B";
        }
        if (size < BYTES_PER_MB) {
            return String.format(Locale.ROOT, "%.1f KB", size / BYTES_PER_KB);
        }
        return String.format(Locale.ROOT, "%.1f MB", size / BYTES_PER_MB);
    }

    /**
     * 当前时间字符串，格式与全库一致。
     *
     * @return yyyy-MM-dd HH:mm:ss
     */
    private String now() {
        return LocalDateTime.now().format(TIME_FORMATTER);
    }
}
