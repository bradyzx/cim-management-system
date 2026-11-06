<script lang="ts" setup>
/**
 * 项目管理：需求归属的第一层主数据。
 *
 * 三条规则在界面上要能看出来，不能等用户点了才报错：
 * - 项目编码创建后不可改（它是附件路径的第一级），所以编辑时置灰；
 * - 下挂系统或需求单的项目不能删，列表里直接显示挂了多少；
 * - 状态改成「已关闭」只是不再接受新需求，存量单照常流转，所以不拦保存。
 *
 * 状态与类型的选项直接写死在前端：它们是 DDL 注释里定死的枚举，
 * 不进字典表，多一次字典请求反而容易和码值对不上。
 */
import type { Dayjs } from 'dayjs';
import type { TablePaginationConfig } from 'ant-design-vue';
import type { ProjectRow, ProjectSaveParams } from '#/api/basic';

import { computed, onMounted, reactive, ref } from 'vue';

import { Page } from '@vben/common-ui';
import { useAccess } from '@vben/access';

import {
  Button,
  Card,
  DatePicker,
  Form,
  FormItem,
  Input,
  InputNumber,
  Modal,
  Popconfirm,
  Select,
  Space,
  Table,
  Tag,
  Textarea,
  message,
} from 'ant-design-vue';
import dayjs from 'dayjs';

import {
  createProjectApi,
  deleteProjectApi,
  listProjectApi,
  updateProjectApi,
} from '#/api/basic';
import { requestClient } from '#/api/request';

/** 项目状态：值与 req_project.status 一致 */
const 状态选项 = [
  { color: 'default', label: '规划中', value: 'PLANNING' },
  { color: 'success', label: '进行中', value: 'ACTIVE' },
  { color: 'warning', label: '已暂停', value: 'SUSPENDED' },
  { color: 'default', label: '已关闭', value: 'CLOSED' },
];

/** 项目类型：值与 req_project.project_type 一致 */
const 类型选项 = [
  { label: '中试', value: 'PILOT' },
  { label: '量产', value: 'MASS' },
  { label: '其他', value: 'OTHER' },
];

const { hasAccessByCodes } = useAccess();
const 可新增 = hasAccessByCodes(['project:add']);
const 可编辑 = hasAccessByCodes(['project:edit']);
const 可删除 = hasAccessByCodes(['project:delete']);

// ---------------- 列表 ----------------

const 加载中 = ref(false);
const 数据 = ref<ProjectRow[]>([]);
const 关键字 = ref('');
const 状态筛选 = ref<string | undefined>();
const 分页 = reactive({ current: 1, pageSize: 10, total: 0 });

/** 表格分页配置。写成 computed 而不是模板里内联对象：模板表达式里不能写类型注解 */
const 分页配置 = computed(() => ({
  current: 分页.current,
  pageSize: 分页.pageSize,
  total: 分页.total,
  showSizeChanger: true,
  showTotal: (总数: number) => `共 ${总数} 条`,
}));

const 列 = [
  { dataIndex: 'projectCode', title: '项目编码', width: 140 },
  { dataIndex: 'projectName', title: '项目名称', ellipsis: true },
  { dataIndex: 'projectType', title: '类型', width: 80 },
  { dataIndex: 'status', title: '状态', width: 90 },
  { dataIndex: 'ownerName', title: '负责人', width: 90 },
  { dataIndex: 'deptName', title: '归口部门', width: 150, ellipsis: true },
  { dataIndex: 'startDate', title: '起止日期', width: 210 },
  { dataIndex: 'systemCount', title: '系统数', width: 80 },
  { dataIndex: 'ticketCount', title: '单据数', width: 80 },
  { dataIndex: 'sortNo', title: '排序', width: 70 },
  { dataIndex: 'actions', title: '操作', width: 120 },
];

/**
 * 拉取项目列表。
 */
async function 加载() {
  加载中.value = true;
  try {
    const 结果 = await listProjectApi({
      keyword: 关键字.value || undefined,
      status: 状态筛选.value || undefined,
      page: 分页.current,
      pageSize: 分页.pageSize,
    });
    数据.value = 结果.items;
    分页.total = 结果.total;
  } finally {
    加载中.value = false;
  }
}

