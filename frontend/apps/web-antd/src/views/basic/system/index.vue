<script lang="ts" setup>
/**
 * 系统管理：需求归属的第二层主数据。
 *
 * 与项目管理的三点差别，界面上必须体现：
 * - 系统编码只在项目内唯一，不同项目可以各有一个 MES，所以校验提示要说「该项目下已存在」；
 * - 所属项目创建后不可改（单据的 project_id 是提单快照），编辑时置灰；
 * - 下挂需求单的系统不能删，列表里显示单据数，删之前就能看出来。
 *
 * 项目下拉取的是管理接口的全量项目（含已关闭），
 * 不用 `/basic/projects`：那个接口按设计排除已关闭项目，管理员在这里要能看到它们。
 */
import type { TablePaginationConfig } from 'ant-design-vue';
import type { SystemRow, SystemSaveParams } from '#/api/basic';

import { computed, onMounted, reactive, ref } from 'vue';

import { Page } from '@vben/common-ui';
import { useAccess } from '@vben/access';

import {
  Button,
  Card,
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

import {
  createSystemApi,
  deleteSystemApi,
  listProjectApi,
  listSystemApi,
  updateSystemApi,
} from '#/api/basic';
import { requestClient } from '#/api/request';

/** 系统状态：值与 req_system.status 一致 */
const 状态选项 = [
  { color: 'processing', label: '建设中', value: 'BUILDING' },
  { color: 'success', label: '在运', value: 'LIVE' },
  { color: 'default', label: '已下线', value: 'OFFLINE' },
];

/** 系统类型：值与 req_system.system_type 一致 */
const 类型选项 = [
  { label: '外购', value: 'PURCHASED' },
  { label: '自研', value: 'INHOUSE' },
  { label: '在运', value: 'RUNNING' },
];

const { hasAccessByCodes } = useAccess();
const 可新增 = hasAccessByCodes(['system:add']);
const 可编辑 = hasAccessByCodes(['system:edit']);
const 可删除 = hasAccessByCodes(['system:delete']);

// ---------------- 列表 ----------------

const 加载中 = ref(false);
const 数据 = ref<SystemRow[]>([]);
const 关键字 = ref('');
const 项目筛选 = ref<string | undefined>();
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
  { dataIndex: 'projectName', title: '所属项目', width: 150, ellipsis: true },
  { dataIndex: 'systemCode', title: '系统编码', width: 120 },
  { dataIndex: 'systemName', title: '系统名称', ellipsis: true },
  { dataIndex: 'systemType', title: '类型', width: 80 },
  { dataIndex: 'status', title: '状态', width: 90 },
  { dataIndex: 'ownerName', title: '负责人', width: 90 },
  { dataIndex: 'ticketCount', title: '单据数', width: 80 },
  { dataIndex: 'sortNo', title: '排序', width: 70 },
  { dataIndex: 'createdAt', title: '创建时间', width: 140 },
  { dataIndex: 'actions', title: '操作', width: 120 },
];

/** 项目下拉项 */
const 项目选项 = ref<{ label: string; value: string }[]>([]);

/**
 * 拉取系统列表。
 */
