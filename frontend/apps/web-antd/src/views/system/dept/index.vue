<script lang="ts" setup>
// 部门管理：树形增删改。接口 /system/dept/*。
// 后端返回平铺列表（ancestors 已排好序），前端按 parentId 组树交给 Table 的 children 渲染。
// 后端不支持改父级：改父级要重算整棵子树的 ancestors 与层级，本期不做，编辑时父级只读。
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
const 可维护 = hasAccessByCodes(['system:dept:edit']);

interface DeptRow {
  ancestors?: string;
  children?: DeptRow[];
  deptCode?: string;
  deptLevel?: number;
  deptName?: string;
  id?: string;
  leaderUserId?: null | string;
  parentId?: null | string;
  remark?: null | string;
  sort?: number;
  status?: string;
}

interface UserOption {
  id: string;
  realName: string;
}

const 加载中 = ref(false);
const 树数据 = ref<DeptRow[]>([]);
const 关键字 = ref('');
const 部门选项 = ref<{ label: string; value: string }[]>([]);
const 用户选项 = ref<{ label: string; value: string }[]>([]);

const 列定义 = [
  { dataIndex: 'deptName', title: '部门名称', width: 240 },
  { dataIndex: 'deptCode', title: '部门编码', width: 140 },
  { dataIndex: 'leaderUserId', title: '负责人', width: 120 },
  { dataIndex: 'sort', title: '排序', width: 80 },
  { dataIndex: 'status', title: '状态', width: 90 },
  { dataIndex: 'remark', title: '备注', ellipsis: true },
  { dataIndex: 'actions', title: '操作', width: 200 },
];

/** 平铺 → 树。孤儿节点（父已删）挂到根，避免数据异常时整条分支消失。 */
function 组树(平铺: DeptRow[]): DeptRow[] {
  const 根: DeptRow[] = [];
  const 按ID = new Map<string, DeptRow>();
  for (const 节点 of 平铺) {
    节点.children = [];
    按ID.set(节点.id ?? '', 节点);
  }
  for (const 节点 of 平铺) {
    const 父 = 节点.parentId ? 按ID.get(节点.parentId) : undefined;
    if (父 && 父 !== 节点) {
      父.children!.push(节点);
    } else {
      根.push(节点);
    }
  }
  return 根;
}

async function 加载列表() {
  加载中.value = true;
  try {
    const 参数 = 关键字.value ? { name: 关键字.value } : {};
    const 结果 = await requestClient.get<DeptRow[]>('/system/dept/list', { params: 参数 });
    树数据.value = 组树(结果);
    部门选项.value = 结果.map((d) => ({ label: d.deptName ?? '', value: d.id ?? '' }));
  } finally {
    加载中.value = false;
  }
}

/** 负责人下拉：用户总量在几百以内，一次拉够用；后续量大了再换远程搜索。 */
async function 加载用户() {
  const 结果 = await requestClient.get<{ items: UserOption[] }>('/system/user/list', {
    params: { page: 1, pageSize: 500 },
  });
  用户选项.value = 结果.items.map((u) => ({ label: u.realName, value: u.id }));
}

// ---------------- 新增 / 编辑 ----------------

const 弹窗 = ref(false);
const 编辑中ID = ref<null | string>(null);
const 提交中 = ref(false);
const 表单 = reactive({
  deptCode: '',
  deptName: '',
  parentId: undefined as string | undefined,
  leaderUserId: undefined as string | undefined,
  sort: 0,
  status: 'ACTIVE',
  remark: '',
});

function 打开新增(父?: DeptRow) {
  编辑中ID.value = null;
  Object.assign(表单, {
    deptCode: '',
    deptName: '',
    parentId: 父?.id,
    leaderUserId: undefined,
    sort: 0,
    status: 'ACTIVE',
    remark: '',
  });
  弹窗.value = true;
}

function 打开编辑(行: DeptRow) {
  编辑中ID.value = 行.id ?? null;
  Object.assign(表单, {
    deptCode: 行.deptCode ?? '',
    deptName: 行.deptName ?? '',
    parentId: 行.parentId ?? undefined,
    leaderUserId: 行.leaderUserId ?? undefined,
    sort: 行.sort ?? 0,
    status: 行.status ?? 'ACTIVE',
    remark: 行.remark ?? '',
  });
  弹窗.value = true;
}

