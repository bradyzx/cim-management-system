/**
 * 单据业务 API。
 *
 * 契约以 11-业务接口契约.md 为准。三条约定：
 * 1. 按钮可用性一律看 `/actions`，前端不自己判角色 —— 判两遍必然和真实规则对不上；
 * 2. 所有 `xxxLabel` 由后端翻译好，前端不再缓存字典；
 * 3. 下载必须走原生 fetch 拿 blob，走 requestClient 会被 JSON 拦截器当报文解析。
 */
import { useAccessStore } from '@vben/stores';

import { requestClient } from '#/api/request';

/** 统一响应里的阻塞项，与后端 Blocker 一一对应 */
export interface TicketBlocker {
  /** 机器可读的阻塞类型，如 ATTACH_REQUIRED / FIELD_REQUIRED */
  bizCode: string;
  /** 附加数据，附件缺失时带 attachType / minCount / uploaded */
  details?: null | Record<string, unknown>;
  /** 展示给人看的原因 */
  message: string;
}

/** 当前用户在某张单上可执行的动作 */
export interface TicketActionItem {
  action: string;
  /** 按钮权限码，仅作留痕；前端不用它做判断 */
  authCode?: null | string;
  /** 不可点时一定带 blockers，且可能多条 */
  blockers: TicketBlocker[];
  enabled: boolean;
  label: string;
  /** 退回点码，非退回类动作为 null；前端据此决定是否必须选原因 */
  returnPoint?: null | string;
}

/** 动作执行结果 */
export interface TicketActionResult {
  action: string;
  /** 本次是否触发了「同一退回点第 3 次转评审」 */
  escalated: boolean;
  fromStatus: string;
  /** 后端生成的提示语（并行确认时告知还差哪几方），空则前端自行兜底 */
  hint?: null | string;
  returnSeq?: null | number;
  slaDueAt?: null | string;
  statusLabel: string;
  ticketId: string;
  toStatus: string;
}

/** 时间线一条 */
export interface TicketTimelineItem {
  action: string;
  actionLabel: string;
  createdAt: string;
  fromStatus?: null | string;
  fromStatusLabel?: null | string;
  id: string;
  operatorName?: null | string;
  reasonCode?: null | string;
  reasonLabel?: null | string;
  remark?: null | string;
  returnSeq?: null | number;
  toStatus: string;
  toStatusLabel: string;
}

/** 单据详情，含完整时间线 */
export interface TicketDetail {
  acceptanceCriteria?: null | string;
  acceptanceLocked?: null | number;
  actualHours?: null | number;
  assigneeName?: null | string;
  assignedAt?: null | string;
  branchNameSuggest?: null | string;
  closeReason?: null | string;
  closeReasonLabel?: null | string;
  closedAt?: null | string;
  contentAsis?: null | string;
  contentTobe?: null | string;
  createdAt?: null | string;
  creatorName?: null | string;
  // v0.7 并行化：三方确认与设计评审的各方通过标志
  dsgnItPassed?: null | number;
  dsgnPlatformPassed?: null | number;
  dsgnUserPassed?: null | number;
  deptName?: null | string;
  description?: null | string;
  designAt?: null | string;
  devAt?: null | string;
  estimatedHours?: null | number;
  evalComment?: null | string;
  evalResult?: null | string;
  evaluatedAt?: null | string;
  expectDueDate?: null | string;
  expectResumeAt?: null | string;
  id: string;
  mitPassed?: null | number;
  moduleCode?: null | string;
  monitorNote?: null | string;
  ownerName?: null | string;
  platformPassed?: null | number;
  superiorPassed?: null | number;
  priority?: null | string;
  priorityCssClass?: null | string;
  priorityLabel?: null | string;
  projectName?: null | string;
  proposeDate?: null | string;
  releaseAt?: null | string;
  reporterName?: null | string;
  returnCount?: null | number;
  roiDesc?: null | string;
  roiValue?: null | number;
  satisfactionScore?: null | number;
  slaDueAt?: null | string;
  slaOverdueHours?: null | number;
  status: string;
  statusCssClass?: null | string;
  statusLabel: string;
  submittedAt?: null | string;
  suspendReason?: null | string;
  suspendedFrom?: null | string;
  suspendedFromLabel?: null | string;
  systemCode?: null | string;
  systemName?: null | string;
  ticketNo: string;
  ticketType?: null | string;
  ticketTypeLabel?: null | string;
  timeline: TicketTimelineItem[];
  title?: null | string;
  uatAt?: null | string;
  updatedAt?: null | string;
}