/**
 * 查询：回到第一页，否则在第 3 页搜关键字会看到空列表。
 */
function 查询() {
  分页.current = 1;
  加载();
}

/**
 * 重置筛选条件。
 */
function 重置() {
  关键字.value = '';
  状态筛选.value = undefined;
  查询();
}

/**
 * 翻页 / 改每页条数。
 *
 * @param 页 分页配置
 */
function 改分页(页: TablePaginationConfig) {
  分页.current = 页.current ?? 1;
  分页.pageSize = 页.pageSize ?? 10;
  加载();
}

/**
 * 状态名。
 *
 * @param 值 状态码
 * @returns 中文名，未命中返回原值
 */
function 状态名(值: string) {
  return 状态选项.find((项) => 项.value === 值)?.label ?? 值;
}

/**
 * 状态标签颜色。
 *
 * @param 值 状态码
 * @returns antd 标签颜色
 */
function 状态色(值: string) {
  return 状态选项.find((项) => 项.value === 值)?.color ?? 'default';
}

/**
 * 类型名。
 *
 * @param 值 类型码
 * @returns 中文名，未命中返回原值
 */
function 类型名(值?: null | string) {
  if (!值) {
    return '-';
  }
  return 类型选项.find((项) => 项.value === 值)?.label ?? 值;
}

/**
 * 起止日期显示成一行。只有一边有值时不要写成「2026-01-01 ~ -」，
 * 那样看起来像数据缺了一半，用「起 / 至」把单边说清楚。
 *
 * @param 行 项目行
 * @returns 如「2026-01-01 ~ 2026-12-31」
 */
function 日期文本(行: ProjectRow) {
  if (!行.startDate && !行.endDate) {
    return '-';
  }
  if (!行.endDate) {
    return `${行.startDate} 起`;
  }
  if (!行.startDate) {
    return `至 ${行.endDate}`;
  }
  return `${行.startDate} ~ ${行.endDate}`;
}

// ---------------- 下拉数据 ----------------

/** 负责人下拉项 */
const 用户选项 = ref<{ label: string; value: string }[]>([]);
/** 归口部门下拉项 */
const 部门选项 = ref<{ label: string; value: string }[]>([]);

/**
 * 拉负责人与部门下拉。
 *
 * 用 allSettled：任一个接口失败不影响新增项目本身，
 * 只是对应的下拉空着，用户可以先把必填项填完。
 */
async function 加载下拉() {
  const [用户结果, 部门结果] = await Promise.allSettled([
    requestClient.get<{ items: { id: string; realName?: string; username: string }[] }>(
      '/system/user/list',
      { params: { page: 1, pageSize: 200 } },
    ),
    requestClient.get<{ deptName: string; id: string }[]>('/system/dept/list'),
  ]);
  if (用户结果.status === 'fulfilled') {
    用户选项.value = 用户结果.value.items.map((用户) => ({
      label: 用户.realName || 用户.username,
      value: 用户.id,
    }));
  }
  if (部门结果.status === 'fulfilled') {
    部门选项.value = 部门结果.value.map((部门) => ({
      label: 部门.deptName,
      value: 部门.id,
    }));
  }
}

// ---------------- 新增 / 编辑 ----------------

const 弹窗 = ref(false);
const 编辑中id = ref<null | string>(null);
const 提交中 = ref(false);
const 开始日期 = ref<Dayjs | undefined>();
const 结束日期 = ref<Dayjs | undefined>();

const 表单 = reactive({
  projectCode: '',
  projectName: '',
  projectType: undefined as string | undefined,
  status: 'PLANNING',
  ownerId: undefined as string | undefined,
  deptId: undefined as string | undefined,
  sortNo: 0,
  remark: '',
});

/**
 * 打开新增弹窗。
 */
