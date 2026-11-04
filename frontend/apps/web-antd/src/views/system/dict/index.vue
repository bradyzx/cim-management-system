<script lang="ts" setup>
// 字典管理：左边字典类型，右边该类型下的字典项，两侧都可增删改。
// 接口：/system/dict/type/* 与 /system/dict/data/*（后端骨架阶段已实现，这里补完整界面）。
// 改字典会触发后端版本号 +1，前端轮询到版本变化后自动重拉下拉数据，页面不需要手工清缓存。
import { onMounted, reactive, ref } from 'vue';

import { Page } from '@vben/common-ui';
import { useAccess } from '@vben/access';
import { requestClient } from '#/api/request';
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

const { hasAccessByCodes } = useAccess();
const 可维护 = hasAccessByCodes(['system:dict:edit']);

interface DictTypeRow {
  dictName?: string;
  dictType?: string;
  id?: string;
  remark?: null | string;
  status?: string;
}

interface DictDataRow {
  cssClass?: null | string;
  dictLabel?: string;
  dictType?: string;
  dictValue?: string;
  id?: string;
  remark?: null | string;
  sort?: number;
  status?: string;
}

// ---------------- 字典类型 ----------------

const 类型加载中 = ref(false);
const 类型数据 = ref<DictTypeRow[]>([]);
const 类型关键字 = ref('');
const 当前类型 = ref<string>('');

const 类型列 = [
  { dataIndex: 'dictType', title: '编码', width: 180 },
  { dataIndex: 'dictName', title: '名称', width: 150 },
  { dataIndex: 'status', title: '状态', width: 80 },
  { dataIndex: 'actions', title: '操作', width: 110 },
];

async function 加载类型() {
  类型加载中.value = true;
  try {
    const 参数: Record<string, unknown> = { page: 1, pageSize: 100 };
    if (类型关键字.value) 参数.name = 类型关键字.value;
    const 结果 = await requestClient.get<{ items: DictTypeRow[] }>(
      '/system/dict/type/list',
      { params: 参数 },
    );
    类型数据.value = 结果.items;
    // 首次进页面或当前选中的类型被删掉时，自动选中第一条，右侧不留白
    const 仍存在 = 结果.items.some((t) => t.dictType === 当前类型.value);
    if (!仍存在 && 结果.items.length > 0) {
      选择类型(结果.items[0]!);
    }
  } finally {
    类型加载中.value = false;
  }
}

function 选择类型(行: DictTypeRow) {
  当前类型.value = 行.dictType ?? '';
  加载数据();
}

const 类型弹窗 = ref(false);
const 类型编辑中ID = ref<null | string>(null);
const 提交中 = ref(false);
const 类型表单 = reactive({
  code: '',
  name: '',
  status: 'ACTIVE',
  remark: '',
});

function 打开类型新增() {
  类型编辑中ID.value = null;
  Object.assign(类型表单, { code: '', name: '', status: 'ACTIVE', remark: '' });
  类型弹窗.value = true;
}

function 打开类型编辑(行: DictTypeRow) {
  类型编辑中ID.value = 行.id ?? null;
  Object.assign(类型表单, {
    code: 行.dictType ?? '',
    name: 行.dictName ?? '',
    status: 行.status ?? 'ACTIVE',
    remark: 行.remark ?? '',
  });
  类型弹窗.value = true;
}

async function 保存类型() {
  if (!类型表单.code.trim() || !类型表单.name.trim()) {
    message.warning('字典编码和名称必填');
    return;
  }
  提交中.value = true;
  try {
    if (类型编辑中ID.value) {
      await requestClient.put(`/system/dict/type/${类型编辑中ID.value}`, 类型表单);
    } else {
      await requestClient.post('/system/dict/type', 类型表单);
    }
    message.success('保存成功');
    类型弹窗.value = false;
    加载类型();
  } finally {
    提交中.value = false;
  }
}

async function 删除类型(行: DictTypeRow) {
  await requestClient.delete(`/system/dict/type/${行.id}`);
  message.success('已删除，其下字典项一并删除');
  加载类型();
}

// ---------------- 字典数据 ----------------

const 数据加载中 = ref(false);
const 数据 = ref<DictDataRow[]>([]);