/** 附件 */
export interface TicketAttachment {
  attachType: string;
  attachTypeLabel: string;
  canDelete: boolean;
  confirmed: boolean;
  createdAt: string;
  downloadUrl: string;
  fileMissing: boolean;
  id: string;
  mimeType?: null | string;
  originalName: string;
  size?: null | number;
  sizeText: string;
  stage: string;
  stageLabel: string;
  textExtracted: boolean;
  ticketId: string;
  uploaderId?: null | string;
  uploaderName?: null | string;
}

/** 上传结果 */
export interface TicketAttachmentUploadResult {
  attachment: TicketAttachment;
  /** 同单同类型同指纹已存在时为 true，此时返回的是已有附件 */
  duplicated: boolean;
}

/** 当前状态往某个后继状态去，需要哪些附件 */
export interface TicketAttachRuleStatus {
  attachType: string;
  attachTypeLabel: string;
  minCount: number;
  required: boolean;
  satisfied: boolean;
  targetStatus: string;
  targetStatusLabel: string;
  uploaded: number;
}

/** 动作请求 */
export interface TicketActionParams {
  action: string;
  /** 动作附加字段，如 assigneeId / estimatedHours / monitorNote */
  extra?: Record<string, unknown>;
  /** 乐观并发：带上点按钮时看到的状态，变化则拒绝 */
  expectStatus?: string;
  reasonCode?: string;
  remark?: string;
}

/** 单据查询参数：后端统一入口拼数据范围，前端只传业务条件（03 5.2.5） */
export interface TicketListParams {
  assigneeId?: string;
  endDate?: string;
  keyword?: string;
  page?: number;
  pageSize?: number;
  priority?: string;
  projectId?: string;
  startDate?: string;
  status?: string;
  systemId?: string;
  systemCode?: string;
  ticketType?: string;
}

/** 列表行 */
export interface TicketRow {
  assigneeName?: null | string;
  createdAt?: null | string;
  deptName?: null | string;
  expectDueDate?: null | string;
  id: string;
  priority?: null | string;
  priorityCssClass?: null | string;
  priorityLabel?: null | string;
  projectName?: null | string;
  reporterName?: null | string;
  returnCount?: null | number;
  slaDueAt?: null | string;
  slaOverdueHours?: null | number;
  status: string;
  statusCssClass?: null | string;
  statusLabel: string;
  systemCode?: null | string;
  ticketNo: string;
  ticketType?: null | string;
  ticketTypeLabel?: null | string;
  title?: null | string;
}

/**
 * 单据详情（含时间线）。
 *
 * @param id 单据 ID
 */
export async function getTicketDetailApi(id: string) {
  return requestClient.get<TicketDetail>(`/ticket/${id}/detail`);
}

/**
 * 当前用户可执行的动作。前端按钮的唯一来源。
 *
 * @param id 单据 ID
 */
export async function listTicketActionsApi(id: string) {
  return requestClient.get<TicketActionItem[]>(`/ticket/${id}/actions`);
}

/**
 * 执行一个流转动作。
 *
 * @param id 单据 ID
 * @param data 动作码与参数
 */
export async function submitTicketActionApi(
  id: string,
  data: TicketActionParams,
) {
  return requestClient.post<TicketActionResult>(`/ticket/${id}/action`, data);
}

/**
 * 单据全部附件，按上传时间升序。
 *
 * @param id 单据 ID
 */
export async function listTicketAttachmentsApi(id: string) {
  return requestClient.get<TicketAttachment[]>(`/ticket/${id}/attachments`);
}

/**
 * 当前环节的附件缺口，按目标状态分组。
 *
 * @param id 单据 ID
 */
export async function listTicketAttachRulesApi(id: string) {
  return requestClient.get<TicketAttachRuleStatus[]>(
    `/ticket/${id}/attach-rules`,
  );
}

/**
 * 上传附件。不传 stage，后端取单据当前状态。
 *
 * @param ticketId 单据 ID
 * @param attachType 交付物类型码
 * @param file 文件
 */
