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
  /** 动作前状态的经办人，即本条的操作人 */
  fromOwner?: null | string;
  id: string;
  operatorName?: null | string;
  reasonCode?: null | string;
  reasonLabel?: null | string;
  remark?: null | string;
  returnSeq?: null | number;
  toStatus: string;
  toStatusLabel: string;
  /** 动作后状态的经办人，即下一条的操作人；刚进入、还没人接时为空 */
  toOwner?: null | string;
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

/**
 * 步骤条的一个节点，鼠标悬停时展示。
 *
 * 人是具体的人名（「张三」）而不是岗位：图上要能直接看出压在谁手上。
 * 只给姓名不带账号 —— 卡片是给人看的，账号是登录用的，摆在卡片上只是噪声。
 * `handlers` 是已经发生的事实，`plannedHandlers` 是按角色推算的预计接手人 ——
 * 前者为空时前端才说「预计」，两者别混着显示。
 */
export interface TicketFlowStep {
  /** 步骤键，如 PROPOSE */
  key: string;
  /** 步骤名，如「提出需求」 */
  label: string;
  /** 该步覆盖的流程节点编号，如 ["N01","N02"] */
  nodeCodes: string[];
  /** 进度：DONE 已走过 / CURRENT 停在这里 / TODO 还没到 */
  state: 'CURRENT' | 'DONE' | 'TODO';
  /** 实际处理过的人，系统动作记「系统」 */
  handlers: string[];
  /** 预计处理人，还没走到的步骤只有这一项 */
  plannedHandlers: string[];
  /** 流程上这一步要交的材料 */
  need: string[];
  /** 已上传的附件，「文件名（类型）」 */
  uploaded: string[];
  /** 必填但还没交的材料类型 */
  missing: string[];
  /** 做完交给谁 */
  handoff: string[];
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

/** 第 5 节功能清单的一行。code 由系统按 F-01 起连续生成，一旦发布不改 */
export interface TicketDocFunction {
  code: string;
  priority?: string;
  text: string;
  type?: string;
}

/** 第 6 节非功能要求的一行 */
export interface TicketDocNfr {
  category: string;
  text: string;
}

/** 第 10 节验收标准的一条。seq 是覆盖率分母的键，必须从 1 连续 */
export interface TicketDocAcceptance {
  seq: number;
  text: string;
}

/**
 * 需求文档 12 节结构化数据。
 *
 * 键名由契约 11 §8.1.1 定稿，前端按 key 直接取，改名会让卡片变空白且不报错。
 */
export interface TicketDocSections {
  s1_name: { content: string };
  s2_asis: { content: string };
  s3_pain: { content: string; metrics: { name: string; value: string }[] };
  s4_tobe: { content: string };
  s5_functions: { items: TicketDocFunction[] };
  s6_nfr: { items: TicketDocNfr[] };
  s7_scope: {
    depts: { deptCode: string; impact: string }[];
    systems: { impact: string; systemCode: string }[];
  };
  s8_priority: {
    level: string;
    reason: string;
    scores: { compliance: number; impact: number; urgency: number };
  };
  s9_effort: {
    aiEstimated?: number;
    confirmedBy?: null | string;
    maxDays: null | number;
    minDays: null | number;
    suggestReleaseDate?: null | string;
  };
  s10_acceptance: { items: TicketDocAcceptance[] };
  s11_attachments: { items: { attachType: string; name: string }[] };
  s12_ai_review: {
    completeness: number;
    dupScore: number;
    risks: string[];
    unclarified: string[];
    verdict: string;
  };
}

/** 需求文档 */
export interface TicketDoc {
  aiDegraded?: null | number;
  aiModel?: null | string;
  confirmedAt?: null | string;
  confirmedBy?: null | string;
  confirmedByName?: null | string;
  /** Markdown 正文，后端从 docJson 现算，与结构化数据始终一致 */
  contentMd: string;
  /** 12 节结构化数据；结构损坏时可能是空对象，取 sections 要判空 */
  docJson: { schemaVersion?: number; sections?: Partial<TicketDocSections> };
  /** 当前可否编辑：仅提出人、仅「需求文档待确认」环节 */
  editable: boolean;
  id: string;
  /** 现在点确认会挡住哪些节，空数组表示能过 */
  missingMessages: string[];
  missingSections: string[];
  ticketId: string;
  ticketStatus: string;
  version: number;
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
 * 我的待办一行：单据摘要 + 「轮到我做什么」。
 *
 * 字段比列表行多出待办部分。判定由后端按与 `/actions` 同一套规则算，
 * 前端不自己推「该不该我处理」——推两遍必然和真实规则对不上。
 */
export interface TicketTodoRow extends TicketRow {
  /** 现在就能点的动作码 */
  blockedActions: string[];
  /** 上述动作的中文名 */
  blockedActionLabels: string[];
  /** 被挡住的原因，一条一句，可直接展示 */
  blockedReasons: string[];
  /** 现在就能点的动作码 */
  dueActions: string[];
  /** 现在就能点的动作中文名 */
  dueActionLabels: string[];
  /** 当前环节是否已过应完成时间 */
  slaOverdue?: null | boolean;
  /** 一句「待你做什么」 */
  todoHint?: null | string;
  /**
   * 待办性质：DUE 现在就能办，BLOCKED 需先补材料。
   *
   * 后端定，前端不拿 dueActions 是否为空去推 —— 一张卡在缺附件上的单，
   * 它的「退回重生成」也是可点的，用动作数判断会算成「能办」。
   */
  todoType?: null | string;
  /** 停留在当前环节的小时数，排序依据 */
  waitingHours?: null | number;
  /** 停留时长的可读形式，如「3 天 2 小时」 */
  waitingText?: null | string;
}

/**
 * 我的待办，按停留时长倒序。后端不分页，返回全量候选。
 *
 * @param ticketType 单据类型，不传表示全部
 */
export async function getTicketTodoApi(ticketType?: string) {
  return requestClient.get<TicketTodoRow[]>('/ticket/todo', {
    params: ticketType ? { ticketType } : {},
  });
}

/** 一条测试用例 */
export interface TestCaseItem {
  bugId?: null | string;
  caseNo: string;
  /** 对应第几条验收标准，覆盖率的分母依据 */
  criteriaIndex?: null | number;
  execStatus: string;
  execStatusLabel: string;
  expected?: null | string;
  /** 1 = 重点关注 */
  focusFlag?: null | number;
  /** 为什么重点关注。只标红不说原因等于没标 */
  focusReason?: null | string;
  id: string;
  origin?: null | string;
  precond?: null | string;
  priority?: null | string;
  roundNo?: null | number;
  sourceCriteria?: null | string;
  /** 步骤，后端已从 JSON 解析成数组 */
  steps: string[];
  title: string;
  updatedAt?: null | string;
}

/** 未覆盖的验收标准 */
export interface CaseCoverageMissing {
  content: string;
  seq: number;
}

/** 覆盖率 */
export interface CaseCoverage {
  /** 已覆盖 */
  covered: number;
  /** 验收标准总数 = 分母 */
  criteriaTotal: number;
  /** 是否已全覆盖 */
  full: boolean;
  missing: CaseCoverageMissing[];
  /** 覆盖率 0~1 */
  rate: number;
  /** 1 = 已豁免 */
  uatWaiver?: null | number;
  waiverReason?: null | string;
}

/** 某一轮的用例 + 覆盖率 */
export interface CaseList {
  coverage: CaseCoverage;
  executedCount: number;
  failedCount: number;
  items: TestCaseItem[];
  passedCount: number;
  /** 当前轮次，未生成过为 null */
  roundNo?: null | number;
  /** 已生成过哪些轮次，倒序 */
  rounds: number[];
}

/** 生成结果 */
export interface CaseGenerateResult {
  coverage: CaseCoverage;
  generated: number;
  roundNo: number;
  skipped: number;
  total: number;
  /** 超量等提示，无异常为 null */
  warning?: null | string;
}

/** 用例编辑参数。编号、来源标准、批次不可改 */
export interface CaseSaveParams {
  expected?: string;
  focusFlag?: number;
  focusReason?: string;
  precond?: string;
  priority?: string;
  steps?: string[];
  title?: string;
}

/** 执行登记参数 */
export interface CaseExecParams {
  actualResult?: string;
  bugId?: string;
  /** PASS / FAIL / SKIP */
  execStatus: string;
  remark?: string;
}

/**
 * 某一轮的测试用例 + 覆盖率。不传轮次取最新一轮。
 *
 * @param ticketId 单据 ID
 * @param roundNo 轮次，可空
 */
export async function getTicketCasesApi(ticketId: string, roundNo?: number) {
  return requestClient.get<CaseList>(`/ticket/${ticketId}/cases`, {
    params: roundNo ? { roundNo } : {},
  });
}

/**
 * 按验收标准生成用例。验收标准未锁定（设计评审未全过）时会被拒。
 *
 * @param ticketId 单据 ID
 * @param roundNo 指定轮次，不传则自增
 */
export async function generateTicketCasesApi(ticketId: string, roundNo?: number) {
  return requestClient.post<CaseGenerateResult>(
    `/ticket/${ticketId}/cases/generate`,
    roundNo ? { roundNo } : {},
  );
}

/**
 * 覆盖率。UAT 通过时后端会用同一份逻辑再算，前端传什么都不算数。
 *
 * @param ticketId 单据 ID
 */
export async function getTicketCoverageApi(ticketId: string) {
  return requestClient.get<CaseCoverage>(`/ticket/${ticketId}/coverage`);
}

/**
 * 编辑用例。改过之后后端会把来源标成 HUMAN。
 *
 * @param caseId 用例 ID
 * @param data 可编辑字段
 */
export async function saveTestCaseApi(caseId: string, data: CaseSaveParams) {
  return requestClient.put<TestCaseItem>(`/cases/${caseId}`, data);
}

/**
 * 执行登记。每次执行追加一条记录，不覆盖历史。
 *
 * @param caseId 用例 ID
 * @param data 执行结果
 */
export async function execTestCaseApi(caseId: string, data: CaseExecParams) {
  return requestClient.post<TestCaseItem>(`/cases/${caseId}/exec`, data);
}

/** 一条点检项 */
export interface CheckItem {
  /** PASS / FAIL / NA，未点检为 null */
  checkResult?: null | string;
  checkResultLabel?: null | string;
  checkRemark?: null | string;
  checkedAt?: null | string;
  checkedByName?: null | string;
  expectResult?: null | string;
  id: string;
  /** 怎么验 */
  itemDesc?: null | string;
  itemName: string;
  /** 1 = 重点功能，必须点检 */
  isKey?: null | number;
  sortNo?: null | number;
  source?: null | string;
  sourceLabel?: null | string;
}

/** 点检清单 */
export interface CheckItemList {
  /** 已点检条数（含非重点） */
  checked: number;
  /** 1 = 未配置清单。此时允许直接点检通过 */
  empty: number;
  /** 已点检的重点项 */
  keyChecked: number;
  /** 重点项总数 */
  keyTotal: number;
  items: CheckItem[];
  total: number;
}

/**
 * 点检清单。`empty=1` 时页面应允许直接点检通过。
 *
 * @param ticketId 单据 ID
 */
export async function getCheckItemsApi(ticketId: string) {
  return requestClient.get<CheckItemList>(`/ticket/${ticketId}/check-items`);
}

/**
 * 从需求文档第 5 节功能清单生成点检项草稿。已有点检项时会被拒。
 *
 * @param ticketId 单据 ID
 */
export async function generateCheckItemsApi(ticketId: string) {
  return requestClient.post<CheckItemList>(
    `/ticket/${ticketId}/check-items/from-doc`,
  );
}

/**
 * 单项登记。
 *
 * @param itemId 点检项 ID
 * @param data 结果与说明
 */
export async function saveCheckItemApi(
  itemId: string,
  data: { checkRemark?: string; checkResult: string },
) {
  return requestClient.put<CheckItemList>(`/check-items/${itemId}`, data);
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
 * 步骤条数据：十步各自的处理人、材料三态、接手人。
 *
 * 单独一个接口：详情首屏不需要它，只有鼠标移到步骤条上才看。
 * 一次返回全部十步，悬停切换时不再发请求。
 *
 * @param id 单据 ID
 */
export async function getTicketFlowStepsApi(id: string) {
  return requestClient.get<TicketFlowStep[]>(`/ticket/${id}/flow-steps`);
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
 * 需求文档（12 节结构化 + 渲染好的 Markdown）。
 *
 * 返回里的 `editable` 与 `missingSections` 是后端算的，前端直接用，
 * 不重复判断状态机与校验规则。
 *
 * @param id 单据 ID
 */
export async function getTicketDocApi(id: string) {
  return requestClient.get<TicketDoc>(`/ticket/${id}/doc`);
}

/**
 * 保存人工编辑后的需求文档。
 *
 * 只传结构化数据，Markdown 正文由后端现算 —— 两边都维护必然漂移。
 *
 * @param id 单据 ID
 * @param docJson 12 节结构化数据
 */
export async function saveTicketDocApi(
  id: string,
  docJson: TicketDoc['docJson'],
) {
  return requestClient.put<TicketDoc>(`/ticket/${id}/doc`, { docJson });
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