const 数据列 = [
  { dataIndex: 'dictLabel', title: '显示名', width: 160 },
  { dataIndex: 'dictValue', title: '值', width: 140 },
  { dataIndex: 'sort', title: '排序', width: 70 },
  { dataIndex: 'cssClass', title: '颜色', width: 100 },
  { dataIndex: 'status', title: '状态', width: 80 },
  { dataIndex: 'remark', title: '备注', ellipsis: true },
  { dataIndex: 'actions', title: '操作', width: 110 },
];

async function 加载数据() {
  if (!当前类型.value) {
    数据.value = [];
    return;
  }
  数据加载中.value = true;
  try {
    const 结果 = await requestClient.get<{ items: DictDataRow[] }>(
      '/system/dict/data/list',
      { params: { page: 1, pageSize: 200, dictCode: 当前类型.value } },
    );
    数据.value = 结果.items;
  } finally {
    数据加载中.value = false;
  }
}

const 数据弹窗 = ref(false);
const 数据编辑中ID = ref<null | string>(null);
const 数据表单 = reactive({
  dictCode: '',
  label: '',
  value: '',
  sort: 0,
  status: 'ACTIVE',
  color: undefined as string | undefined,
  remark: '',
});

const 颜色选项 = [
  { label: '无', value: '' },
  { label: '成功（绿）', value: 'success' },
  { label: '警告（橙）', value: 'warning' },
  { label: '错误（红）', value: 'error' },
  { label: '信息（蓝）', value: 'processing' },
];

function 打开数据新增() {
  if (!当前类型.value) {
    message.warning('请先在左侧选择一个字典类型');
    return;
  }
  数据编辑中ID.value = null;
  Object.assign(数据表单, {
    dictCode: 当前类型.value,
    label: '',
    value: '',
    sort: 0,
    status: 'ACTIVE',
    color: undefined,
    remark: '',
  });
  数据弹窗.value = true;
}

function 打开数据编辑(行: DictDataRow) {
  数据编辑中ID.value = 行.id ?? null;
  Object.assign(数据表单, {
    dictCode: 行.dictType ?? 当前类型.value,
    label: 行.dictLabel ?? '',
    value: 行.dictValue ?? '',
    sort: 行.sort ?? 0,
    status: 行.status ?? 'ACTIVE',
    color: 行.cssClass ?? undefined,
    remark: 行.remark ?? '',
  });
  数据弹窗.value = true;
}

async function 保存数据() {
  if (!数据表单.label.trim() || !数据表单.value.trim()) {
    message.warning('显示名和值必填');
    return;
  }
  提交中.value = true;
  try {
    if (数据编辑中ID.value) {
      await requestClient.put(`/system/dict/data/${数据编辑中ID.value}`, 数据表单);
    } else {
      await requestClient.post('/system/dict/data', 数据表单);
    }
    message.success('保存成功');
    数据弹窗.value = false;
    加载数据();
  } finally {
    提交中.value = false;
  }
}

async function 删除数据(行: DictDataRow) {
  await requestClient.delete(`/system/dict/data/${行.id}`);
  message.success('已删除');
  加载数据();
}

onMounted(() => {
  加载类型();
});
</script>

