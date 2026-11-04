<script lang="ts" setup>
// 菜单管理：目录 / 菜单 / 按钮 / 内嵌 / 外链 五种节点的增删改。接口 /system/menu/*。
// 列表用 /all（平铺全量，含停用），前端组树；/list 是运行视角路由树，没有 ID，不能用来维护。
// 类型决定表单字段：按钮只要权限码，外链要 URL，目录/菜单要路由与组件路径。
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
  InputNumber,
  Modal,
  Popconfirm,
  Select,
  Space,
  Switch,
  Table,
  Tag,
  message,
} from 'ant-design-vue';

const { hasAccessByCodes } = useAccess();
const 可维护 = hasAccessByCodes(['system:menu:edit']);

interface MenuRow {
  authCode?: null | string;
  children?: MenuRow[];
  component?: null | string;
  icon?: null | string;
  id?: string;
  keepAlive?: number;
  linkUrl?: null | string;
  menuName?: string;
  menuType?: string;
  parentId?: null | string;
  path?: null | string;
  sort?: number;
  status?: string;
  visible?: number;
}

const 类型文本: Record<string, string> = {
  DIR: '目录',
  MENU: '菜单',
  BUTTON: '按钮',
  EMBED: '内嵌',
  LINK: '外链',
};
const 类型颜色: Record<string, string> = {
  DIR: 'purple',
  MENU: 'blue',
  BUTTON: 'orange',
  EMBED: 'cyan',
  LINK: 'green',
};

const 加载中 = ref(false);
const 树数据 = ref<MenuRow[]>([]);
const 平铺数据 = ref<MenuRow[]>([]);
const 关键字 = ref('');

const 列定义 = [
  { dataIndex: 'menuName', title: '菜单名称', width: 240 },
  { dataIndex: 'menuType', title: '类型', width: 90 },
  { dataIndex: 'path', title: '路由路径', width: 180 },
  { dataIndex: 'component', title: '组件', width: 180 },
  { dataIndex: 'authCode', title: '权限码', width: 160 },
  { dataIndex: 'sort', title: '排序', width: 70 },
  { dataIndex: 'visible', title: '可见', width: 70 },
  { dataIndex: 'status', title: '状态', width: 80 },
  { dataIndex: 'actions', title: '操作', width: 190 },
];

/** 平铺 → 树。孤儿节点挂到根，避免父节点被删后整条分支消失。 */
function 组树(平铺: MenuRow[]): MenuRow[] {
  const 根: MenuRow[] = [];
  const 按ID = new Map<string, MenuRow>();
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
    const 结果 = await requestClient.get<MenuRow[]>('/system/menu/all', { params: 参数 });
    平铺数据.value = 结果;
    树数据.value = 组树(结果);
  } finally {
    加载中.value = false;
  }
}

/** 上级菜单下拉：只列目录与菜单，按钮不能当父级。 */
const 父级选项 = computed(() =>
  平铺数据.value
    .filter((m) => m.menuType === 'DIR' || m.menuType === 'MENU')
    .map((m) => ({ label: `${m.menuName}（${类型文本[m.menuType ?? ''] ?? ''}）`, value: m.id ?? '' })),
);

// ---------------- 新增 / 编辑 ----------------

const 弹窗 = ref(false);
const 编辑中ID = ref<null | string>(null);
const 提交中 = ref(false);
const 表单 = reactive({
  menuType: 'MENU',
  menuName: '',
  parentId: undefined as string | undefined,
  path: '',
  component: '',
  authCode: '',
  icon: '',
  linkUrl: '',
  sort: 0,
  visible: true,
  keepAlive: true,
  status: 'ACTIVE',
});

// 字段显隐跟着类型走：按钮不要路由，外链要 URL，其余要路由+组件
const 需要路由 = computed(() => 表单.menuType !== 'BUTTON');
const 需要组件 = computed(() => 表单.menuType === 'MENU');
const 需要外链 = computed(() => 表单.menuType === 'LINK');
const 需要权限码 = computed(
  () => 表单.menuType === 'BUTTON' || 表单.menuType === 'MENU',
);

function 打开新增(父?: MenuRow) {
  编辑中ID.value = null;
  Object.assign(表单, {
    // 在目录下新增默认建子菜单，在菜单下新增默认建按钮
    menuType: 父?.menuType === 'MENU' ? 'BUTTON' : 'MENU',
    menuName: '',
    parentId: 父?.id,
    path: '',
    component: '',
    authCode: '',
    icon: '',
    linkUrl: '',
    sort: 0,
    visible: true,
    keepAlive: true,
    status: 'ACTIVE',
  });
  弹窗.value = true;
}

function 打开编辑(行: MenuRow) {
  编辑中ID.value = 行.id ?? null;
  Object.assign(表单, {
    menuType: 行.menuType ?? 'MENU',
    menuName: 行.menuName ?? '',
    parentId: 行.parentId ?? undefined,
    path: 行.path ?? '',
    component: 行.component ?? '',
    authCode: 行.authCode ?? '',
    icon: 行.icon ?? '',
    linkUrl: 行.linkUrl ?? '',
    sort: 行.sort ?? 0,
    visible: 行.visible === 1,
    keepAlive: 行.keepAlive === 1,
    status: 行.status ?? 'ACTIVE',
  });
  弹窗.value = true;
}

