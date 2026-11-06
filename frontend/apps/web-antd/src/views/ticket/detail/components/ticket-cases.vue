<script lang="ts" setup>
/**
 * 详情页·测试用例。
 *
 * 这一块的存在意义不是「列一堆用例」，而是把**验收标准**和**实际测了什么**焊在一起：
 * 页面上常驻覆盖率，缺哪条标准直接点出来，UAT 点「通过」时后端再算一次同样的数。
 * 覆盖率一旦能靠前端传，这条硬拦截就等于没有。
 *
 * 用例由规则模板按验收标准逐条生成（一期不接模型），所以：
 * - 验收标准没锁定（设计评审未全过）时不给生成入口，标准会变的时候生成等于白做；
 * - 生成出来的用例是草稿，允许改，改过就标「人工」，这个比例是回看生成质量的关键指标。
 */
import type {
  CaseCoverage,
  CaseExecParams,
  CaseList,
  TestCaseItem,
} from '#/api/ticket';

import { computed, onMounted, reactive, ref, watch } from 'vue';

import {
  Button,
  Input,
  Modal,
  Progress,
  Radio,
  Select,
  Space,
  Table,
  Tag,
  Textarea,
  message,
} from 'ant-design-vue';

import {
  execTestCaseApi,
  generateTicketCasesApi,
  getTicketCasesApi,
} from '#/api/ticket';

import TicketCaseEditor from './ticket-case-editor.vue';

const props = defineProps<{
  /** 验收标准是否已锁定：未锁定时不提供生成入口 */
  acceptanceLocked?: null | number;
  ticketId: string;
}>();

const 加载中 = ref(false);
const 生成中 = ref(false);
const 数据 = ref<CaseList | null>(null);
/** 当前查看的轮次，不选表示最新 */
const 查看轮次 = ref<number | undefined>();

/** 编辑抽屉 */
const 编辑中 = ref(false);
const 编辑目标 = ref<null | TestCaseItem>(null);

/** 执行弹窗 */
const 执行中 = ref(false);
const 执行目标 = ref<null | TestCaseItem>(null);
const 执行表单 = reactive({
  execStatus: 'PASS',
  actualResult: '',
  bugId: '',
});

const 覆盖率 = computed<CaseCoverage | null>(() => 数据.value?.coverage ?? null);

/** 状态锁定才给生成入口 */
const 可生成 = computed(() => props.acceptanceLocked === 1);

/** 轮次下拉 */
const 轮次选项 = computed(() =>
  (数据.value?.rounds ?? []).map((轮) => ({
    label: `第 ${轮} 轮`,
    value: 轮,
  })),
);

/** 已生成过用例 */
const 已生成 = computed(() => (数据.value?.rounds ?? []).length > 0);

/** 轮次下拉的占位：不选时也要让人知道当前看的是第几轮 */
const 轮次占位 = computed(() => {
  const 当前 = 数据.value?.roundNo;
  const 最新 = 数据.value?.rounds?.[0];
  if (!当前) {
    return '最新轮次';
  }
  return 当前 === 最新 ? `第 ${当前} 轮（最新）` : `第 ${当前} 轮`;
});

// 列宽总和压在左栏可视宽度内（约 800px）：操作列在最右，
// 一旦触发横向滚动就看不见「执行 / 编辑」
const 列 = [
  { dataIndex: 'caseNo', title: '编号', width: 140 },
  { dataIndex: 'title', title: '用例', width: 280 },
  { dataIndex: 'criteriaIndex', title: '来源标准', width: 90 },
  { dataIndex: 'priority', title: '优先级', width: 80 },
  { dataIndex: 'execStatusLabel', title: '执行结果', width: 100 },
  { dataIndex: 'actions', title: '操作', width: 110 },
];

/**
 * 加载用例。轮次为空时取最新一轮。
 */
async function 加载() {
  加载中.value = true;
  try {
    数据.value = await getTicketCasesApi(props.ticketId, 查看轮次.value);
  } finally {
    加载中.value = false;
  }
}

/**
 * 生成用例。新一轮按当前验收标准重来，旧轮保留，便于看出「变更后漏测了哪条」。
 */
async function 生成() {
  生成中.value = true;
  try {
    const 结果 = await generateTicketCasesApi(props.ticketId);
    if (结果.warning) {
      message.warning(结果.warning);
    } else {
      message.success(`已生成 ${结果.generated} 条用例`);
    }
    查看轮次.value = undefined;
    await 加载();
  } catch {
    // 失败提示由请求层统一弹
  } finally {
    生成中.value = false;
  }
}

