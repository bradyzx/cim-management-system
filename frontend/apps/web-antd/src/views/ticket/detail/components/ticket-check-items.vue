<script lang="ts" setup>
/**
 * 详情页·上线点检。
 *
 * 点检是上线后唯一「必须有人看过一眼」的环节，所以这里的重点是让人**点得动**：
 * 通过 / 不通过 / 不适用三个动作直接在行内，不通过才弹窗要说明。
 * 包一层「先点编辑、再选结果」的流程，最后就是整张清单没人点。
 *
 * 清单为空是允许的（`02` 4.5）：新建系统在清单维护好之前，所有单子都得能走完点检，
 * 否则上线流程死锁。空清单时显示提示，点检通过后后端会在流转日志标
 * `check_items_empty=1` 留痕。
 */
import type { CheckItem, CheckItemList } from '#/api/ticket';

import { computed, onMounted, reactive, ref, watch } from 'vue';

import {
  Button,
  Input,
  Modal,
  Progress,
  Space,
  Table,
  Tag,
  message,
} from 'ant-design-vue';

import {
  generateCheckItemsApi,
  getCheckItemsApi,
  saveCheckItemApi,
} from '#/api/ticket';

const props = defineProps<{ ticketId: string }>();

const 加载中 = ref(false);
const 生成中 = ref(false);
const 数据 = ref<CheckItemList | null>(null);

/** 不通过弹窗 */
const 弹窗 = ref(false);
const 目标 = ref<CheckItem | null>(null);
const 表单 = reactive({ checkRemark: '' });

/** 空清单 */
const 空清单 = computed(() => 数据.value?.empty === 1);

/** 重点项进度 */
const 进度 = computed(() => {
  const 总 = 数据.value?.keyTotal ?? 0;
  const 已 = 数据.value?.keyChecked ?? 0;
  return 总 === 0 ? 100 : Math.round((已 / 总) * 100);
});

/** 全部重点项已点检 */
const 已完成 = computed(() => {
  const 总 = 数据.value?.keyTotal ?? 0;
  return 总 > 0 && (数据.value?.keyChecked ?? 0) >= 总;
});

// 列宽总和控制在左栏可视宽度内（约 800px）：超了就得横向滚动，
// 而操作列在最右边，一滚就看不见 —— 用户会以为「不通过」这个按钮不存在
const 列 = [
  { dataIndex: 'itemName', title: '功能点', width: 200 },
  { dataIndex: 'itemDesc', title: '怎么验', width: 210 },
  { dataIndex: 'checkResult', title: '点检结果', width: 110 },
  { dataIndex: 'checkRemark', title: '说明', width: 110 },
  { dataIndex: 'actions', title: '操作', width: 150 },
];

/**
 * 加载清单。
 */
async function 加载() {
  加载中.value = true;
  try {
    数据.value = await getCheckItemsApi(props.ticketId);
  } finally {
    加载中.value = false;
  }
}

/**
 * 从需求文档功能清单生成草稿。
 */
async function 生成() {
  生成中.value = true;
  try {
    数据.value = await generateCheckItemsApi(props.ticketId);
    message.success(`已按需求文档生成 ${数据.value.items.length} 项点检草稿`);
  } catch {
    // 失败提示由请求层统一弹
  } finally {
    生成中.value = false;
  }
}

/**
 * 直接登记通过 / 不适用。不通过时要先问原因，所以单独走弹窗。
 *
 * @param 行 点检项
 * @param 结果 PASS / NA
 */
async function 直接登记(行: Record<string, any>, 结果: string) {
  try {
    数据.value = await saveCheckItemApi(行.id, { checkResult: 结果 });
    message.success(结果 === 'PASS' ? '已标记通过' : '已标记不适用');
  } catch {
    // 失败提示由请求层统一弹
  }
}

/**
 * 打开不通过弹窗。
 *
 * @param 行 点检项
 */
function 打开不通过(行: Record<string, any>) {
  目标.value = 行 as CheckItem;
  表单.checkRemark = '';
  弹窗.value = true;
}

/**
 * 提交不通过。
 */
async function 提交不通过() {
  if (!目标.value) {
    return;
  }
  if (!表单.checkRemark.trim()) {
    message.warning('不通过必须写清哪里不对，否则复盘时只剩一个「没通过」');
    return;
  }
  try {
    数据.value = await saveCheckItemApi(目标.value.id, {
      checkResult: 'FAIL',
      checkRemark: 表单.checkRemark.trim(),
    });
    弹窗.value = false;
    message.success('已登记不通过');
  } catch {
    // 失败提示由请求层统一弹
  }
}

onMounted(加载);

watch(
  () => props.ticketId,
  () => 加载(),
);
</script>