async function 保存() {
  if (!表单.menuName.trim()) {
    message.warning('菜单名称必填');
    return;
  }
  if (需要路由.value && !表单.path.trim()) {
    message.warning('该类型必须填写路由路径');
    return;
  }
  const 提交体 = {
    menuType: 表单.menuType,
    menuName: 表单.menuName,
    parentId: 编辑中ID.value ? undefined : 表单.parentId,
    path: 需要路由.value ? 表单.path : '',
    component: 需要组件.value ? 表单.component : '',
    authCode: 需要权限码.value ? 表单.authCode : '',
    icon: 表单.icon,
    linkUrl: 需要外链.value ? 表单.linkUrl : '',
    sort: 表单.sort,
    visible: 表单.visible ? 1 : 0,
    keepAlive: 表单.keepAlive ? 1 : 0,
    status: 表单.status,
  };
  提交中.value = true;
  try {
    if (编辑中ID.value) {
      await requestClient.put(`/system/menu/${编辑中ID.value}`, 提交体);
    } else {
      await requestClient.post('/system/menu', 提交体);
    }
    message.success('保存成功');
    弹窗.value = false;
    加载列表();
  } finally {
    提交中.value = false;
  }
}

async function 删除(行: MenuRow) {
  await requestClient.delete(`/system/menu/${行.id}`);
  message.success('已删除');
  加载列表();
}

onMounted(() => {
  加载列表();
});
</script>

<template>
  <Page
    title="菜单管理"
    description="目录 / 菜单 / 按钮 / 内嵌 / 外链维护；删除菜单会同步清理角色授权"
  >
    <Card>
      <div class="mb-3 flex items-center gap-2">
        <Input
          v-model:value="关键字"
          allow-clear
          placeholder="菜单名称 / 路径"
          style="width: 220px"
          @press-enter="加载列表"
        />
        <Button type="primary" @click="加载列表">查询</Button>
        <Button v-if="可维护" type="primary" @click="打开新增()">新增菜单</Button>
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
          <template v-if="column.dataIndex === 'menuType'">
            <Tag :color="类型颜色[record.menuType]">
              {{ 类型文本[record.menuType] ?? record.menuType }}
            </Tag>
          </template>
          <template v-else-if="column.dataIndex === 'path'">
            {{ record.path || '-' }}
          </template>
          <template v-else-if="column.dataIndex === 'component'">
            {{ record.component || '-' }}
          </template>
          <template v-else-if="column.dataIndex === 'authCode'">
            {{ record.authCode || '-' }}
          </template>
          <template v-else-if="column.dataIndex === 'visible'">
            {{ record.visible === 1 ? '显示' : '隐藏' }}
          </template>
          <template v-else-if="column.dataIndex === 'status'">
            <Tag :color="record.status === 'ACTIVE' ? 'success' : 'default'">
              {{ record.status === 'ACTIVE' ? '启用' : '停用' }}
            </Tag>
          </template>
          <template v-else-if="column.dataIndex === 'actions'">
            <Space>
              <Button v-if="可维护" size="small" type="link" @click="打开新增(record)">
                加子级
              </Button>
              <Button v-if="可维护" size="small" type="link" @click="打开编辑(record)">
                编辑
              </Button>
              <Popconfirm title="确认删除该菜单？" @confirm="删除(record)">
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
      :title="编辑中ID ? '编辑菜单' : '新增菜单'"
      @ok="保存"
    >
      <Form layout="vertical">
        <FormItem label="类型" required>
          <!-- 类型决定后续字段，建好后不能改：改类型等于换一种节点语义 -->
          <Select
            v-model:value="表单.menuType"
            :disabled="!!编辑中ID"
            :options="Object.entries(类型文本).map(([值, 文本]) => ({ label: 文本, value: 值 }))"
          />
        </FormItem>
        <FormItem label="上级菜单">
          <Select
            v-model:value="表单.parentId"
            allow-clear
            :disabled="!!编辑中ID"
            placeholder="不选则为顶级"
            :options="父级选项"
          />
        </FormItem>
        <FormItem label="名称" required>
          <Input v-model:value="表单.menuName" placeholder="侧边栏显示的名称" />
        </FormItem>
        <FormItem v-if="需要路由" label="路由路径" required>
          <Input v-model:value="表单.path" placeholder="如 /system/user" />
        </FormItem>
        <FormItem v-if="需要组件" label="组件路径">
          <Input v-model:value="表单.component" placeholder="如 system/user/index" />
        </FormItem>
        <FormItem v-if="需要外链" label="外链地址">
          <Input v-model:value="表单.linkUrl" placeholder="https://..." />
        </FormItem>
        <FormItem v-if="需要权限码" label="权限码">
          <Input v-model:value="表单.authCode" placeholder="如 system:user:edit" />
        </FormItem>
        <FormItem label="图标">
          <Input v-model:value="表单.icon" placeholder="如 lucide:users" />
        </FormItem>
        <FormItem label="排序">
          <InputNumber v-model:value="表单.sort" :min="0" style="width: 100%" />
        </FormItem>
        <FormItem label="显示与缓存">
          <Space size="large">
            <span>侧边栏显示 <Switch v-model:checked="表单.visible" /></span>
            <span v-if="需要组件">页面缓存 <Switch v-model:checked="表单.keepAlive" /></span>
          </Space>
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
      </Form>
    </Modal>
  </Page>
</template>