function 打开新增() {
  编辑中id.value = null;
  Object.assign(表单, {
    projectCode: '',
    projectName: '',
    projectType: undefined,
    status: 'PLANNING',
    ownerId: undefined,
    deptId: undefined,
    sortNo: 0,
    remark: '',
  });
  开始日期.value = undefined;
  结束日期.value = undefined;
  弹窗.value = true;
}

/**
 * 打开编辑弹窗，把行数据回填到表单。
 *
 * @param 行 项目行
 */
function 打开编辑(行: ProjectRow) {
  编辑中id.value = 行.id;
  Object.assign(表单, {
    projectCode: 行.projectCode,
    projectName: 行.projectName,
    projectType: 行.projectType ?? undefined,
    status: 行.status,
    ownerId: 行.ownerId ?? undefined,
    deptId: 行.deptId ?? undefined,
    sortNo: 行.sortNo ?? 0,
    remark: 行.remark ?? '',
  });
  开始日期.value = 行.startDate ? dayjs(行.startDate) : undefined;
  结束日期.value = 行.endDate ? dayjs(行.endDate) : undefined;
  弹窗.value = true;
}

/**
 * 保存。日期清空时传空串，后端会把空串归一成 null。
 */
async function 保存() {
  if (!表单.projectCode.trim() || !表单.projectName.trim()) {
    message.warning('项目编码与项目名称必填');
    return;
  }
  const 载荷: ProjectSaveParams = {
    projectCode: 表单.projectCode.trim(),
    projectName: 表单.projectName.trim(),
    projectType: 表单.projectType,
    status: 表单.status,
    ownerId: 表单.ownerId ?? '',
    deptId: 表单.deptId ?? '',
    startDate: 开始日期.value?.format('YYYY-MM-DD') ?? '',
    endDate: 结束日期.value?.format('YYYY-MM-DD') ?? '',
    sortNo: 表单.sortNo ?? 0,
    remark: 表单.remark,
  };
  提交中.value = true;
  try {
    if (编辑中id.value) {
      await updateProjectApi(编辑中id.value, 载荷);
    } else {
      await createProjectApi(载荷);
    }
    message.success('保存成功');
    弹窗.value = false;
    加载();
  } catch {
    // 失败提示由请求层统一弹，这里保留弹窗让用户改完再提交
  } finally {
    提交中.value = false;
  }
}

/**
 * 删除项目。有系统或单据时后端会拒绝并说明原因，此处不预先拦。
 *
 * @param 行 项目行
 */
async function 删除(行: ProjectRow) {
  try {
    await deleteProjectApi(行.id);
    message.success('已删除');
    加载();
  } catch {
    // 统一错误提示
  }
}

onMounted(() => {
  加载();
  加载下拉();
});
</script>

