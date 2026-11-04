<script lang="ts" setup>
/**
 * 详情页·附件区。
 *
 * 三块：必填缺口、上传入口、按节点分组的文件列表。
 *
 * 缺口数据来自 `/attach-rules`，与后端流转拦截读同一张规则表，
 * 所以这里显示「齐了」，点流转按钮就不会被拦。
 *
 * 上传只传 attachType，不传 stage —— 后端按当前状态归档。
 */
import type { UploadProps } from 'ant-design-vue';
import type {
  TicketAttachment,
  TicketAttachRuleStatus,
} from '#/api/ticket';

import { computed, ref } from 'vue';

import {
  Button,
  Empty,
  Popconfirm,
  Select,
  Space,
  Tag,
  Upload,
  message,
} from 'ant-design-vue';

import {
  confirmTicketAttachmentApi,
  deleteTicketAttachmentApi,
  downloadTicketAttachmentApi,
  uploadTicketAttachmentApi,
} from '#/api/ticket';

const props = defineProps<{
  /** 单据是否已关闭，关闭后不可再改附件 */
  closed: boolean;
  /** 当前环节的附件缺口 */
  rules: TicketAttachRuleStatus[];
  /** 已有附件 */
  attachments: TicketAttachment[];
  /** 单据 ID */
  ticketId: string;
}>();

const emit = defineEmits<{ refresh: [] }>();

/** 交付物类型下拉项 */
interface 类型选项 {
  label: string;
  value: string;
}

/** 缺口分组 */
interface 缺口组 {
  targetStatus: string;
  targetStatusLabel: string;
  items: TicketAttachRuleStatus[];
}

/** antd 自定义上传函数的签名 */
type 自定义上传 = NonNullable<UploadProps['customRequest']>;

// 同上，Select 的 SelectValue 不接受 null
const 已选类型 = ref<string | undefined>(undefined);
const 上传中 = ref(false);

/**
 * 可选类型 = 缺口规则里出现的类型 + 已上传过的类型。
 *
 * 不拉全量字典：attach_type 有 100 个槽位，绝大多数在本环节没有意义，
 * 给一堆用不上的选项只会让人选错。
 */
const 类型选项 = computed<类型选项[]>(() => {
  const 表 = new Map<string, string>();
  for (const 规则 of props.rules) {
    表.set(规则.attachType, 规则.attachTypeLabel);
  }
  for (const 附件 of props.attachments) {
    if (!表.has(附件.attachType)) {
      表.set(附件.attachType, 附件.attachTypeLabel);
    }
  }
  return [...表.entries()].map(([value, label]) => ({ label, value }));
});

/** 按目标状态分组的缺口 */
const 缺口分组 = computed<缺口组[]>(() => {
  const 表 = new Map<string, 缺口组>();
  for (const 规则 of props.rules) {
    const 已有 = 表.get(规则.targetStatus);
    if (已有) {
      已有.items.push(规则);
    } else {
      表.set(规则.targetStatus, {
        targetStatus: 规则.targetStatus,
        targetStatusLabel: 规则.targetStatusLabel,
        items: [规则],
      });
    }
  }
  return [...表.values()];
});

/** 按上传节点分组的附件 */
const 附件分组 = computed(() => {
  const 表 = new Map<string, { label: string; items: TicketAttachment[] }>();
  for (const 附件 of props.attachments) {
    const 已有 = 表.get(附件.stage);
    if (已有) {
      已有.items.push(附件);
    } else {
      表.set(附件.stage, { label: 附件.stageLabel, items: [附件] });
    }
  }
  return [...表.values()];
});

/**
 * 上传。命中重复时后端返回已有附件，这里提示「已存在」而不是报错。
 */
const 上传: 自定义上传 = async (选项) => {
  // 先落到局部变量：await 之后 ref 的值可能已被改动，TS 也不会保留窄化结果
  const 类型 = 已选类型.value;
  if (!类型) {
    message.warning('请先选择交付物类型');
    return;
  }
  上传中.value = true;
  try {
    const 结果 = await uploadTicketAttachmentApi(
      props.ticketId,
      类型,
      // antd 传进来的就是浏览器 File，窄化一下类型即可
      选项.file as File,
    );
    if (结果.duplicated) {
      message.info('该文件已在本环节存在，未重复上传');
    } else {
      message.success('上传成功');
    }
    emit('refresh');
    选项.onSuccess?.(结果);
  } catch (错误) {
    message.error(错误 instanceof Error ? 错误.message : '上传失败');
    选项.onError?.(错误 as Error);
  } finally {
    上传中.value = false;
  }
};

/**
 * 下载。
 *
 * @param 附件 附件
 */