<template>
  <Page title="字典管理" description="下拉选项与状态标签的统一维护，改完即时生效">
    <div class="flex gap-3">
      <Card class="w-[46%]" size="small" title="字典类型">
        <template #extra>
          <Button v-if="可维护" size="small" type="primary" @click="打开类型新增">
            新增类型
          </Button>
        </template>
        <div class="mb-2">
          <Input
            v-model:value="类型关键字"
            allow-clear
            placeholder="名称 / 编码"
            size="small"
            @press-enter="加载类型"
          />
        </div>
        <Table
          :columns="类型列"
          :data-source="类型数据"
          :custom-row="
            (行: DictTypeRow) => ({
              onClick: () => 选择类型(行),
              style:
                行.dictType === 当前类型
                  ? 'cursor: pointer; background: var(--ant-color-primary-bg)'
                  : 'cursor: pointer',
            })
          "
          :loading="类型加载中"
          :pagination="false"
          row-key="id"
          size="small"
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.dataIndex === 'status'">
              <Tag :color="record.status === 'ACTIVE' ? 'success' : 'default'">
                {{ record.status === 'ACTIVE' ? '启用' : '停用' }}
              </Tag>
            </template>
            <template v-else-if="column.dataIndex === 'actions'">
              <Space>
                <Button v-if="可维护" size="small" type="link" @click.stop="打开类型编辑(record)">
                  编辑
                </Button>
                <Popconfirm title="删除类型会连带删除其下字典项，确认？" @confirm="删除类型(record)">
                  <Button v-if="可维护" danger size="small" type="link" @click.stop>
                    删除
                  </Button>
                </Popconfirm>
              </Space>
            </template>
          </template>
        </Table>
      </Card>

      <Card class="flex-1" size="small">
        <template #title>
          字典项{{ 当前类型 ? ` · ${当前类型}` : '' }}
        </template>
        <template #extra>
          <Button v-if="可维护" size="small" type="primary" @click="打开数据新增">
            新增字典项
          </Button>
        </template>
        <Table
          :columns="数据列"
          :data-source="数据"
          :loading="数据加载中"
          :pagination="false"
          row-key="id"
          size="small"
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.dataIndex === 'cssClass'">
              <Tag v-if="record.cssClass" :color="record.cssClass">{{ record.cssClass }}</Tag>
              <span v-else>-</span>
            </template>
            <template v-else-if="column.dataIndex === 'status'">
              <Tag :color="record.status === 'ACTIVE' ? 'success' : 'default'">
                {{ record.status === 'ACTIVE' ? '启用' : '停用' }}
              </Tag>
            </template>
            <template v-else-if="column.dataIndex === 'remark'">
              {{ record.remark || '-' }}
            </template>
            <template v-else-if="column.dataIndex === 'actions'">
              <Space>
                <Button v-if="可维护" size="small" type="link" @click="打开数据编辑(record)">
                  编辑
                </Button>
                <Popconfirm title="确认删除该字典项？" @confirm="删除数据(record)">
                  <Button v-if="可维护" danger size="small" type="link">删除</Button>
                </Popconfirm>
              </Space>
            </template>
          </template>
        </Table>
      </Card>
    </div>

    <Modal
      v-model:open="类型弹窗"
      :confirm-loading="提交中"
      :title="类型编辑中ID ? '编辑字典类型' : '新增字典类型'"
      @ok="保存类型"
    >
      <Form layout="vertical">
        <FormItem label="字典编码" required>
          <Input v-model:value="类型表单.code" placeholder="如 ticket_priority" />
        </FormItem>
        <FormItem label="字典名称" required>
          <Input v-model:value="类型表单.name" placeholder="如 优先级" />
        </FormItem>
        <FormItem label="状态">
          <Select
            v-model:value="类型表单.status"
            :options="[
              { label: '启用', value: 'ACTIVE' },
              { label: '停用', value: 'DISABLED' },
            ]"
          />
        </FormItem>
        <FormItem label="备注">
          <Textarea v-model:value="类型表单.remark" :rows="2" />
        </FormItem>
      </Form>
    </Modal>

    <Modal
      v-model:open="数据弹窗"
      :confirm-loading="提交中"
      :title="数据编辑中ID ? '编辑字典项' : '新增字典项'"
      @ok="保存数据"
    >
      <Form layout="vertical">
        <FormItem label="所属类型">
          <Input :value="数据表单.dictCode" disabled />
        </FormItem>
        <FormItem label="显示名" required>
          <Input v-model:value="数据表单.label" placeholder="如 P0 紧急" />
        </FormItem>
        <FormItem label="值" required>
          <Input v-model:value="数据表单.value" placeholder="如 P0" />
        </FormItem>
        <FormItem label="排序">
          <InputNumber v-model:value="数据表单.sort" :min="0" style="width: 100%" />
        </FormItem>
        <FormItem label="标签颜色">
          <Select v-model:value="数据表单.color" allow-clear :options="颜色选项" />
        </FormItem>
        <FormItem label="状态">
          <Select
            v-model:value="数据表单.status"
            :options="[
              { label: '启用', value: 'ACTIVE' },
              { label: '停用', value: 'DISABLED' },
            ]"
          />
        </FormItem>
        <FormItem label="备注">
          <Textarea v-model:value="数据表单.remark" :rows="2" />
        </FormItem>
      </Form>
    </Modal>
  </Page>
</template>
