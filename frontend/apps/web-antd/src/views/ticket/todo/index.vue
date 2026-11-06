<script lang="ts" setup>
/**
 * 我的待办。
 *
 * 口径与详情页的 `/actions` 完全同源（后端同一套判定），前端只做展示与轻量筛选，
 * 不自己推「该不该我处理」—— 推两遍必然和真实规则对不上，
 * 最典型的症状是待办里有单、点进去一个按钮都没有。
 *
 * 两类待办分开表达：
 * - 「待你 XX」：现在就能点，动作已可用；
 * - 「缺材料」：归属是我，但前置条件没满足（没传附件、没填工时），点进去补完就能推进。
 *   后者如果只按「enabled=true」筛就会被丢掉，那些人永远不知道有单在等他。
 *
 * 除「单据类型」外，筛选全部在前端做：`/ticket/todo` 不分页、一次返回全量候选
 * （通常几十条），六七个条件都塞给后端等于为一个列表新开一堆查询参数，
 * 收益远小于维护成本。类型必须走后端 —— 它决定后端按哪个类型算数据范围与待办判定。
 */
import type { TicketTodoRow } from '#/api/ticket';

import { computed, onMounted, reactive, ref, watch } from 'vue';
import { useRouter } from 'vue-router';

import { Page } from '@vben/common-ui';

import {
  Button,
  Card,
  Checkbox,
  Input,
  Select,
  Table,
  Tag,
} from 'ant-design-vue';

import { getTicketTodoApi } from '#/api/ticket';

/** 下拉选项 */
interface 选项 {
  label: string;
  value: string;
}

/** 类型筛选项：走后端，切换即重新请求 */
const 类型选项 = [
  { label: '全部类型', value: '' },
  { label: '需求', value: 'REQ' },
  { label: '缺陷', value: 'BUG' },
  { label: '故障', value: 'INC' },
];

/** 待办性质筛选项，取值与后端 todoType 一致 */
const 性质选项: 选项[] = [
  { label: '可立即处理', value: 'DUE' },
  { label: '待补材料', value: 'BLOCKED' },
];

const 路由 = useRouter();

const 加载中 = ref(false);
const 全部数据 = ref<TicketTodoRow[]>([]);

/** 筛选条件。除「类型」外都只影响本地计算，改动即时生效，不需要点查询 */
const 筛选 = reactive({
  类型: '',
  性质: undefined as string | undefined,
  环节: [] as string[],
  优先级: undefined as string | undefined,
  项目: undefined as string | undefined,
  关键字: '',
  只看超期: false,
});

/**
 * 从待办数据里抽取下拉选项。
 *
 * 待办是全量返回的，环节 / 优先级 / 项目直接按现有数据去重即可，
 * 不再额外请求字典接口 —— 多一次请求只是多一个可能与列表不同步的来源。
 *
 * @param 取值 从一行里取选项值
 * @param 取标签 从一行里取展示文案
 * @returns 选项列表，保持数据中首次出现的顺序
 */
function 抽取选项(
  取值: (行: TicketTodoRow) => null | string | undefined,
  取标签: (行: TicketTodoRow) => null | string | undefined,
) {
  const 结果 = new Map<string, string>();
  for (const 行 of 全部数据.value) {
    const 值 = 取值(行);
    if (!值) {
      continue;
    }
    if (!结果.has(值)) {
      结果.set(值, 取标签(行) || 值);
    }
  }
  return [...结果.entries()].map(([value, label]) => ({ label, value }));
}

/** 当前环节选项 */
const 环节选项 = computed(() =>
  抽取选项((行) => 行.status, (行) => 行.statusLabel),
);

/** 优先级选项，按 P0 → P3 排 */
const 优先级选项 = computed(() =>
  抽取选项((行) => 行.priority, (行) => 行.priorityLabel).sort((甲, 乙) =>
    甲.value.localeCompare(乙.value),
  ),
);

/** 项目选项 */
const 项目选项 = computed(() =>
  抽取选项((行) => 行.projectName, (行) => 行.projectName),
);