<template>
  <Page title="项目管理" description="编码创建后不可修改；下有系统或需求单的项目不能删除">
    <Card size="small">
      <!-- 宽度一律走内联 style：antd 的 css-in-js 注入晚于 Tailwind，
           同特异性下 .ant-input / .ant-select 的宽度会盖掉 w-[220px]，
           结果是控件按内容自适应、筛选区被挤成多行 -->
      <div class="mb-3 flex flex-wrap items-center gap-2">
        <Input
          v-model:value="关键字"
          allow-clear
          placeholder="项目编码 / 名称"
          style="width: 200px"
          @press-enter="查询"
        />
        <Select
          v-model:value="状态筛选"
          allow-clear
          :options="状态选项.map((项) => ({ label: 项.label, value: 项.value }))"
          placeholder="全部状态"
          style="width: 130px"
        />
        <Button type="primary" @click="查询">查询</Button>
        <Button @click="重置">重置</Button>
        <div class="ml-auto">
          <Button v-if="可新增" type="primary" @click="打开新增">新增项目</Button>
        </div>
      </div>

      <Table
        :columns="列"
        :data-source="数据"
        :loading="加载中"
        :pagination="分页配置"
        row-key="id"
        size="small"
        @change="改分页"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.dataIndex === 'projectType'">
            {{ 类型名(record.projectType) }}
          </template>
          <template v-else-if="column.dataIndex === 'status'">
            <Tag :color="状态色(record.status)">{{ 状态名(record.status) }}</Tag>
          </template>
          <template v-else-if="column.dataIndex === 'ownerName'">
            {{ record.ownerName || '-' }}
          </template>
          <template v-else-if="column.dataIndex === 'deptName'">
            {{ record.deptName || '-' }}
          </template>
          <template v-else-if="column.dataIndex === 'startDate'">
            {{ 日期文本(record as ProjectRow) }}
          </template>
          <template v-else-if="column.dataIndex === 'systemCount'">
            <Tag :color="record.systemCount > 0 ? 'blue' : 'default'">
              {{ record.systemCount }}
            </Tag>
          </template>
          <template v-else-if="column.dataIndex === 'ticketCount'">
            <Tag :color="record.ticketCount > 0 ? 'blue' : 'default'">
              {{ record.ticketCount }}
            </Tag>
          </template>
          <template v-else-if="column.dataIndex === 'actions'">
            <Space :size="0">
              <Button v-if="可编辑" size="small" type="link" @click="打开编辑(record as ProjectRow)">
                编辑
              </Button>
              <Popconfirm
                v-if="可删除"
                :title="
                  record.systemCount > 0 || record.ticketCount > 0
                    ? `该项目下有 ${record.systemCount} 个系统、${record.ticketCount} 张单据，删除会被拒绝。仍要尝试？`
                    : '删除后不可恢复，确认删除？'
                "
                @confirm="删除(record as ProjectRow)"
              >
                <Button danger size="small" type="link">删除</Button>
              </Popconfirm>
            </Space>
          </template>
        </template>
      </Table>
    </Card>

    <Modal
      v-model:open="弹窗"
      :confirm-loading="提交中"
      :title="编辑中id ? '编辑项目' : '新增项目'"
      width="640px"
      @ok="保存"
    >
      <Form layout="vertical">
        <div class="flex gap-3">
          <FormItem class="flex-1" label="项目编码" required>
            <Input
              v-model:value="表单.projectCode"
              :disabled="!!编辑中id"
              placeholder="如 ZS-PILOT，全局唯一"
            />
          </FormItem>
          <FormItem class="flex-1" label="项目名称" required>
            <Input v-model:value="表单.projectName" placeholder="如 中试平台一期" />
          </FormItem>
        </div>

        <div class="flex gap-3">
          <FormItem class="flex-1" label="项目类型">
            <Select v-model:value="表单.projectType" allow-clear :options="类型选项" />
          </FormItem>
          <FormItem class="flex-1" label="状态">
            <Select
              v-model:value="表单.status"
              :options="状态选项.map((项) => ({ label: 项.label, value: 项.value }))"
            />
          </FormItem>
        </div>

        <div class="flex gap-3">
          <FormItem class="flex-1" label="项目负责人">
            <Select
              v-model:value="表单.ownerId"
              allow-clear
              :options="用户选项"
              option-filter-prop="label"
              placeholder="选择负责人"
              show-search
            />
          </FormItem>
          <FormItem class="flex-1" label="归口部门">
            <Select
              v-model:value="表单.deptId"
              allow-clear
              :options="部门选项"
              option-filter-prop="label"
              placeholder="选择部门"
              show-search
            />
          </FormItem>
        </div>

        <div class="flex gap-3">
          <FormItem class="flex-1" label="开始日期">
            <DatePicker v-model:value="开始日期" style="width: 100%" />
          </FormItem>
          <FormItem class="flex-1" label="结束日期">
            <DatePicker v-model:value="结束日期" style="width: 100%" />
          </FormItem>
        </div>

        <div class="flex gap-3">
          <FormItem class="w-[140px]" label="排序号">
            <InputNumber v-model:value="表单.sortNo" :min="0" style="width: 100%" />
          </FormItem>
          <FormItem class="flex-1" label="备注">
            <Textarea v-model:value="表单.remark" :rows="2" />
          </FormItem>
        </div>
      </Form>
    </Modal>
  </Page>
</template>