async function 保存() {
  if (!表单.deptCode.trim() || !表单.deptName.trim()) {
    message.warning('部门编码和名称必填');
    return;
  }
  提交中.value = true;
  try {
    if (编辑中ID.value) {
      // 编辑时不提交父级：后端不支持改父级，避免误以为能调整层级
      const { parentId: _父级, ...可改字段 } = 表单;
      await requestClient.put(`/system/dept/${编辑中ID.value}`, 可改字段);
    } else {
      await requestClient.post('/system/dept', 表单);
    }
    message.success('保存成功');
    弹窗.value = false;
    加载列表();
  } finally {
    提交中.value = false;
  }
}

async function 删除(行: DeptRow) {
  await requestClient.delete(`/system/dept/${行.id}`);
  message.success('已删除');
  加载列表();
}

onMounted(() => {
  加载列表();
  加载用户();
});
</script>

<template>
  <Page title="部门管理" description="组织层级维护；有子部门或仍有人在用时不允许删除">
    <Card>
      <div class="mb-3 flex items-center gap-2">
        <Input
          v-model:value="关键字"
          allow-clear
          placeholder="部门名称 / 编码"
          style="width: 220px"
          @press-enter="加载列表"
        />
        <Button type="primary" @click="加载列表">查询</Button>
        <Button v-if="可维护" type="primary" @click="打开新增()">新增部门</Button>
      </div>

      <Table
        :columns="列定义"
        :data-source="树数据"
        :loading="加载中"
        :pagination="false"
        default-expand-all-rows
        row-key="id"
        size="small"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.dataIndex === 'leaderUserId'">
            {{ 用户选项.find((u) => u.value === record.leaderUserId)?.label || '-' }}
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
              <Button v-if="可维护" size="small" type="link" @click="打开新增(record)">
                加子部门
              </Button>
              <Button v-if="可维护" size="small" type="link" @click="打开编辑(record)">
                编辑
              </Button>
              <Popconfirm title="确认删除该部门？" @confirm="删除(record)">
                <Button v-if="可维护" danger size="small" type="link">删除</Button>
              </Popconfirm>
            </Space>
          </template>
        </template>
      </Table>
    </Card>

    <Modal
      v-model:open="弹窗"
      :confirm-loading="提交中"
      :title="编辑中ID ? '编辑部门' : '新增部门'"
      @ok="保存"
    >
      <Form layout="vertical">
        <FormItem label="部门编码" required>
          <Input v-model:value="表单.deptCode" placeholder="如 MIT（大写下划线）" />
        </FormItem>
        <FormItem label="部门名称" required>
          <Input v-model:value="表单.deptName" placeholder="如 MIT 开发组" />
        </FormItem>
        <FormItem label="上级部门">
          <!-- 新增时可选父级；编辑时锁定——后端不支持改父级，避免误以为能调整层级 -->
          <Select
            v-model:value="表单.parentId"
            allow-clear
            :disabled="!!编辑中ID"
            placeholder="根部门（不选即为根）"
            :options="部门选项"
          />
        </FormItem>
        <FormItem label="负责人">
          <Select
            v-model:value="表单.leaderUserId"
            allow-clear
            show-search
            :options="用户选项"
            :filter-option="(输入: string, 项?: { label?: string }) => (项?.label ?? '').includes(输入)"
          />
        </FormItem>
        <FormItem label="排序">
          <InputNumber v-model:value="表单.sort" :min="0" style="width: 100%" />
        </FormItem>
        <FormItem label="状态">
          <Select
            v-model:value="表单.status"
            :options="[
              { label: '启用', value: 'ACTIVE' },
              { label: '停用', value: 'DISABLED' },
            ]"
          />
        </FormItem>
        <FormItem label="备注">
          <Textarea v-model:value="表单.remark" :rows="2" />
        </FormItem>
      </Form>
    </Modal>
  </Page>
</template>