/** 是否设置了任一筛选条件，用于显示「重置」与「筛选后 / 共」 */
const 有筛选 = computed(
  () =>
    筛选.性质 !== undefined ||
    筛选.优先级 !== undefined ||
    筛选.项目 !== undefined ||
    筛选.环节.length > 0 ||
    筛选.只看超期 ||
    筛选.关键字.trim() !== '',
);

/** 筛选后的列表。逐条判断，任一条件不满足即排除 */
const 列表 = computed(() => {
  const 关键字 = 筛选.关键字.trim().toLowerCase();
  const 环节 = 筛选.环节;

  return 全部数据.value.filter((行) => {
    if (筛选.性质 && 行.todoType !== 筛选.性质) {
      return false;
    }
    if (环节.length > 0 && !环节.includes(行.status)) {
      return false;
    }
    if (筛选.优先级 && 行.priority !== 筛选.优先级) {
      return false;
    }
    if (筛选.项目 && 行.projectName !== 筛选.项目) {
      return false;
    }
    // 超期只有 true 才有意义，null 视为未超期
    if (筛选.只看超期 && !行.slaOverdue) {
      return false;
    }
    if (关键字) {
      const 命中 =
        (行.ticketNo || '').toLowerCase().includes(关键字) ||
        (行.title || '').toLowerCase().includes(关键字);
      if (!命中) {
        return false;
      }
    }
    return true;
  });
});

/** 可立即处理。判据用后端给的 todoType，不数动作个数 —— 卡在缺附件上的单也有可点动作 */
const 可处理数 = computed(
  () => 列表.value.filter((行) => 行.todoType === 'DUE').length,
);

/** 待补材料 */
const 待补数 = computed(
  () => 列表.value.filter((行) => 行.todoType === 'BLOCKED').length,
);

/** 已过应完成时间 */
const 超期数 = computed(() => 列表.value.filter((行) => 行.slaOverdue).length);

/** 当前页码。必须受控：筛选后条数变少，停在第 3 页会看到一张空表 */
const 当前页 = ref(1);
const 每页条数 = ref(20);

/** 筛选结果一变就回第一页 */
watch(列表, () => {
  当前页.value = 1;
});

/**
 * 换页 / 改每页条数。
 *
 * @param 页 目标页码
 * @param 条 每页条数
 */
function 换页(页: number, 条: number) {
  当前页.value = 页;
  每页条数.value = 条;
}

const 列 = [
  { dataIndex: 'ticketNo', fixed: 'left' as const, title: '单号', width: 150 },
  { dataIndex: 'ticketTypeLabel', title: '类型', width: 70 },
  { dataIndex: 'title', ellipsis: true, title: '标题', width: 250 },
  { dataIndex: 'statusLabel', title: '当前环节', width: 115 },
  { dataIndex: 'todoHint', title: '待你处理', width: 260 },
  { dataIndex: 'priorityLabel', title: '优先级', width: 90 },
  { dataIndex: 'waitingText', title: '已停留', width: 140 },
  { dataIndex: 'reporterName', title: '提出人', width: 100 },
  { dataIndex: 'ownerPath', title: '项目 / 系统', width: 170 },
];

/**
 * 类型切换后，把已经不存在的筛选项清掉。
 *
 * 换了类型，原来的「环节 / 项目 / 优先级」可能在新数据里一个都没有。
 * 留着会让列表恒为空，而下拉框里显示一个查不到的值，用户会以为数据丢了。
 */
function 收敛筛选() {
  const 环节集 = new Set(环节选项.value.map((项) => 项.value));
  筛选.环节 = 筛选.环节.filter((值) => 环节集.has(值));

  const 优先级集 = new Set(优先级选项.value.map((项) => 项.value));
  if (筛选.优先级 && !优先级集.has(筛选.优先级)) {
    筛选.优先级 = undefined;
  }

  const 项目集 = new Set(项目选项.value.map((项) => 项.value));
  if (筛选.项目 && !项目集.has(筛选.项目)) {
    筛选.项目 = undefined;
  }
}

/**
 * 加载待办。类型切换时重新请求，让后端按类型算数据范围。
 */
