<script lang="ts" setup>
// 角色管理：角色列表 + 新增/编辑/启停/删除 + 角色-菜单授权。
// 接口：/system/role/*（查询契约 mock 清单 2.4，写操作为本系统扩展）。
// 关键规则：内置角色（isSystem=1）不允许删除、不允许改编码；
// 角色下仍有用户绑定时后端会拒绝删除。
import { defineAsyncComponent, onMounted, reactive, ref } from 'vue';

import { Page, useVbenDrawer } from '@vben/common-ui';
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

/** 菜单授权抽屉。独立组件：树勾选逻辑自成一体，放同一文件里主页面会太长。 */
const 授权抽屉 = defineAsyncComponent(() => import('./role-auth-drawer.vue'));

const { hasAccessByCodes } = useAccess();
const 可维护 = hasAccessByCodes(['system:role:edit']);

interface RoleRow {
  id?: string;
  roleCode?: string;
  roleName?: string;
  isSystem?: number;
  sort?: number;
  status?: string;
  remark?: null | string;
  createdAt?: string;
}

// ---------------- 列表 ----------------

const 加载中 = ref(false);
const 数据 = ref<RoleRow[]>([]);
const 总数 = ref(0);
const 查询 = reactive({ page: 1, pageSize: 20, name: '', status: '' });

const 列定义 = [
  { dataIndex: 'roleCode', title: '角色编码' },
  { dataIndex: 'roleName', title: '角色名称' },
  { dataIndex: 'isSystem', title: '内置', width: 80 },
  { dataIndex: 'sort', title: '排序', width: 80 },
  { dataIndex: 'status', title: '状态', width: 90 },
  { dataIndex: 'remark', title: '备注', ellipsis: true },
  { dataIndex: 'createdAt', title: '创建时间', width: 170 },
  { dataIndex: 'actions', title: '操作', width: 220 },
];

async function 加载列表() {
  加载中.value = true;
  try {
    const 参数: Record<string, unknown> = {
      page: 查询.page,
      pageSize: 查询.pageSize,
    };
    if (查询.name) 参数.name = 查询.name;
    if (查询.status) 参数.status = 查询.status;
    const 结果 = await requestClient.get<{ items: RoleRow[]; total: number }>(
      '/system/role/list',
      { params: 参数 },
    );
    数据.value = 结果.items;
    总数.value = 结果.total;
  } finally {
    加载中.value = false;
  }
}

function 搜索() {
  查询.page = 1;
  加载列表();
}

// ---------------- 新增 / 编辑 ----------------

const 编辑弹窗 = ref(false);
const 编辑中ID = ref<string | null>(null);
const 提交中 = ref(false);
const 表单 = reactive({
  roleCode: '',
  roleName: '',
  sort: 0,
  status: 'ACTIVE',
  remark: '',
});

/** 编辑内置角色时编码只读：编码被数据范围规则引用，改了会失联（后端同样有校验兜底）。 */
const 编码只读 = ref(false);

function 打开新增() {
  编辑中ID.value = null;
  编码只读.value = false;
  Object.assign(表单, { roleCode: '', roleName: '', sort: 0, status: 'ACTIVE', remark: '' });
  编辑弹窗.value = true;
}

function 打开编辑(行: RoleRow) {
  编辑中ID.value = 行.id ?? null;
  编码只读.value = 行.isSystem === 1;
  Object.assign(表单, {
    roleCode: 行.roleCode ?? '',
    roleName: 行.roleName ?? '',
    sort: 行.sort ?? 0,
    status: 行.status ?? 'ACTIVE',
    remark: 行.remark ?? '',
  });
  编辑弹窗.value = true;
}

async function 保存() {
  if (!表单.roleCode.trim() || !表单.roleName.trim()) {
    message.warning('角色编码和名称必填');
    return;
  }
  提交中.value = true;
  try {
    if (编辑中ID.value) {
      await requestClient.put(`/system/role/${编辑中ID.value}`, 表单);
    } else {
      await requestClient.post('/system/role', 表单);
    }
    message.success('保存成功');
    编辑弹窗.value = false;
    加载列表();
  } finally {
    提交中.value = false;
  }
}

/** 启用/停用直接改 status：停用而不是删除，历史单据上的角色引用不丢。 */
async function 切换状态(行: RoleRow) {
  const 新状态 = 行.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE';
  await requestClient.put(`/system/role/${行.id}`, { status: 新状态 });
  message.success(新状态 === 'ACTIVE' ? '已启用' : '已停用');
  加载列表();
}

async function 删除(行: RoleRow) {
  await requestClient.delete(`/system/role/${行.id}`);
  message.success('已删除');
  加载列表();
}

// ---------------- 菜单授权 ----------------

const [Drawer, drawerApi] = useVbenDrawer({ connectedComponent: 授权抽屉 });

function 打开授权(行: RoleRow) {
  drawerApi.setData({ roleId: 行.id, roleName: 行.roleName });
  drawerApi.open();
}

onMounted(() => {
  加载列表();
});
</script>

<template>
  <Page title="角色管理" description="角色维护与菜单授权，角色按部门生效（sys_user_role.dept_id）">
    <Card>
      <div class="mb-3 flex items-center gap-2">
        <Input
          v-model:value="查询.name"
          allow-clear
          placeholder="编码 / 名称"
          style="width: 220px"
          @press-enter="搜索"
        />
        <Select
          v-model:value="查询.status"
          allow-clear
          placeholder="状态"
          style="width: 130px"
          :options="[
            { label: '启用', value: 'ACTIVE' },
            { label: '停用', value: 'DISABLED' },
          ]"
        />
        <Button type="primary" @click="搜索">查询</Button>
        <Button v-if="可维护" type="primary" @click="打开新增">新增角色</Button>
      </div>

      <Table
        :columns="列定义"
        :data-source="数据"
        :loading="加载中"
        :pagination="{
          total: 总数,
          current: 查询.page,
          pageSize: 查询.pageSize,
          showSizeChanger: false,
        }"
        row-key="id"
        size="small"
        @change="(分页: any) => { 查询.page = 分页.current; 加载列表(); }"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.dataIndex === 'isSystem'">
            <Tag v-if="record.isSystem === 1" color="blue">内置</Tag>
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
              <Button size="small" type="link" @click="打开授权(record)">菜单授权</Button>
              <Button v-if="可维护" size="small" type="link" @click="打开编辑(record)">
                编辑
              </Button>
              <Button v-if="可维护" size="small" type="link" @click="切换状态(record)">
                {{ record.status === 'ACTIVE' ? '停用' : '启用' }}
              </Button>
              <Popconfirm
                v-if="可维护 && record.isSystem !== 1"
                title="确认删除该角色？"
                @confirm="删除(record)"
              >
                <Button danger size="small" type="link">删除</Button>
              </Popconfirm>
            </Space>
          </template>
        </template>
      </Table>
    </Card>

    <Modal
      v-model:open="编辑弹窗"
      :confirm-loading="提交中"
      :title="编辑中ID ? '编辑角色' : '新增角色'"
      @ok="保存"
    >
      <Form layout="vertical">
        <FormItem label="角色编码" required>
          <Input
            v-model:value="表单.roleCode"
            :disabled="编码只读"
            placeholder="如 MIT_LEADER（大写下划线）"
          />
        </FormItem>
        <FormItem label="角色名称" required>
          <Input v-model:value="表单.roleName" placeholder="如 中试研发负责人" />
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

    <!-- 菜单授权抽屉：树形勾选，保存时后端自动补全父节点 -->
    <Drawer />
  </Page>
</template>
