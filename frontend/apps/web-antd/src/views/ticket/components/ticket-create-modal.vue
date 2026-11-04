<script lang="ts" setup>
/**
 * 新建需求弹窗。
 *
 * 层级归属在这里落死：**先选项目，系统下拉只出该项目下的系统**
 * （一个项目 → 多个系统 → 多个需求）。没选项目时系统禁用，
 * 后端还会再校验一次「系统属于项目」——前端拦的是体验，后端拦的是数据。
 *
 * 查重交互：提交命中（bizCode=DUP_FOUND）时**不关弹窗**，
 * 在表单下方展示相似需求列表，用户看完列表勾「仍要新建」并填原因后重提。
 * 报错信息由统一拦截器弹出，这里只处理需要继续交互的 DUP_FOUND。
 */
import type { Dayjs } from 'dayjs';
import type {
  BasicOption,
  SimilarTicket,
  TicketCreateParams,
} from '#/api/ticket';

import { computed, reactive, ref } from 'vue';

import {
  Checkbox,
  DatePicker,
  Form,
  FormItem,
  Input,
  Modal,
  Select,
  Textarea,
  message,
} from 'ant-design-vue';

import { requestClient } from '#/api/request';
import {
  createTicketApi,
  listProjectOptionsApi,
  listSystemOptionsApi,
} from '#/api/ticket';

const emit = defineEmits<{ created: [ticketId: string] }>();

const 可见 = ref(false);
const 提交中 = ref(false);

const 项目选项 = ref<BasicOption[]>([]);
const 系统选项 = ref<BasicOption[]>([]);
const 类型选项 = ref<{ label: string; value: string }[]>([]);
const 优先级选项 = ref<{ label: string; value: string }[]>([]);

/** 查重命中后的相似需求列表 */
const 相似列表 = ref<SimilarTicket[]>([]);
/** 用户确认与相似需求不重复，仍要新建 */
const 仍要新建 = ref(false);

const 表单 = reactive({
  ticketType: 'REQ',
  projectId: undefined as string | undefined,
  systemId: undefined as string | undefined,
  moduleCode: '',
  title: '',
  priority: 'P2',
  description: '',
  contentAsis: '',
  contentTobe: '',
  日期: undefined as Dayjs | undefined,
  acceptanceCriteria: '',
  roiDesc: '',
});

const 未选项目 = computed(() => !表单.projectId);

/** 打开弹窗并加载数据。每次打开重置表单与查重状态 */
async function 打开() {
  可见.value = true;
  相似列表.value = [];
  仍要新建.value = false;
  Object.assign(表单, {
    ticketType: 'REQ',
    projectId: undefined,
    systemId: undefined,
    moduleCode: '',
    title: '',
    priority: 'P2',
    description: '',
    contentAsis: '',
    contentTobe: '',
    日期: undefined,
    acceptanceCriteria: '',
    roiDesc: '',
  });
  if (类型选项.value.length === 0) {
    const [项目, 类型, 优先级] = await Promise.all([
      listProjectOptionsApi(),
      requestClient.get<{ label: string; value: string }[]>('/dict/data', {
        params: { code: 'ticket_type' },
      }),
      requestClient.get<{ label: string; value: string }[]>('/dict/data', {
        params: { code: 'ticket_priority' },
      }),
    ]);
    项目选项.value = 项目 ?? [];
    类型选项.value = 类型 ?? [];
    优先级选项.value = 优先级 ?? [];
  }
}

/** 项目变更：重置系统并拉该项目下的系统（两级联动的第二级） */
async function 项目变更() {
  表单.systemId = undefined;
  系统选项.value = [];
  if (!表单.projectId) {
    return;
  }
  系统选项.value = await listSystemOptionsApi(表单.projectId);
}

/**
 * 提交。查重命中时把相似列表展示出来等用户决定，弹窗不关。
 */
async function 提交() {
  const 缺失: string[] = [];
  if (!表单.title.trim()) 缺失.push('标题');
  if (!表单.projectId) 缺失.push('所属项目');
  if (!表单.systemId) 缺失.push('所属系统');
  if (!表单.description.trim()) 缺失.push('需求简述');
  if (!表单.contentAsis.trim()) 缺失.push('现状 As Is');
  if (!表单.contentTobe.trim()) 缺失.push('期望 To Be');
  if (仍要新建.value && !表单_原因()) 缺失.push('仍要新建的原因');
  if (缺失.length > 0) {
    message.warning(`请填写：${缺失.join('、')}`);
    return;
  }

  const 参数: TicketCreateParams = {
    ticketType: 表单.ticketType,
    projectId: 表单.projectId!,
    systemId: 表单.systemId!,
    moduleCode: 表单.moduleCode || undefined,
    title: 表单.title.trim(),
    priority: 表单.priority,
    description: 表单.description.trim(),
    contentAsis: 表单.contentAsis.trim(),
    contentTobe: 表单.contentTobe.trim(),
    expectDueDate: 表单.日期 ? 表单.日期.format('YYYY-MM-DD') : undefined,
    acceptanceCriteria: 表单.acceptanceCriteria.trim() || undefined,
    roiDesc: 表单.roiDesc.trim() || undefined,
  };
  if (仍要新建.value) {
    参数.dupOverride = 1;
    参数.overrideReason = 表单_原因();
  }

  提交中.value = true;
  try {
    const 结果 = await createTicketApi(参数);
    message.success(`已创建 ${结果.ticketNo}，等待文档确认`);
    可见.value = false;
    emit('created', 结果.id);
  } catch (错误: any) {
    // 业务码非 0 时 vben 会 throw response 的浅拷贝：顶层与 response.data 都有 R 结构。
    // 统一拦截器已弹过「检测到相似需求…」提示，这里只接管相似列表，不重复弹。
    const 数据 = 错误?.response?.data ?? 错误?.data ?? {};
    if (数据.bizCode === 'DUP_FOUND' && Array.isArray(数据.details)) {
      相似列表.value = 数据.details;
    }
  } finally {
    提交中.value = false;
  }
}