<template>
  <div class="space-y-4">
    <!-- 进度 + 生成入口 -->
    <div
      class="flex flex-wrap items-center gap-3 rounded border p-3"
      :class="
        空清单
          ? 'border-gray-200 bg-gray-50 dark:border-gray-700 dark:bg-gray-800'
          : 已完成
            ? 'border-green-200 bg-green-50 dark:border-green-900 dark:bg-green-950'
            : 'border-orange-200 bg-orange-50 dark:border-orange-900 dark:bg-orange-950'
      "
    >
      <template v-if="空清单">
        <span class="text-sm">本单未配置点检项</span>
        <Tag>可直接点检通过，日志会标记未配置</Tag>
      </template>
      <template v-else>
        <span class="text-sm font-medium">重点功能点检</span>
        <Progress
          :percent="进度"
          :status="已完成 ? 'success' : 'active'"
          :show-info="false"
          style="width: 160px"
        />
        <span class="text-sm">
          {{ 数据?.keyChecked }} / {{ 数据?.keyTotal }}
        </span>
        <Tag v-if="已完成" color="success">重点项已全部点检</Tag>
        <Tag v-else color="error">未点检完不能提交点检通过</Tag>
        <span v-if="(数据?.total ?? 0) > (数据?.keyTotal ?? 0)" class="text-xs text-gray-500">
          另有 {{ (数据?.total ?? 0) - (数据?.keyTotal ?? 0) }} 项非重点
        </span>
      </template>

      <Space class="ml-auto" :size="8">
        <Button :loading="加载中" size="small" @click="加载">刷新</Button>
        <Button
          v-if="空清单"
          :loading="生成中"
          size="small"
          type="primary"
          @click="生成"
        >
          从需求文档生成
        </Button>
      </Space>
    </div>

    <Table
      :columns="列"
      :data-source="数据?.items ?? []"
      :loading="加载中"
      :locale="{ emptyText: '本单未配置点检项，可直接点检通过' }"
      :pagination="false"
      :scroll="{ x: 780 }"
      row-key="id"
      size="small"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.dataIndex === 'itemName'">
          <div class="flex items-start gap-1">
            <Tag v-if="record.isKey === 1" class="shrink-0" color="red">重点</Tag>
            <span class="min-w-0 text-sm">{{ record.itemName }}</span>
          </div>
          <div v-if="record.source === 'AI_SUGGEST'" class="text-xs text-gray-400">
            文档带出
          </div>
        </template>
        <template v-else-if="column.dataIndex === 'itemDesc'">
          <span class="text-xs text-gray-600 dark:text-gray-300">
            {{ record.itemDesc || '-' }}
          </span>
        </template>
        <template v-else-if="column.dataIndex === 'checkResult'">
          <Tag
            v-if="record.checkResult"
            :color="
              record.checkResult === 'PASS'
                ? 'success'
                : record.checkResult === 'FAIL'
                  ? 'error'
                  : 'default'
            "
          >
            {{ record.checkResultLabel }}
          </Tag>
          <span v-else class="text-xs text-gray-400">未点检</span>
          <div v-if="record.checkedByName" class="text-xs text-gray-400">
            {{ record.checkedByName }}
          </div>
        </template>
        <template v-else-if="column.dataIndex === 'checkRemark'">
          <span class="text-xs" :title="record.checkRemark">{{ record.checkRemark || '-' }}</span>
        </template>
        <template v-else-if="column.dataIndex === 'actions'">
          <Space :size="4">
            <Button size="small" type="link" @click="直接登记(record, 'PASS')">
              通过
            </Button>
            <Button size="small" danger type="link" @click="打开不通过(record)">
              不通过
            </Button>
            <Button size="small" type="link" @click="直接登记(record, 'NA')">
              不适用
            </Button>
          </Space>
        </template>
      </template>
    </Table>

    <Modal
      v-model:open="弹窗"
      :title="目标 ? `点检不通过：${目标.itemName}` : '点检不通过'"
      :width="560"
      ok-text="登记"
      @ok="提交不通过"
    >
      <div class="space-y-3">
        <div class="rounded bg-gray-50 p-2 text-xs dark:bg-gray-800">
          <div>怎么验：{{ 目标?.itemDesc || '-' }}</div>
          <div class="mt-1">期望：{{ 目标?.expectResult || '-' }}</div>
        </div>
        <div>
          <div class="mb-1 text-sm">
            哪里不对
            <span class="text-xs text-red-500">必填</span>
          </div>
          <Input.TextArea
            v-model:value="表单.checkRemark"
            :rows="4"
            placeholder="具体表现是什么，如「过站后 WIP 未扣减，仍显示原数量」"
          />
        </div>
      </div>
    </Modal>
  </div>
</template>