async function 加载() {
  加载中.value = true;
  try {
    全部数据.value = await getTicketTodoApi(筛选.类型 || undefined);
    收敛筛选();
  } finally {
    加载中.value = false;
  }
}

/** 重置全部筛选条件，包括走后端的类型 */
function 重置筛选() {
  筛选.类型 = '';
  筛选.性质 = undefined;
  筛选.环节 = [];
  筛选.优先级 = undefined;
  筛选.项目 = undefined;
  筛选.关键字 = '';
  筛选.只看超期 = false;
  加载();
}

/**
 * 点统计标签快速筛选：再点一次取消。
 *
 * @param 性质 待办性质，DUE 可立即处理 / BLOCKED 待补材料
 */
function 切换性质(性质: string) {
  筛选.性质 = 筛选.性质 === 性质 ? undefined : 性质;
}

/**
 * 打开详情。
 *
 * 参数用 Record 而不是 TicketTodoRow：antd 的 bodyCell 把 record 归为 Record<string, any>，
 * 声明成具体类型会在模板里报不兼容，而这里只用到 id。
 *
 * @param 行 待办行
 */
function 打开详情(行: Record<string, any>) {
  路由.push(`/req/detail/${行.id}`);
}

onMounted(加载);
</script>

<template>
  <Page description="轮到你处理的单，等得最久的排在前面" title="我的待办">
    <!-- 用父容器 gap 控制间距而不是给 Card 加 mb-4：
         antd 给 .ant-card 注入的 margin-bottom 会盖掉 Tailwind 的 margin 类 -->
    <div class="flex flex-col gap-4">
      <Card size="small">
        <div class="flex flex-col gap-3">
          <!-- 筛选条件：全部即时生效，不需要点查询 -->
          <div class="flex flex-wrap items-center gap-2">
            <Input
              v-model:value="筛选.关键字"
              allow-clear
              placeholder="单号或标题"
              style="width: 200px"
            />
            <Select
              v-model:value="筛选.类型"
              :options="类型选项"
              style="width: 120px"
              @change="加载"
            />
            <Select
              v-model:value="筛选.性质"
              :options="性质选项"
              allow-clear
              placeholder="待办性质"
              style="width: 140px"
            />
            <Select
              v-model:value="筛选.环节"
              :options="环节选项"
              :max-tag-count="1"
              allow-clear
              mode="multiple"
              placeholder="当前环节"
              style="width: 200px"
            />
            <Select
              v-model:value="筛选.优先级"
              :options="优先级选项"
              allow-clear
              placeholder="优先级"
              style="width: 120px"
            />
            <Select
              v-model:value="筛选.项目"
              :options="项目选项"
              allow-clear
              placeholder="项目"
              style="width: 160px"
            />
            <Checkbox v-model:checked="筛选.只看超期">只看超期</Checkbox>
            <Button :loading="加载中" @click="加载">刷新</Button>
            <Button :disabled="!有筛选" @click="重置筛选">重置</Button>
          </div>

          <!-- 统计：数字跟着筛选结果走，才和下面的表对得上。
               标签可点，等价于快捷筛选，再点一次取消 -->
          <div
            class="flex flex-wrap items-center gap-2 border-t border-gray-100 pt-3 dark:border-gray-700"
          >
            <Tag>
              筛选后 {{ 列表.length }} 条
              <template v-if="有筛选"> / 共 {{ 全部数据.length }}</template>
            </Tag>
            <Tag
              :color="筛选.性质 === 'DUE' ? 'processing' : 'default'"
              class="cursor-pointer select-none"
              @click="切换性质('DUE')"
            >
              可立即处理 {{ 可处理数 }}
            </Tag>
            <Tag
              :color="筛选.性质 === 'BLOCKED' ? 'warning' : 'default'"
              class="cursor-pointer select-none"
              @click="切换性质('BLOCKED')"
            >
              待补材料 {{ 待补数 }}
            </Tag>
            <Tag
              :color="筛选.只看超期 ? 'error' : 'default'"
              class="cursor-pointer select-none"
              @click="筛选.只看超期 = !筛选.只看超期"
            >
              已超期 {{ 超期数 }}
            </Tag>
            <a
              v-if="有筛选"
              class="cursor-pointer text-xs text-gray-500 underline-offset-2 hover:underline"
              @click="重置筛选"
            >
              清空筛选
            </a>
          </div>
        </div>
      </Card>

      <Table
        :columns="列"
        :data-source="列表"
        :loading="加载中"
        :pagination="{
          current: 当前页,
          pageSize: 每页条数,
          pageSizeOptions: ['10', '20', '50'],
          showSizeChanger: true,
          showTotal: (总: number) => `共 ${总} 条`,
          onChange: 换页,
        }"
        :scroll="{ x: 1300 }"
        row-key="id"
        size="small"
      >
        <template #emptyText>
          <div class="py-8 text-center text-sm text-gray-400">
            {{ 有筛选 ? '当前筛选条件下没有待办，试试重置' : '暂无待办' }}
          </div>
        </template>
        <template #bodyCell="{ column, record }">
          <template v-if="column.dataIndex === 'ticketNo'">
            <Button class="!px-0" type="link" @click="打开详情(record)">
              {{ record.ticketNo }}
            </Button>
          </template>
          <template v-else-if="column.dataIndex === 'ticketTypeLabel'">
            <Tag>{{ record.ticketTypeLabel || '-' }}</Tag>
          </template>
          <template v-else-if="column.dataIndex === 'title'">
            <span
              class="cursor-pointer hover:underline"
              :title="record.title"
              @click="打开详情(record)"
            >
              {{ record.title }}
            </span>
          </template>
          <template v-else-if="column.dataIndex === 'statusLabel'">
            <Tag :color="record.statusCssClass || 'default'">{{ record.statusLabel }}</Tag>
          </template>
          <template v-else-if="column.dataIndex === 'todoHint'">
            <!-- 能办的用主色、缺材料的用警示色：一个是「去做」，一个是「先补」。
                 提示语只放第一条原因（放全会把列撑爆），完整清单走悬停 -->
            <div
              :class="
                record.todoType === 'DUE'
                  ? 'text-blue-600 dark:text-blue-400'
                  : 'text-orange-600 dark:text-orange-400'
              "
              :title="record.blockedReasons.join('；')"
            >
              {{ record.todoHint }}
            </div>
            <div
              v-if="record.todoType === 'DUE' && record.dueActions.length > 1"
              class="text-xs text-gray-400"
            >
              另有：{{ record.dueActionLabels.slice(1).join('、') }}
            </div>
            <div
              v-else-if="record.todoType === 'BLOCKED' && record.blockedReasons.length > 1"
              class="text-xs text-gray-400"
              :title="record.blockedReasons.join('；')"
            >
              另有 {{ record.blockedReasons.length - 1 }} 项待补
            </div>
          </template>
          <template v-else-if="column.dataIndex === 'priorityLabel'">
            <Tag :color="record.priorityCssClass || 'default'">{{ record.priorityLabel }}</Tag>
          </template>
          <template v-else-if="column.dataIndex === 'waitingText'">
            <!-- 停留时长与 SLA 是两个口径：待办按自然小时展示「压了多久」，
                 超期标记按工作日历算（后端 slaOverdue）。所以会出现「停了 2 天但没超期」 -->
            <span
              :class="record.slaOverdue ? 'text-red-500' : ''"
              :title="`已停留 ${record.waitingHours} 小时${record.slaDueAt ? `，应完成 ${record.slaDueAt}` : ''}`"
            >
              {{ record.waitingText }}
              <Tag v-if="record.slaOverdue" class="ml-1" color="error">超期</Tag>
            </span>
          </template>
          <template v-else-if="column.dataIndex === 'ownerPath'">
            <span class="text-xs text-gray-600 dark:text-gray-300">
              {{ record.projectName || '-' }}
              <template v-if="record.systemCode"> / {{ record.systemCode }}</template>
            </span>
          </template>
        </template>
      </Table>

      <div class="text-xs text-gray-400">
        停留时长按自然小时算，从进入当前环节起；SLA 超期按工作日历算。两者口径不同，
        所以会出现「已停留 2 天但未超期」的情况。
      </div>
    </div>
  </Page>
</template>
