<script lang="ts" setup>
/**
 * 单据列表（通用表格）。
 *
 * 需求列表与需求池共用本组件：池子只是把状态锁死在「待分配」，
 * 其余筛选、列、分页完全一致。写两份会立刻分叉。
 *
 * 可见范围由后端按角色算，前端不传、也无法传范围参数 ——
 * 传范围等于把可见性交给客户端。
 */
import type { Dayjs } from 'dayjs';
import type { TicketListParams, TicketRow } from '#/api/ticket';

import { onMounted, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';

import { useAccess } from '@vben/access';

import {
  Button,
  Card,
  DatePicker,
  Input,
  Select,
  Space,
  Table,
  Tag,
} from 'ant-design-vue';

import TicketCreateModal from './ticket-create-modal.vue';

import { requestClient } from '#/api/request';
import { getTicketListApi } from '#/api/ticket';

import { 计算时限 } from '../utils';

// 用解构出来的 RangePicker 而不是 DatePicker.RangePicker：
// 后者是「组件对象上的属性」，vue-tsc 推不出 props 类型，v-model 与 value-format 会互相打架
const { RangePicker } = DatePicker;

const props = defineProps<{
  /** 固定筛选条件。需求池用它锁死 PENDING_ASSIGN，锁住后界面上不再给状态选择 */
  fixedQuery?: { status?: string };
  /** 是否显示「新建需求」入口 */
  showCreate?: boolean;
}>();

const { hasAccessByCodes } = useAccess();
/** 提需求的按钮权限；权限码由种子授给提出人 / Leader / 管理员 */
const 可建单 = hasAccessByCodes(['ticket:create']);

const 建单弹窗 = ref<InstanceType<typeof TicketCreateModal> | null>(null);

const 路由 = useRouter();

/** 下拉选项 */
interface 选项 {
  label: string;
  value: string;
}

/** 固定了状态时，状态筛选不再展示 */
const 状态锁定 = Boolean(props.fixedQuery?.status);

const 加载中 = ref(false);
const 数据 = ref<TicketRow[]>([]);
const 总数 = ref(0);

const 状态选项 = ref<选项[]>([]);
const 优先级选项 = ref<选项[]>([]);
const 类型选项 = ref<选项[]>([]);

const 查询 = reactive({
  page: 1,
  pageSize: 20,
  keyword: '',
  status: [] as string[],
  priority: undefined as string | undefined,
  ticketType: undefined as string | undefined,
  // 用 Dayjs 二元组而不是字符串：RangePicker 的 valueFormat 与 value 类型在
  // ant-design-vue 4 的类型定义里互相牵制，直接给 Dayjs 最省事，格式化放到查询时做
  日期: undefined as [Dayjs, Dayjs] | undefined,
});

// 列宽总和控制在 1100 以内：1366 屏去掉侧边栏后可视区约 1100，
// 超过就会出现横向滚动，固定在右侧的操作列会压在中间列上，看起来像文字被遮住。
// 操作列因此不做 fixed —— 滚动到位自然能看到，比浮在内容上强。
const 列定义 = [
  { dataIndex: 'ticketNo', title: '单号', width: 145 },
  { dataIndex: 'title', title: '标题', ellipsis: true },
  { dataIndex: 'ticketTypeLabel', title: '类型', width: 70 },
  { dataIndex: 'statusLabel', title: '状态', width: 130 },
  { dataIndex: 'priorityLabel', title: '优先级', width: 85 },
  { dataIndex: 'reporterName', title: '提出人', width: 85 },
  { dataIndex: 'assigneeName', title: '承办人', width: 85 },
  { dataIndex: 'deptName', title: '提出部门', width: 105 },
  { dataIndex: 'systemCode', title: '系统', width: 75 },
  { dataIndex: 'slaDueAt', title: '当前时限', width: 130 },
  { dataIndex: 'actions', title: '操作', width: 60 },
];

/** 拉三个字典。列表要显示中文标签与语义色，前端不硬编码 27 个状态。 */
async function 加载字典() {
  const [状态, 优先级, 类型] = await Promise.all([
    requestClient.get<选项[]>('/dict/data', { params: { code: 'ticket_status' } }),
    requestClient.get<选项[]>('/dict/data', { params: { code: 'ticket_priority' } }),
    requestClient.get<选项[]>('/dict/data', { params: { code: 'ticket_type' } }),
  ]);
  状态选项.value = 状态 ?? [];
  优先级选项.value = 优先级 ?? [];
  类型选项.value = 类型 ?? [];
}

/** 查询列表 */
async function 加载列表() {
  加载中.value = true;
  try {
    const 参数: TicketListParams = {
      page: 查询.page,
      pageSize: 查询.pageSize,
    };
    if (查询.keyword.trim()) {
      参数.keyword = 查询.keyword.trim();
    }
    const 状态 = 状态锁定
      ? props.fixedQuery?.status
      : 查询.status.length > 0
        ? 查询.status.join(',')
        : undefined;
    if (状态) {
      参数.status = 状态;
    }
    if (查询.priority) {
      参数.priority = 查询.priority;
    }
    if (查询.ticketType) {
      参数.ticketType = 查询.ticketType;
    }
    if (查询.日期) {
      参数.startDate = 查询.日期[0].format('YYYY-MM-DD');
      参数.endDate = 查询.日期[1].format('YYYY-MM-DD');
    }

    const 结果 = await getTicketListApi(参数);
    数据.value = 结果.items ?? [];
    总数.value = 结果.total ?? 0;
  } finally {
    加载中.value = false;
  }
}

/** 搜索：回到第一页 */
function 搜索() {
  查询.page = 1;
  加载列表();
}

/** 重置筛选条件。固定条件不重置，否则需求池会变成全量列表 */
function 重置() {
  查询.keyword = '';
  查询.priority = undefined;
  查询.ticketType = undefined;
  查询.日期 = undefined;
  if (!状态锁定) {
    查询.status = [];
  }
  搜索();
}

/**
 * 建单完成：跳到新单详情，让用户接着确认需求文档。
 *
 * @param 单据id 新单 ID
 */
function 建单完成(单据id: string) {
  加载列表();
  路由.push(`/req/detail/${单据id}`);
}

/**
 * 打开详情。
 *
 * 参数用 Record 而不是 TicketRow：antd 的 bodyCell 插槽把 record 归为 Record<string, any>，
 * 声明成 TicketRow 会在模板里报类型不兼容，而这里只用到 id。
 *
 * @param 行 列表行
 */
function 打开详情(行: Record<string, any>) {
  路由.push(`/req/detail/${行.id}`);
}

onMounted(async () => {
  await 加载字典();
  await 加载列表();
});
</script>

<template>
  <div>
    <Card class="mb-4" size="small">
      <Space wrap :size="[8, 8]">
        <Input
          v-model:value="查询.keyword"
          allow-clear
          placeholder="单号或标题"
          style="width: 220px"
          @press-enter="搜索"
        />
        <Select
          v-if="!状态锁定"
          v-model:value="查询.status"
          :options="状态选项"
          class="w-56"
          mode="multiple"
          :max-tag-count="1"
          placeholder="状态"
        />
        <Select
          v-model:value="查询.ticketType"
          :options="类型选项"
          allow-clear
          class="w-32"
          placeholder="类型"
        />
        <Select
          v-model:value="查询.priority"
          :options="优先级选项"
          allow-clear
          class="w-32"
          placeholder="优先级"
        />
        <RangePicker v-model:value="查询.日期" :placeholder="['开始日期', '结束日期']" />
        <Button type="primary" @click="搜索">查询</Button>
        <Button @click="重置">重置</Button>
        <Button
          v-if="showCreate && 可建单"
          type="primary"
          ghost
          @click="建单弹窗?.打开()"
        >
          新建需求
        </Button>
      </Space>
    </Card>

    <Table
      :columns="列定义"
      :data-source="数据"
      :loading="加载中"
      :pagination="{
        total: 总数,
        current: 查询.page,
        pageSize: 查询.pageSize,
        showSizeChanger: true,
        pageSizeOptions: ['10', '20', '50'],
        showTotal: (总: number) => `共 ${总} 条`,
      }"
      :scroll="{ x: 1100 }"
      row-key="id"
      size="small"
      @change="(分页: { current?: number; pageSize?: number }) => {
        查询.page = 分页.current ?? 1;
        查询.pageSize = 分页.pageSize ?? 20;
        加载列表();
      }"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.dataIndex === 'ticketNo'">
          <Button class="!px-0" type="link" @click="打开详情(record)">
            {{ record.ticketNo }}
          </Button>
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
        <template v-else-if="column.dataIndex === 'priorityLabel'">
          <Tag :color="record.priorityCssClass || 'default'">{{ record.priorityLabel }}</Tag>
        </template>
        <template v-else-if="column.dataIndex === 'slaDueAt'">
          <span
            v-if="!record.slaDueAt"
            class="text-gray-400"
          >-</span>
          <span
            v-else
            :class="计算时限(record.slaDueAt)?.超期 ? 'text-red-500' : 'text-gray-600 dark:text-gray-300'"
            :title="`应完成 ${record.slaDueAt}`"
          >
            {{ 计算时限(record.slaDueAt)?.文本 }}
          </span>
        </template>
        <template v-else-if="column.dataIndex === 'actions'">
          <Button size="small" type="link" @click="打开详情(record)">详情</Button>
        </template>
      </template>
    </Table>

    <TicketCreateModal ref="建单弹窗" @created="建单完成" />
  </div>
</template>