/**
 * 打开执行弹窗。
 *
 * @param 行 用例
 */
function 打开执行(行: Record<string, any>) {
  const 用例 = 行 as TestCaseItem;
  执行目标.value = 用例;
  执行表单.execStatus = 'PASS';
  执行表单.actualResult = '';
  执行表单.bugId = 用例.bugId ?? '';
  执行中.value = true;
}

/**
 * 提交执行结果。
 */
async function 提交执行() {
  if (!执行目标.value) {
    return;
  }
  if (执行表单.execStatus === 'FAIL' && !执行表单.actualResult.trim()) {
    message.warning('不通过要写清实际结果，否则复盘时不知道当时哪里不对');
    return;
  }
  const 参数: CaseExecParams = {
    execStatus: 执行表单.execStatus,
    actualResult: 执行表单.actualResult,
    bugId: 执行表单.bugId || undefined,
  };
  try {
    await execTestCaseApi(执行目标.value.id, 参数);
    message.success('已登记');
    执行中.value = false;
    await 加载();
  } catch {
    // 失败提示由请求层统一弹
  }
}

/**
 * 打开编辑抽屉。
 *
 * @param 行 用例
 */
function 打开编辑(行: Record<string, any>) {
  编辑目标.value = 行 as TestCaseItem;
  编辑中.value = true;
}

/** 切换轮次 */
async function 切换轮次() {
  await 加载();
}

onMounted(加载);

// 用例卡片挂在详情页里，单据切换时重新拉
watch(
  () => props.ticketId,
  () => {
    查看轮次.value = undefined;
    加载();
  },
);
</script>

