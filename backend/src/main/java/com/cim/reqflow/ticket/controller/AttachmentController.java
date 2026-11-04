package com.cim.reqflow.ticket.controller;

import com.cim.reqflow.common.R;
import com.cim.reqflow.common.constant.CommonConstants;
import com.cim.reqflow.ticket.entity.ReqAttachmentDO;
import com.cim.reqflow.ticket.service.AttachmentService;
import com.cim.reqflow.ticket.vo.AttachRuleStatusVO;
import com.cim.reqflow.ticket.vo.AttachmentUploadVO;
import com.cim.reqflow.ticket.vo.AttachmentVO;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

/**
 * 附件接口。
 * <p>
 * 上传接口刻意不接收 stage 与 uploadedBy：stage 取单据当前状态、uploadedBy 取登录用户，
 * 都由后端决定。允许前端传，就等于允许伪造归档节点和冒名上传。
 * <p>
 * 下载接口返回文件流而不是统一响应体，前端必须用 blob 方式请求
 * （见 11 第五章「导出与下载」的说明），否则会被 JSON 解析器当成报文。
 *
 * @author Haijie Xiao
 * @date 2026-11-04
 */
@RestController
@RequestMapping("/ticket")
public class AttachmentController {

    /** 附件服务 */
    private final AttachmentService attachmentService;

    public AttachmentController(AttachmentService attachmentService) {
        this.attachmentService = attachmentService;
    }

    /**
     * 上传附件。
     *
     * @param ticketId   单据 ID
     * @param attachType 交付物类型码，必须是字典 attach_type 的启用项
     * @param file       文件
     * @param request    HTTP 请求，用于取当前登录用户
     * @return 上传结果，命中重复时 duplicated = true
     */
    @PostMapping("/{ticketId}/attachment")
    public R<AttachmentUploadVO> upload(@PathVariable String ticketId,
                                        @RequestParam String attachType,
                                        @RequestParam MultipartFile file,
                                        HttpServletRequest request) {
        String userId = currentUserId(request);
        return R.ok(attachmentService.upload(ticketId, attachType, file, userId));
    }

    /**
     * 列出单据下的全部附件。
     *
     * @param ticketId 单据 ID
     * @param request  HTTP 请求，用于取当前登录用户
     * @return 附件列表
     */
    @GetMapping("/{ticketId}/attachments")
    public R<List<AttachmentVO>> list(@PathVariable String ticketId,
                                      HttpServletRequest request) {
        String userId = currentUserId(request);
        return R.ok(attachmentService.list(ticketId, userId));
    }

    /**
     * 当前环节的附件必填缺口。供详情页显示「还差什么」。
     *
     * @param ticketId 单据 ID
     * @return 规则满足情况
     */
    @GetMapping("/{ticketId}/attach-rules")
    public R<List<AttachRuleStatusVO>> listRuleStatus(@PathVariable String ticketId) {
        return R.ok(attachmentService.listRuleStatus(ticketId));
    }

    /**
     * 下载附件。用原始文件名，浏览器直接落盘。
     *
     * @param attachmentId 附件 ID
     * @return 文件流
     */
    @GetMapping("/attachment/{attachmentId}/download")
    public ResponseEntity<Resource> download(@PathVariable String attachmentId) {
        ReqAttachmentDO attachment = attachmentService.getForDownload(attachmentId);
        Path path = attachmentService.resolvePhysicalPath(attachment);

        MediaType mediaType = attachment.getMimeType() == null
                ? MediaType.APPLICATION_OCTET_STREAM
                : MediaType.parseMediaType(attachment.getMimeType());

        // 中文文件名必须同时给 filename 与 filename*：老浏览器只认前者且会乱码，
        // 现代浏览器优先用后者，两个都给才能在所有环境下正确落盘
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(attachment.getOriginalName(), StandardCharsets.UTF_8)
                .build();

        // 不显式设 Content-Length：交给 FileSystemResource 按实际文件长度决定。
        // 库里 size 若因历史数据为 null（或与磁盘不一致），写死 0 会把响应截成空文件，
        // 而且这种错只在下载时才暴露。
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentType(mediaType)
                .body(new FileSystemResource(path));
    }

    /**
     * 软删除附件。
     *
     * @param attachmentId 附件 ID
     * @param request      HTTP 请求，用于取当前登录用户
     * @return 空响应
     */
    @DeleteMapping("/attachment/{attachmentId}")
    public R<Void> delete(@PathVariable String attachmentId, HttpServletRequest request) {
        attachmentService.delete(attachmentId, currentUserId(request));
        return R.ok(null);
    }

    /**
     * 标记为确认版。同单同类型只保留一个。
     *
     * @param attachmentId 附件 ID
     * @param request      HTTP 请求，用于取当前登录用户
     * @return 更新后的附件
     */
    @PostMapping("/attachment/{attachmentId}/confirm")
    public R<AttachmentVO> confirm(@PathVariable String attachmentId, HttpServletRequest request) {
        return R.ok(attachmentService.confirm(attachmentId, currentUserId(request)));
    }

    /**
     * 从请求域取当前登录用户 ID。由 JwtInterceptor 写入，进到这里一定非空。
     *
     * @param request HTTP 请求
     * @return 用户 ID
     */
    private String currentUserId(HttpServletRequest request) {
        return (String) request.getAttribute(CommonConstants.ATTR_CURRENT_USER_ID);
    }
}