async function 下载(附件: TicketAttachment) {
  try {
    await downloadTicketAttachmentApi(附件.id, 附件.originalName);
  } catch (错误) {
    message.error(错误 instanceof Error ? 错误.message : '下载失败');
  }
}

/**
 * 设为确认版。
 *
 * @param 附件 附件
 */
async function 设为确认版(附件: TicketAttachment) {
  try {
    await confirmTicketAttachmentApi(附件.id);
    message.success('已标记为确认版');
    emit('refresh');
  } catch (错误) {
    message.error(错误 instanceof Error ? 错误.message : '操作失败');
  }
}

/**
 * 删除附件。
 *
 * @param 附件 附件
 */
async function 删除(附件: TicketAttachment) {
  try {
    await deleteTicketAttachmentApi(附件.id);
    message.success('已删除');
    emit('refresh');
  } catch (错误) {
    message.error(错误 instanceof Error ? 错误.message : '删除失败');
  }
}
</script>

<template>
  <div>
    <div v-if="缺口分组.length > 0" class="mb-4 space-y-2">
      <div
        v-for="组 in 缺口分组"
        :key="组.targetStatus"
        class="rounded border border-dashed border-gray-300 p-2 dark:border-gray-600"
      >
        <div class="mb-1 text-xs text-gray-500 dark:text-gray-400">
          流转到「{{ 组.targetStatusLabel }}」需提供
        </div>
        <Space wrap :size="[6, 6]">
          <Tag
            v-for="项 in 组.items"
            :key="项.attachType"
            :color="项.satisfied ? 'success' : 项.required ? 'error' : 'default'"
          >
            {{ 项.attachTypeLabel }}
            {{ 项.uploaded }}/{{ 项.minCount }}
            <template v-if="!项.required">（可选）</template>
          </Tag>
        </Space>
      </div>
    </div>

    <!-- 选择框与按钮同一行（用户要求）。Select 用 flex-1 弹性收缩，
         窄卡放不下时按钮自动换行，不会再把提示文字挤出卡片。 -->
    <div class="mb-2 flex flex-wrap items-center gap-2">
      <Select
        v-model:value="已选类型"
        :disabled="closed"
        :options="类型选项"
        class="min-w-0 flex-1"
        placeholder="选择交付物类型"
        show-search
      />
      <Upload
        :custom-request="上传"
        :disabled="closed || !已选类型"
        :show-upload-list="false"
      >
        <Button class="shrink-0" :disabled="closed || !已选类型" :loading="上传中" type="primary">
          上传附件
        </Button>
      </Upload>
    </div>
    <div class="mb-3 text-xs text-gray-400">
      单文件不超过 50MB；可执行文件与脚本不允许上传
    </div>

    <Empty v-if="附件分组.length === 0" :image-style="{ height: '48px' }" description="暂无附件" />

    <div v-for="组 in 附件分组" :key="组.label" class="mb-4">
      <div class="mb-2 flex items-center gap-2">
        <span class="text-xs font-medium text-gray-500 dark:text-gray-400">{{ 组.label }}</span>
        <Tag>{{ 组.items.length }}</Tag>
      </div>
      <div class="space-y-2">
        <div
          v-for="附件 in 组.items"
          :key="附件.id"
          class="rounded border border-gray-200 p-2 dark:border-gray-700"
        >
          <div class="flex items-start justify-between gap-2">
            <div class="min-w-0">
              <div class="flex items-center gap-2">
                <span class="truncate text-sm" :title="附件.originalName">
                  {{ 附件.originalName }}
                </span>
                <Tag v-if="附件.confirmed" color="success">确认版</Tag>
                <Tag v-if="附件.fileMissing" color="error">文件丢失</Tag>
              </div>
              <div class="mt-1 text-xs text-gray-500 dark:text-gray-400">
                {{ 附件.attachTypeLabel }} · {{ 附件.sizeText }} ·
                {{ 附件.uploaderName || '未知' }} · {{ 附件.createdAt?.slice(0, 16) }}
              </div>
            </div>
            <Space class="shrink-0" :size="0">
              <Button
                :disabled="附件.fileMissing"
                size="small"
                type="link"
                @click="下载(附件)"
              >
                下载
              </Button>
              <Button
                v-if="!附件.confirmed"
                :disabled="closed"
                size="small"
                type="link"
                title="同类型只保留一个确认版"
                @click="设为确认版(附件)"
              >
                确认
              </Button>
              <Popconfirm
                v-if="附件.canDelete"
                title="删除后列表不再显示，确定删除？"
                @confirm="删除(附件)"
              >
                <Button danger size="small" type="link">删除</Button>
              </Popconfirm>
            </Space>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