<template>
  <div class="space-y-4">
    <!-- 覆盖率：这块比用例列表更重要，放最上面 -->
    <div
      v-if="覆盖率"
      class="rounded border p-3"
      :class="
        覆盖率.full
          ? 'border-green-200 bg-green-50 dark:border-green-900 dark:bg-green-950'
          : 'border-orange-200 bg-orange-50 dark:border-orange-900 dark:bg-orange-950'
      "
    >
      <div class="flex flex-wrap items-center gap-3">
        <span class="text-sm font-medium">用例覆盖率</span>
        <Progress
          :percent="Math.round((覆盖率.rate ?? 0) * 100)"
          :status="覆盖率.full ? 'success' : 'exception'"
          :show-info="false"
          style="width: 180px"
        />
        <span class="text-sm">
          {{ 覆盖率.covered }} / {{ 覆盖率.criteriaTotal }}
        </span>
        <Tag v-if="覆盖率.full" color="success">全覆盖，可做 UAT</Tag>
        <Tag v-else color="error">
          缺 {{ 覆盖率.missing.length }} 条，UAT 通过会被拦下
        </Tag>
        <Tag v-if="覆盖率.uatWaiver === 1" color="warning">已豁免</Tag>

        <Space class="ml-auto" :size="8">
          <Select
            v-if="轮次选项.length > 1"
            v-model:value="查看轮次"
            :options="轮次选项"
            :placeholder="轮次占位"
            style="width: 130px"
            @change="切换轮次"
          />
          <Button :loading="加载中" size="small" @click="加载">刷新</Button>
          <Button
            v-if="可生成"
            :loading="生成中"
            size="small"
            type="primary"
            @click="生成"
          >
            {{ 已生成 ? '按最新标准重新生成' : '生成用例' }}
          </Button>
        </Space>
      </div>

      <!-- 缺哪条直接点名，不让人去数 -->
      <div v-if="!覆盖率.full && 覆盖率.missing.length > 0" class="mt-2 text-xs">
        <div
          v-for="缺 in 覆盖率.missing.slice(0, 3)"
          :key="缺.seq"
          class="text-orange-700 dark:text-orange-300"
        >
          第 {{ 缺.seq }} 条未覆盖：{{ 缺.content }}
        </div>
        <div v-if="覆盖率.missing.length > 3" class="text-orange-600">
          另有 {{ 覆盖率.missing.length - 3 }} 条未覆盖
        </div>
      </div>

      <div
        v-if="!可生成 && !已生成"
        class="mt-2 text-xs text-gray-500 dark:text-gray-400"
      >
        验收标准在设计评审全部通过后锁定，锁定后才能按标准生成用例。
      </div>
    </div>

    <!-- 执行进度 -->
    <div v-if="数据 && 数据.items.length > 0" class="flex flex-wrap items-center gap-3 text-xs">
      <span class="text-gray-500 dark:text-gray-400">
        本轮 {{ 数据.items.length }} 条 · 已执行 {{ 数据.executedCount }}
      </span>
      <Tag v-if="数据.passedCount > 0" color="success">通过 {{ 数据.passedCount }}</Tag>
      <Tag v-if="数据.failedCount > 0" color="error">不通过 {{ 数据.failedCount }}</Tag>
    </div>

    <Table
      :columns="列"
      :data-source="数据?.items ?? []"
      :loading="加载中"
      :locale="{ emptyText: '本单还没有测试用例' }"
      :pagination="false"
      :scroll="{ x: 800 }"
      row-key="id"
      size="small"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.dataIndex === 'caseNo'">
          <span class="text-xs text-gray-600 dark:text-gray-300">{{ record.caseNo }}</span>
        </template>
        <template v-else-if="column.dataIndex === 'title'">
          <div class="flex flex-wrap items-center gap-1">
            <Tag v-if="record.focusFlag === 1" color="orange" :title="record.focusReason">
              重点
            </Tag>
            <Tag :color="record.origin === 'HUMAN' ? 'blue' : 'default'">
              {{ record.origin === 'HUMAN' ? '人工' : '规则' }}
            </Tag>
            <span class="min-w-0 break-words">{{ record.title }}</span>
          </div>
          <!-- 重点理由必须露出来：只标红不说原因，测试的人不知道该盯什么 -->
          <div
            v-if="record.focusFlag === 1 && record.focusReason"
            class="mt-1 text-xs text-orange-600 dark:text-orange-400"
          >
            {{ record.focusReason }}
          </div>
        </template>
        <template v-else-if="column.dataIndex === 'criteriaIndex'">
          <span class="text-xs">第 {{ record.criteriaIndex ?? '-' }} 条</span>
        </template>
        <template v-else-if="column.dataIndex === 'priority'">
          <Tag :color="record.priority === 'P0' ? 'red' : 'default'">
            {{ record.priority }}
          </Tag>
        </template>
        <template v-else-if="column.dataIndex === 'execStatusLabel'">
          <Tag
            :color="
              record.execStatus === 'PASS'
                ? 'success'
                : record.execStatus === 'FAIL'
                  ? 'error'
                  : 'default'
            "
          >
            {{ record.execStatusLabel }}
          </Tag>
        </template>
        <template v-else-if="column.dataIndex === 'actions'">
          <Space :size="4">
            <Button size="small" type="link" @click="打开执行(record)">执行</Button>
            <Button size="small" type="link" @click="打开编辑(record)">编辑</Button>
          </Space>
        </template>
      </template>
    </Table>

    <!-- 编辑抽屉 -->
    <TicketCaseEditor
      v-model:open="编辑中"
      :item="编辑目标"
      @saved="加载"
    />

    <!-- 执行登记 -->
    <Modal
      v-model:open="执行中"
      :title="执行目标 ? `执行用例 ${执行目标.caseNo}` : '执行用例'"
      :width="620"
      ok-text="登记结果"
      @ok="提交执行"
    >
      <div v-if="执行目标" class="space-y-3">
        <div class="rounded bg-gray-50 p-2 text-xs dark:bg-gray-800">
          <div class="font-medium">{{ 执行目标.title }}</div>
          <div class="mt-1 text-gray-500 dark:text-gray-400">
            预期：{{ 执行目标.expected || '-' }}
          </div>
          <div v-if="执行目标.steps.length > 0" class="mt-1 text-gray-500 dark:text-gray-400">
            <div v-for="(步, i) in 执行目标.steps" :key="i">{{ i + 1 }}. {{ 步 }}</div>
          </div>
        </div>

        <div>
          <div class="mb-1 text-sm">结果</div>
          <Radio.Group v-model:value="执行表单.execStatus">
            <Radio value="PASS">通过</Radio>
            <Radio value="FAIL">不通过</Radio>
            <Radio value="SKIP">跳过</Radio>
          </Radio.Group>
        </div>

        <div>
          <div class="mb-1 text-sm">
            实际结果
            <span v-if="执行表单.execStatus === 'FAIL'" class="text-xs text-red-500">
              不通过时必填
            </span>
          </div>
          <Textarea
            v-model:value="执行表单.actualResult"
            :rows="3"
            placeholder="实际看到的是什么。写「不行」不算"
          />
        </div>

        <div>
          <div class="mb-1 text-sm">关联 BUG 单（可选）</div>
          <!-- 缺陷列表接口还没有，先手填单号；有了列表后换成下拉 -->
          <Input
            v-model:value="执行表单.bugId"
            placeholder="填 BUG 单号，如 BUG-2026-000001"
          />
        </div>
      </div>
    </Modal>
  </div>
</template>