/** 仍要新建的原因单独存，不与表单字段混（勾选才显示） */
const 原因 = ref('');
function 表单_原因(): string {
  return 原因.value.trim();
}

defineExpose({ 打开 });
</script>

<template>
  <Modal
    v-model:open="可见"
    :confirm-loading="提交中"
    :mask-closable="false"
    :width="640"
    destroy-on-close
    ok-text="提交需求"
    title="新建需求"
    @ok="提交"
  >
    <Form layout="vertical" class="pt-2">
      <div class="flex gap-3">
        <FormItem class="w-40" label="单据类型" required>
          <Select
            v-model:value="表单.ticketType"
            :options="类型选项"
            placeholder="类型"
          />
        </FormItem>
        <FormItem class="w-32" label="优先级" required>
          <Select
            v-model:value="表单.priority"
            :options="优先级选项"
            placeholder="优先级"
          />
        </FormItem>
        <FormItem class="flex-1" label="期望完成">
          <DatePicker
            v-model:value="表单.日期"
            class="w-full"
            placeholder="期望完成日期"
          />
        </FormItem>
      </div>

      <!-- 两级联动：先选项目，系统只出该项目下的 -->
      <div class="flex gap-3">
        <FormItem class="flex-1" label="所属项目" required>
          <Select
            v-model:value="表单.projectId"
            :options="项目选项.map((o) => ({ label: `${o.code} ${o.name}`, value: o.id }))"
            placeholder="先选项目"
            show-search
            option-filter-prop="label"
            @change="项目变更"
          />
        </FormItem>
        <FormItem class="flex-1" label="所属系统" required>
          <Select
            v-model:value="表单.systemId"
            :disabled="未选项目"
            :options="系统选项.map((o) => ({ label: `${o.code} ${o.name}`, value: o.id }))"
            :placeholder="未选项目 ? '先选项目' : '选择该系统'"
            show-search
            option-filter-prop="label"
          />
        </FormItem>
      </div>

      <div class="flex gap-3">
        <FormItem class="flex-1" label="标题" required>
          <Input
            v-model:value="表单.title"
            :maxlength="100"
            placeholder="一句话说清要什么"
            show-count
          />
        </FormItem>
        <FormItem class="w-40" label="模块（可选）">
          <Input v-model:value="表单.moduleCode" placeholder="如 RPT" />
        </FormItem>
      </div>

      <FormItem label="需求简述" required>
        <Textarea
          v-model:value="表单.description"
          :rows="2"
          placeholder="做什么、给谁用、解决什么问题"
        />
      </FormItem>
      <div class="flex gap-3">
        <FormItem class="flex-1" label="现状 As Is" required>
          <Textarea
            v-model:value="表单.contentAsis"
            :rows="3"
            placeholder="现在是怎么做的，痛点在哪"
          />
        </FormItem>
        <FormItem class="flex-1" label="期望 To Be" required>
          <Textarea
            v-model:value="表单.contentTobe"
            :rows="3"
            placeholder="希望做成什么样"
          />
        </FormItem>
      </div>
      <FormItem help="每行一条；设计确认后锁定，变更需走流程" label="验收标准（可选）">
        <Textarea
          v-model:value="表单.acceptanceCriteria"
          :rows="3"
          placeholder="如：导出支持按班次筛选"
        />
      </FormItem>
      <FormItem label="ROI 说明（可选）">
        <Input v-model:value="表单.roiDesc" placeholder="带来什么收益，量化更好" />
      </FormItem>

      <!-- 查重命中：列出相似需求，用户看完再决定 -->
      <div v-if="相似列表.length > 0" class="rounded border border-orange-300 bg-orange-50 p-3 dark:border-orange-700 dark:bg-orange-950">
        <div class="mb-2 text-sm font-medium">
          发现 {{ 相似列表.length }} 条相似需求，请先确认是否重复：
        </div>
        <div class="mb-3 max-h-40 space-y-1 overflow-y-auto">
          <div
            v-for="项 in 相似列表"
            :key="项.ticketId"
            class="flex items-center justify-between gap-2 rounded bg-white px-2 py-1 text-xs dark:bg-gray-800"
          >
            <span class="min-w-0 truncate">
              <span class="text-gray-500">{{ 项.ticketNo }}</span>
              {{ 项.title }}
            </span>
            <span class="shrink-0 font-medium text-orange-600">
              {{ (项.score * 100).toFixed(1) }}%
            </span>
          </div>
        </div>
        <Checkbox v-model:checked="仍要新建">
          我已核对，与以上需求不重复，仍要新建
        </Checkbox>
        <Textarea
          v-if="仍要新建"
          v-model:value="原因"
          :rows="2"
          class="mt-2"
          placeholder="必填：说明与相似需求的区别，供后续复盘追溯"
        />
      </div>
    </Form>
  </Modal>
</template>
