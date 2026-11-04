<script lang="ts" setup>
// 数据范围：角色 × 单据类型 的可见/可写规则维护（sys_data_scope_rule）。
// 接口：/system/data-scope/*，规则总量小，列表不分页整表返回。
// 规则说明：ticketType='*' 是该角色全部单据类型的兜底规则；
// scopeType 八种取值见 03 技术方案数据模型（SELF/ASSIGNED/GROUP/DEPT/DEPT_AND_SUB/ALL/CUSTOM/NONE）。
import { computed, onMounted, reactive, ref } from 'vue';

import { Page } from '@vben/common-ui';
import { useAccess } from '@vben/access';
import { requestClient } from '#/api/request';
import {
  Button,
  Card,
  Form,
  FormItem,
  Input,
  Modal,
  Popconfirm,
  Select,
  Space,
  Switch,
  Table,
  Tag,
  Textarea,
  message,
} from 'ant-design-vue';

const { hasAccessByCodes } = useAccess();
const 可维护 = hasAccessByCodes(['system:scope:edit']);

interface RoleRow {
  roleCode?: string;
  roleName?: string;
}

interface ScopeRow {
  id?: string;
  roleCode?: string;
  ticketType?: string;
  scopeType?: string;
  readable?: number;
  writable?: number;
  enabled?: number;
  extraJson?: null | string;
  remark?: null | string;
  updatedAt?: string;
}

const 范围选项 = [
  { label: 'SELF · 仅本人创建', value: 'SELF' },
  { label: 'ASSIGNED · 指派给我的', value: 'ASSIGNED' },
  { label: 'GROUP · 用户组内', value: 'GROUP' },
  { label: 'DEPT · 本部门', value: 'DEPT' },
  { label: 'DEPT_AND_SUB · 本部门及下级', value: 'DEPT_AND_SUB' },
  { label: 'ALL · 全部', value: 'ALL' },
  { label: 'CUSTOM · 自定义用户组（预留）', value: 'CUSTOM' },
  { label: 'NONE · 不可见', value: 'NONE' },
];
const 单据选项 = [
  { label: '* · 全部单据类型（兜底）', value: '*' },
  { label: 'REQ · 需求', value: 'REQ' },
  { label: 'BUG · 缺陷', value: 'BUG' },
  { label: 'INC · 事件', value: 'INC' },
];

const 加载中 = ref(false);
const 数据 = ref<ScopeRow[]>([]);
const 角色 = ref<RoleRow[]>([]);
const 筛选 = reactive({ roleCode: '', ticketType: '' });

const 角色选项 = computed(() =>
  角色.value.map((r) => ({ label: `${r.roleCode} · ${r.roleName}`, value: r.roleCode })),
);

const 列定义 = [
  { dataIndex: 'roleCode', title: '角色编码', width: 150 },
  { dataIndex: 'ticketType', title: '单据类型', width: 110 },
  { dataIndex: 'scopeType', title: '可见范围', width: 220 },
  { dataIndex: 'readable', title: '可读', width: 80 },
  { dataIndex: 'writable', title: '可写', width: 80 },
  { dataIndex: 'enabled', title: '启用', width: 80 },
  { dataIndex: 'remark', title: '备注', ellipsis: true },
  { dataIndex: 'updatedAt', title: '更新时间', width: 170 },
  { dataIndex: 'actions', title: '操作', width: 130 },
];

async function 加载列表() {
  加载中.value = true;
  try {
    const 参数: Record<string, unknown> = {};
    if (筛选.roleCode) 参数.roleCode = 筛选.roleCode;
    if (筛选.ticketType) 参数.ticketType = 筛选.ticketType;
    数据.value = await requestClient.get<ScopeRow[]>('/system/data-scope/list', {
      params: 参数,
    });
  } finally {
    加载中.value = false;
  }
}

/** 角色下拉数据。角色总量是个位数到十几个量级，一次拉全即可。 */
async function 加载角色() {
  const 结果 = await requestClient.get<{ items: RoleRow[] }>('/system/role/list', {
    params: { page: 1, pageSize: 200 },
  });
  角色.value = 结果.items;
}

function 范围文本(值?: string) {
  const 命中 = 范围选项.find((o) => o.value === 值);
  return 命中 ? 命中.label : (值 ?? '-');
}

function 单据文本(值?: string) {
  if (!值) return '-';
  if (值 === '*') return '全部类型';
  const 命中 = 单据选项.find((o) => o.value === 值);
  return 命中 ? 命中.label : 值;
}

/** Select 的 show-search 过滤：按「编码 · 名称」整串模糊匹配。选项类型 antd 未给全，弱化处理。 */
function 按标签过滤(输入: string, 选项?: { label?: string }) {
  return (选项?.label ?? '').includes(输入);
}

// ---------------- 新增 / 编辑 ----------------

const 编辑弹窗 = ref(false);
const 编辑中ID = ref<string | null>(null);
const 提交中 = ref(false);
const 表单 = reactive({
  roleCode: '',
  ticketType: '*',
  scopeType: 'SELF',
  readable: true,
  writable: true,
  enabled: true,
  extraJson: '',
  remark: '',
});

function 打开新增() {
  编辑中ID.value = null;
  Object.assign(表单, {
    roleCode: '',
    ticketType: '*',
    scopeType: 'SELF',
    readable: true,
    writable: true,
    enabled: true,
    extraJson: '',
    remark: '',
  });
  编辑弹窗.value = true;
}