async function 加载() {
  加载中.value = true;
  try {
    const 结果 = await listSystemApi({
      projectId: 项目筛选.value || undefined,
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
 * 拉全部项目，供筛选与新增时选择。
 *
 * 用 allSettled 混在一起没必要：这个接口失败时页面主体（系统列表）仍然可用，
 * 只是项目列显示空、新增时会提示先选项目。
 */
async function 加载项目() {
  try {
    const 结果 = await listProjectApi({ page: 1, pageSize: 200 });
    项目选项.value = 结果.items.map((项目) => ({
      label: `${项目.projectCode} ${项目.projectName}`,
      value: 项目.id,
    }));
  } catch {
    // 统一错误提示
  }
}

/**
 * 查询：回到第一页。
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
  项目筛选.value = undefined;
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

// ---------------- 下拉数据 ----------------

/** 负责人下拉项 */
const 用户选项 = ref<{ label: string; value: string }[]>([]);

/**
 * 拉负责人下拉。失败不影响新增，只是下拉空着。
 */
async function 加载用户() {
  try {
    const 结果 = await requestClient.get<{
      items: { id: string; realName?: string; username: string }[];
    }>('/system/user/list', { params: { page: 1, pageSize: 200 } });
    用户选项.value = 结果.items.map((用户) => ({
      label: 用户.realName || 用户.username,
      value: 用户.id,
    }));
  } catch {
    // 统一错误提示
  }
}

// ---------------- 新增 / 编辑 ----------------

const 弹窗 = ref(false);
const 编辑中id = ref<null | string>(null);
const 提交中 = ref(false);

const 表单 = reactive({
  projectId: undefined as string | undefined,
  systemCode: '',
  systemName: '',
  systemType: undefined as string | undefined,
  ownerId: undefined as string | undefined,
  status: 'BUILDING',
  sortNo: 0,
  remark: '',
});

/**
 * 打开新增弹窗。
 */
function 打开新增() {
  编辑中id.value = null;
  Object.assign(表单, {
    // 已按项目筛过就默认带过去，省得再选一次
    projectId: 项目筛选.value,
    systemCode: '',
    systemName: '',
    systemType: undefined,
    ownerId: undefined,
    status: 'BUILDING',
    sortNo: 0,
    remark: '',
  });
  弹窗.value = true;
}

/**
 * 打开编辑弹窗，把行数据回填到表单。
 *
 * @param 行 系统行
 */
function 打开编辑(行: SystemRow) {
  编辑中id.value = 行.id;
  Object.assign(表单, {
    projectId: 行.projectId,
    systemCode: 行.systemCode,
    systemName: 行.systemName,
    systemType: 行.systemType ?? undefined,
    ownerId: 行.ownerId ?? undefined,
    status: 行.status,
    sortNo: 行.sortNo ?? 0,
    remark: 行.remark ?? '',
  });
  弹窗.value = true;
}

/**
 * 保存。
 */
async function 保存() {
  if (!表单.projectId) {
    message.warning('请先选择所属项目');
    return;
  }
  if (!表单.systemCode.trim() || !表单.systemName.trim()) {
    message.warning('系统编码与系统名称必填');
    return;
  }
  const 载荷: SystemSaveParams = {
    projectId: 表单.projectId,
    systemCode: 表单.systemCode.trim(),
    systemName: 表单.systemName.trim(),
    systemType: 表单.systemType,
    ownerId: 表单.ownerId ?? '',
    status: 表单.status,
    sortNo: 表单.sortNo ?? 0,
    remark: 表单.remark,
  };
  提交中.value = true;
  try {
    if (编辑中id.value) {
      await updateSystemApi(编辑中id.value, 载荷);
    } else {
      await createSystemApi(载荷);
    }
    message.success('保存成功');
    弹窗.value = false;
    加载();
  } catch {
    // 失败提示由请求层统一弹，保留弹窗让用户改完再提交
  } finally {
    提交中.value = false;
  }
}

/**
 * 删除系统。有单据时后端拒绝并说明原因。
 *
 * @param 行 系统行
 */
async function 删除(行: SystemRow) {
  try {
    await deleteSystemApi(行.id);
    message.success('已删除');
    加载();
  } catch {
    // 统一错误提示
  }
}

onMounted(() => {
  加载();
  加载项目();
  加载用户();
});
</script>

<template>
  <Page
    title="系统管理"
    description="编码在项目内唯一；系统只属一个项目，归属与编码创建后不可修改"
  >
    <Card size="small">
      <!-- 宽度一律走内联 style：antd 的 css-in-js 注入晚于 Tailwind，
           .ant-select / .ant-input 的宽度会盖掉 w-[220px]，控件自适应后筛选区会被挤成三行 -->
      <div class="mb-3 flex flex-wrap items-center gap-2">
        <Select
          v-model:value="项目筛选"
          allow-clear
          :options="项目选项"
          option-filter-prop="label"
          placeholder="全部项目"
          show-search
          style="width: 220px"
        />
        <Input
          v-model:value="关键字"
          allow-clear
          placeholder="系统编码 / 名称"
          style="width: 170px"
          @press-enter="查询"
        />
        <Select
          v-model:value="状态筛选"
          allow-clear
          :options="状态选项.map((项) => ({ label: 项.label, value: 项.value }))"
          placeholder="全部状态"
          style="width: 120px"
        />
        <Button type="primary" @click="查询">查询</Button>
        <Button @click="重置">重置</Button>
        <div class="ml-auto">
          <Button v-if="可新增" type="primary" @click="打开新增">新增系统</Button>
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
          <template v-if="column.dataIndex === 'projectName'">
            <span :title="record.projectName">{{ record.projectName || '-' }}</span>
          </template>
          <template v-else-if="column.dataIndex === 'systemType'">
            {{ 类型名(record.systemType) }}
          </template>
          <template v-else-if="column.dataIndex === 'status'">
            <Tag :color="状态色(record.status)">{{ 状态名(record.status) }}</Tag>
          </template>
          <template v-else-if="column.dataIndex === 'ownerName'">
            {{ record.ownerName || '-' }}
          </template>
          <template v-else-if="column.dataIndex === 'ticketCount'">
            <Tag :color="record.ticketCount > 0 ? 'blue' : 'default'">
              {{ record.ticketCount }}
            </Tag>
          </template>
          <template v-else-if="column.dataIndex === 'createdAt'">
            {{ record.createdAt?.slice(0, 16) || '-' }}
          </template>
          <template v-else-if="column.dataIndex === 'actions'">
            <Space :size="0">
              <Button v-if="可编辑" size="small" type="link" @click="打开编辑(record as SystemRow)">
                编辑
              </Button>
              <Popconfirm
                v-if="可删除"
                :title="
                  record.ticketCount > 0
                    ? `该系统下有 ${record.ticketCount} 张单据，删除会被拒绝。仍要尝试？`
                    : '删除后不可恢复，确认删除？'
                "
                @confirm="删除(record as SystemRow)"
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
      :title="编辑中id ? '编辑系统' : '新增系统'"
      width="620px"
      @ok="保存"
    >
      <Form layout="vertical">
        <FormItem label="所属项目" required>
          <Select
            v-model:value="表单.projectId"
            :disabled="!!编辑中id"
            :options="项目选项"
            option-filter-prop="label"
            placeholder="选择系统所属的项目"
            show-search
          />
        </FormItem>

        <div class="flex gap-3">
          <FormItem class="flex-1" label="系统编码" required>
            <Input
              v-model:value="表单.systemCode"
              :disabled="!!编辑中id"
              placeholder="如 MES，项目内唯一"
            />
          </FormItem>
          <FormItem class="flex-1" label="系统名称" required>
            <Input v-model:value="表单.systemName" placeholder="如 制造执行系统" />
          </FormItem>
        </div>

        <div class="flex gap-3">
          <FormItem class="flex-1" label="系统类型">
            <Select v-model:value="表单.systemType" allow-clear :options="类型选项" />
          </FormItem>
          <FormItem class="flex-1" label="状态">
            <Select
              v-model:value="表单.status"
              :options="状态选项.map((项) => ({ label: 项.label, value: 项.value }))"
            />
          </FormItem>
        </div>

        <div class="flex gap-3">
          <FormItem class="flex-1" label="系统负责人">
            <Select
              v-model:value="表单.ownerId"
              allow-clear
              :options="用户选项"
              option-filter-prop="label"
              placeholder="选择负责人"
              show-search
            />
          </FormItem>
          <FormItem class="w-[140px]" label="排序号">
            <InputNumber v-model:value="表单.sortNo" :min="0" style="width: 100%" />
          </FormItem>
        </div>

        <FormItem label="备注">
          <Textarea v-model:value="表单.remark" :rows="2" />
        </FormItem>
      </Form>
    </Modal>
  </Page>
</template>