export async function uploadTicketAttachmentApi(
  ticketId: string,
  attachType: string,
  file: File,
) {
  const formData = new FormData();
  formData.append('attachType', attachType);
  formData.append('file', file);
  // RequestClient 实例默认头是 application/json，axios 1.19 浏览器端不会为
  // FormData 自动覆盖它，请求会带着 JSON 头出去，Spring 就不按 multipart 解析，
  // attachType / file 全部取不到（报「缺少必填参数：attachType」）。
  // 必须在请求级显式指回 multipart；boundary 由浏览器补，不能手写。
  return requestClient.post<TicketAttachmentUploadResult>(
    `/ticket/${ticketId}/attachment`,
    formData,
    {
      headers: {
        'Content-Type': 'multipart/form-data',
      },
    },
  );
}

/**
 * 软删除附件。
 *
 * @param attachmentId 附件 ID
 */
export async function deleteTicketAttachmentApi(attachmentId: string) {
  return requestClient.delete(`/ticket/attachment/${attachmentId}`);
}

/**
 * 标记为确认版，同单同类型只保留一个。
 *
 * @param attachmentId 附件 ID
 */
export async function confirmTicketAttachmentApi(attachmentId: string) {
  return requestClient.post<TicketAttachment>(
    `/ticket/attachment/${attachmentId}/confirm`,
  );
}

/**
 * 下载附件。
 *
 * 用原生 fetch 而不是 requestClient：后者会按 JSON 解析响应，
 * 二进制流走那里必然报错。这里拿 blob 再触发浏览器下载。
 *
 * @param attachmentId 附件 ID
 * @param fileName 落盘文件名
 */
export async function downloadTicketAttachmentApi(
  attachmentId: string,
  fileName: string,
) {
  const accessStore = useAccessStore();
  const baseUrl = import.meta.env.VITE_GLOB_API_URL;
  const response = await fetch(
    `${baseUrl}/ticket/attachment/${attachmentId}/download`,
    {
      headers: accessStore.accessToken
        ? { Authorization: `Bearer ${accessStore.accessToken}` }
        : {},
    },
  );
  if (!response.ok) {
    throw new Error(`附件下载失败（HTTP ${response.status}）`);
  }
  const blob = await response.blob();
  const objectUrl = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = objectUrl;
  link.download = fileName;
  document.body.append(link);
  link.click();
  link.remove();
  URL.revokeObjectURL(objectUrl);
}

/**
 * 单据列表。列表页尚未实现后端，接口先行占位。
 *
 * @param params 查询条件
 */
export async function getTicketListApi(params: TicketListParams) {
  return requestClient.get<{ items: TicketRow[]; total: number }>('/ticket/list', {
    params,
  });
}

/** 基础数据下拉项：项目或系统 */
export interface BasicOption {
  code: string;
  id: string;
  name: string;
  /** 仅系统选项有，联动筛选用 */
  projectId?: string;
}

/** 新建单据表单 */
export interface TicketCreateParams {
  acceptanceCriteria?: string;
  contentAsis: string;
  contentTobe: string;
  description: string;
  dupOverride?: number;
  expectDueDate?: string;
  moduleCode?: string;
  overrideReason?: string;
  priority: string;
  projectId: string;
  roiDesc?: string;
  systemId: string;
  ticketType: string;
  title: string;
}

/** 查重命中的相似需求（随 DUP_FOUND 的 details 返回） */
export interface SimilarTicket {
  score: number;
  systemCode?: null | string;
  ticketId: string;
  ticketNo: string;
  title?: null | string;
}

/** 建单结果 */
export interface TicketCreatedResult {
  id: string;
  maxSimilarity?: number;
  status: string;
  statusLabel: string;
  ticketNo: string;
}

/**
 * 项目下拉（建单第一级）。
 */
export async function listProjectOptionsApi() {
  return requestClient.get<BasicOption[]>('/basic/projects');
}

/**
 * 某项目下的系统下拉（建单第二级，联动）。
 *
 * @param projectId 项目 ID
 */
export async function listSystemOptionsApi(projectId: string) {
  return requestClient.get<BasicOption[]>('/basic/systems', {
    params: { projectId },
  });
}

/**
 * 新建单据。
 *
 * 查重命中时后端拒绝并返回 bizCode=DUP_FOUND + 相似列表，
 * 由用户看完列表决定是否带 dupOverride 重提。
 *
 * @param data 表单
 */
export async function createTicketApi(data: TicketCreateParams) {
  return requestClient.post<TicketCreatedResult>('/ticket', data);
}
