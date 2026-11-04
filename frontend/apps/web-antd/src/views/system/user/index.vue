<script lang="ts" setup>
// 用户管理：账号增改、启停、角色分配、口令重置。
// 接口：/system/user/*。不做删除——离职只停用（status=DISABLED），历史单据上的人员引用不能断。
// 新增用户的初始口令由后端统一生成（Cim#Init2026），首次登录强制改密。
import { onMounted, reactive, ref } from 'vue';

import { Page } from '@vben/common-ui';
import { useAccess } from '@vben/access';
import { requestClient } from '#/api/request';
import {
  Button,
  Card,
  Checkbox,
  CheckboxGroup,
  Form,
  FormItem,
  Input,
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
const 可维护 = hasAccessByCodes(['system:user:edit']);

interface UserRow {
  id?: string;
  username?: string;
  realName?: string;
  email?: null | string;
  mobile?: null | string;
  employmentType?: string;
  primaryDeptId?: null | string;
  deptName?: string;
  status?: string;
  mustChangePassword?: number;
  remark?: null | string;
  lastLoginAt?: null | string;
  roles?: string[];
}

interface Option {
  label: string;
  value: string;
}

const 用工形式选项: Option[] = [
  { label: '正式员工', value: 'EMPLOYEE' },
  { label: '实习生', value: 'INTERN' },
  { label: '外包', value: 'CONTRACTOR' },
];
const 状态选项: Option[] = [
  { label: '启用', value: 'ACTIVE' },
  { label: '停用', value: 'DISABLED' },
  { label: '锁定', value: 'LOCKED' },
];

const 用工形式文本 = (值?: string) =>
  用工形式选项.find((o) => o.value === 值)?.label ?? (值 || '-');

// ---------------- 列表 ----------------

const 加载中 = ref(false);
const 数据 = ref<UserRow[]>([]);
const 总数 = ref(0);
const 查询 = reactive({ page: 1, pageSize: 20, name: '', deptId: '', status: '' });

const 部门选项 = ref<Option[]>([]);

const 列定义 = [
  { dataIndex: 'username', title: '登录账号', width: 130 },
  { dataIndex: 'realName', title: '姓名', width: 110 },
  { dataIndex: 'deptName', title: '主部门', width: 150 },
  { dataIndex: 'roles', title: '角色', width: 220 },
  { dataIndex: 'employmentType', title: '用工形式', width: 100 },
  { dataIndex: 'status', title: '状态', width: 100 },
  { dataIndex: 'lastLoginAt', title: '最近登录', width: 170 },
  { dataIndex: 'actions', title: '操作', width: 240 },
];

async function 加载列表() {
  加载中.value = true;
  try {
    const 参数: Record<string, unknown> = {
      page: 查询.page,
      pageSize: 查询.pageSize,
    };
    if (查询.name) 参数.name = 查询.name;
    if (查询.deptId) 参数.deptId = 查询.deptId;
    if (查询.status) 参数.status = 查询.status;
    const 结果 = await requestClient.get<{ items: UserRow[]; total: number }>(
      '/system/user/list',
      { params: 参数 },
    );
    数据.value = 结果.items;
    总数.value = 结果.total;
  } finally {
    加载中.value = false;
  }
}

/** 部门下拉：一次拉全量（部门是几十条量级），前端直接铺平给选择器。 */
async function 加载部门() {
  const 部门列表 = await requestClient.get<{ id: string; deptName: string }[]>(
    '/system/dept/list',
  );
  部门选项.value = 部门列表.map((d) => ({ label: d.deptName, value: d.id }));
}

function 搜索() {
  查询.page = 1;
  加载列表();
}

// ---------------- 新增 / 编辑 ----------------

const 编辑弹窗 = ref(false);
const 编辑中ID = ref<null | string>(null);
const 提交中 = ref(false);
const 表单 = reactive({
  username: '',
  realName: '',
  email: '',
  mobile: '',
  employmentType: 'EMPLOYEE',
  primaryDeptId: undefined as string | undefined,
  status: 'ACTIVE',
  remark: '',
});

function 打开新增() {
  编辑中ID.value = null;
  Object.assign(表单, {
    username: '',
    realName: '',
    email: '',
    mobile: '',
    employmentType: 'EMPLOYEE',
    primaryDeptId: undefined,
    status: 'ACTIVE',
    remark: '',
  });
  编辑弹窗.value = true;
}

function 打开编辑(行: UserRow) {
  编辑中ID.value = 行.id ?? null;
  Object.assign(表单, {
    username: 行.username ?? '',
    realName: 行.realName ?? '',
    email: 行.email ?? '',
    mobile: 行.mobile ?? '',
    employmentType: 行.employmentType ?? 'EMPLOYEE',
    primaryDeptId: 行.primaryDeptId ?? undefined,
    status: 行.status ?? 'ACTIVE',
    remark: 行.remark ?? '',
  });
  编辑弹窗.value = true;
}

async function 保存() {
  if (!表单.username.trim() || !表单.realName.trim()) {
    message.warning('登录账号和姓名必填');
    return;
  }
  提交中.value = true;
  try {
    if (编辑中ID.value) {
      await requestClient.put(`/system/user/${编辑中ID.value}`, 表单);
    } else {
      await requestClient.post('/system/user', 表单);
    }
    message.success('保存成功');
    编辑弹窗.value = false;
    加载列表();
  } finally {
    提交中.value = false;
  }
}

async function 切换状态(行: UserRow) {
  const 新状态 = 行.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE';
  await requestClient.put(`/system/user/${行.id}/status`, { status: 新状态 });
  message.success(新状态 === 'ACTIVE' ? '已启用' : '已停用');
  加载列表();
}

/** 重置后口令回到统一初始值，并把 must_change_password 置 1，本人下次登录必须改。 */
async function 重置密码(行: UserRow) {
  const 初始口令 = await requestClient.put<string>(
    `/system/user/${行.id}/password`,
  );
  message.success(`已重置为 ${初始口令}，该用户下次登录须先修改密码`, 8);
}

// ---------------- 角色分配 ----------------

const 角色弹窗 = ref(false);
const 角色加载中 = ref(false);
const 全部角色 = ref<{ id: string; roleCode: string; roleName: string }[]>([]);
const 已选角色 = ref<string[]>([]);
const 当前用户 = ref<null | UserRow>(null);

async function 打开角色分配(行: UserRow) {
  当前用户.value = 行;
  角色弹窗.value = true;
  角色加载中.value = true;
  try {
    const [角色列表, 已绑定] = await Promise.all([
      requestClient.get<{ items: typeof 全部角色.value }>('/system/role/list', {
        params: { page: 1, pageSize: 200 },
      }),
      requestClient.get<string[]>(`/system/user/${行.id}/role-ids`),
    ]);
    全部角色.value = 角色列表.items;
    已选角色.value = 已绑定;
  } finally {
    角色加载中.value = false;
  }
}

async function 保存角色() {
  提交中.value = true;
  try {
    await requestClient.put(`/system/user/${当前用户.value?.id}/roles`, {
      roleIds: 已选角色.value,
    });
    message.success('角色已更新，该用户重新登录后生效');
    角色弹窗.value = false;
    加载列表();
  } finally {
    提交中.value = false;
  }
}

onMounted(() => {
  加载列表();
  加载部门();
});
</script>

<template>
  <Page title="用户管理" description="账号、部门归属与角色分配；停用代替删除">
    <Card>
      <div class="mb-3 flex flex-wrap items-center gap-2">
        <Input
          v-model:value="查询.name"
          allow-clear
          placeholder="账号 / 姓名"
          style="width: 200px"
          @press-enter="搜索"
        />
        <Select
          v-model:value="查询.deptId"
          allow-clear
          placeholder="主部门"
          style="width: 180px"
          :options="部门选项"
        />
        <Select
          v-model:value="查询.status"
          allow-clear
          placeholder="状态"
          style="width: 130px"
          :options="状态选项"
        />
        <Button type="primary" @click="搜索">查询</Button>
        <Button v-if="可维护" type="primary" @click="打开新增">新增用户</Button>
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
          <template v-if="column.dataIndex === 'roles'">
            <Tag v-for="码 in record.roles" :key="码" color="blue">{{ 码 }}</Tag>
            <span v-if="!record.roles || record.roles.length === 0" class="opacity-50">
              未分配
            </span>
          </template>
          <template v-else-if="column.dataIndex === 'employmentType'">
            {{ 用工形式文本(record.employmentType) }}
          </template>
          <template v-else-if="column.dataIndex === 'status'">
            <Tag :color="record.status === 'ACTIVE' ? 'success' : 'default'">
              {{ record.status === 'ACTIVE' ? '启用' : record.status === 'LOCKED' ? '锁定' : '停用' }}
            </Tag>
          </template>
          <template v-else-if="column.dataIndex === 'lastLoginAt'">
            {{ record.lastLoginAt || '从未登录' }}
          </template>
          <template v-else-if="column.dataIndex === 'actions'">
            <Space>
              <Button v-if="可维护" size="small" type="link" @click="打开编辑(record)">
                编辑
              </Button>
              <Button v-if="可维护" size="small" type="link" @click="打开角色分配(record)">
                角色
              </Button>
              <Button v-if="可维护" size="small" type="link" @click="切换状态(record)">
                {{ record.status === 'ACTIVE' ? '停用' : '启用' }}
              </Button>
              <Popconfirm
                v-if="可维护"
                title="重置为统一初始口令？该用户下次登录必须改密"
                @confirm="重置密码(record)"
              >
                <Button danger size="small" type="link">重置密码</Button>
              </Popconfirm>
            </Space>
          </template>
        </template>
      </Table>
    </Card>

    <Modal
      v-model:open="编辑弹窗"
      :confirm-loading="提交中"
      :title="编辑中ID ? '编辑用户' : '新增用户'"
      @ok="保存"
    >
      <Form layout="vertical">
        <FormItem label="登录账号" required>
          <Input v-model:value="表单.username" placeholder="如 zhangsan" />
        </FormItem>
        <FormItem label="姓名" required>
          <Input v-model:value="表单.realName" />
        </FormItem>
        <FormItem label="主部门">
          <Select
            v-model:value="表单.primaryDeptId"
            allow-clear
            show-search
            :options="部门选项"
            :filter-option="(输入: string, 项?: { label?: string }) => (项?.label ?? '').includes(输入)"
          />
        </FormItem>
        <FormItem label="用工形式">
          <Select v-model:value="表单.employmentType" :options="用工形式选项" />
        </FormItem>
        <FormItem label="邮箱">
          <Input v-model:value="表单.email" />
        </FormItem>
        <FormItem label="手机">
          <Input v-model:value="表单.mobile" />
        </FormItem>
        <FormItem label="状态">
          <Select v-model:value="表单.status" :options="状态选项" />
        </FormItem>
        <FormItem label="备注">
          <Textarea v-model:value="表单.remark" :rows="2" />
        </FormItem>
        <p v-if="!编辑中ID" class="text-xs opacity-60">
          初始口令由系统统一生成，该用户首次登录必须修改密码。
        </p>
      </Form>
    </Modal>

    <Modal
      v-model:open="角色弹窗"
      :confirm-loading="提交中"
      :title="`分配角色 · ${当前用户?.realName ?? ''}`"
      @ok="保存角色"
    >
      <CheckboxGroup
        v-model:value="已选角色"
        :disabled="角色加载中"
        class="flex flex-col gap-2"
      >
        <Checkbox v-for="角色 in 全部角色" :key="角色.id" :value="角色.id">
          {{ 角色.roleName }}（{{ 角色.roleCode }}）
        </Checkbox>
      </CheckboxGroup>
      <p class="mt-2 text-xs opacity-60">
        角色绑定到该用户的主部门生效；ADMIN 为全局角色，不绑定部门。
      </p>
    </Modal>
  </Page>
</template>