function 打开编辑(行: ScopeRow) {
  编辑中ID.value = 行.id ?? null;
  Object.assign(表单, {
    roleCode: 行.roleCode ?? '',
    ticketType: 行.ticketType ?? '*',
    scopeType: 行.scopeType ?? 'SELF',
    readable: 行.readable === 1,
    writable: 行.writable === 1,
    enabled: 行.enabled === 1,
    extraJson: 行.extraJson ?? '',
    remark: 行.remark ?? '',
  });
  编辑弹窗.value = true;
}

async function 保存() {
  if (!表单.roleCode) {
    message.warning('请选择角色');
    return;
  }
  提交中.value = true;
  try {
    if (编辑中ID.value) {
      // 编辑时不传 roleCode：roleCode 是唯一键的一部分，换角色应删了重建，避免误操作
      const { roleCode: _角色, ...可改字段 } = 表单;
      await requestClient.put(`/system/data-scope/${编辑中ID.value}`, 可改字段);
    } else {
      await requestClient.post('/system/data-scope', 表单);
    }
    message.success('保存成功');
    编辑弹窗.value = false;
    加载列表();
  } finally {
    提交中.value = false;
  }
}

async function 删除(行: ScopeRow) {
  await requestClient.delete(`/system/data-scope/${行.id}`);
  message.success('已删除');
  加载列表();
}

onMounted(() => {
  加载列表();
  加载角色();
});
</script>

<template>
  <Page title="数据范围" description="按「角色 × 单据类型」配置单据的可见与可写范围">
    <Card>
      <div class="mb-3 flex items-center gap-2">
        <Select
          v-model:value="筛选.roleCode"
          allow-clear
          placeholder="角色"
          show-search
          style="width: 240px"
          :options="角色选项"
          :filter-option="按标签过滤"
        />
        <Select
          v-model:value="筛选.ticketType"
          allow-clear
          placeholder="单据类型"
          style="width: 180px"
          :options="单据选项"
        />
        <Button type="primary" @click="加载列表">查询</Button>
        <Button v-if="可维护" type="primary" @click="打开新增">新增规则</Button>
      </div>

      <Table
        :columns="列定义"
        :data-source="数据"
        :loading="加载中"
        :pagination="false"
        row-key="id"
        size="small"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.dataIndex === 'scopeType'">
            {{ 范围文本(record.scopeType) }}
          </template>
          <template v-else-if="column.dataIndex === 'ticketType'">
            {{ 单据文本(record.ticketType) }}
          </template>
          <template v-else-if="column.dataIndex === 'readable'">
            <Tag :color="record.readable === 1 ? 'success' : 'default'">
              {{ record.readable === 1 ? '是' : '否' }}
            </Tag>
          </template>
          <template v-else-if="column.dataIndex === 'writable'">
            <Tag :color="record.writable === 1 ? 'success' : 'default'">
              {{ record.writable === 1 ? '是' : '否' }}
            </Tag>
          </template>
          <template v-else-if="column.dataIndex === 'enabled'">
            <Tag :color="record.enabled === 1 ? 'success' : 'default'">
              {{ record.enabled === 1 ? '启用' : '停用' }}
            </Tag>
          </template>
          <template v-else-if="column.dataIndex === 'remark'">
            {{ record.remark || '-' }}
          </template>
          <template v-else-if="column.dataIndex === 'actions'">
            <Space>
              <Button v-if="可维护" size="small" type="link" @click="打开编辑(record)">
                编辑
              </Button>
              <Popconfirm title="确认删除该规则？" @confirm="删除(record)">
                <Button v-if="可维护" danger size="small" type="link">删除</Button>
              </Popconfirm>
            </Space>
          </template>
        </template>
      </Table>
    </Card>

    <Modal
      v-model:open="编辑弹窗"
      :confirm-loading="提交中"
      :title="编辑中ID ? '编辑规则' : '新增规则'"
      @ok="保存"
    >
      <Form layout="vertical">
        <FormItem label="角色" required>
          <!-- 编辑时锁定角色：roleCode 是唯一键的一部分，换角色应删了重建 -->
          <Select
            v-model:value="表单.roleCode"
            :disabled="!!编辑中ID"
            show-search
            :options="角色选项"
            :filter-option="按标签过滤"
          />
        </FormItem>
        <FormItem label="单据类型" required>
          <Select v-model:value="表单.ticketType" :options="单据选项" />
        </FormItem>
        <FormItem label="可见范围" required>
          <Select v-model:value="表单.scopeType" :options="范围选项" />
        </FormItem>
        <FormItem label="读写与启用">
          <Space size="large">
            <span>可读 <Switch v-model:checked="表单.readable" /></span>
            <span>可写 <Switch v-model:checked="表单.writable" /></span>
            <span>启用 <Switch v-model:checked="表单.enabled" /></span>
          </Space>
        </FormItem>
        <FormItem extra="范围附加参数，JSON 格式，如 GROUP 范围的用户组 ID；一般留空">
          <Textarea v-model:value="表单.extraJson" :rows="2" placeholder='{"groupId":"..."}' />
        </FormItem>
        <FormItem label="备注">
          <Input v-model:value="表单.remark" />
        </FormItem>
      </Form>
    </Modal>
  </Page>
</template>
