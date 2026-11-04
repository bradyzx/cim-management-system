<script lang="ts" setup>
// 角色-菜单授权抽屉：树形勾选 + 保存。
// 菜单节点来自 /system/menu/nodes（平铺、带 ID，前端组树）；
// 已授权列表来自 /system/role/{id}/menu-ids；保存走全删全插。
import { computed, ref } from 'vue';

import { useVbenDrawer } from '@vben/common-ui';
import { requestClient } from '#/api/request';
import { message, Tree } from 'ant-design-vue';

/**
 * 树节点。key / title 是 antd Tree 的默认字段名：
 * key 必须等于菜单 ID（授权存储的键），title 组装了类型标记便于区分按钮节点。
 */
interface MenuNode {
  authCode?: null | string;
  children?: MenuNode[];
  id: string;
  key: string;
  menuName: string;
  menuType: string;
  parentId?: null | string;
  status?: string;
  title: string;
}

const 树数据 = ref<MenuNode[]>([]);
const 勾选 = ref<string[]>([]);
const 角色ID = ref('');
const 角色名 = ref('');
const 保存中 = ref(false);

const [Drawer, drawerApi] = useVbenDrawer({
  title: '菜单授权',
  confirmText: '保存授权',
  class: 'w-[420px]',
  onConfirm: 保存,
  onOpenChange(isOpen) {
    if (isOpen) {
      const 数据 = drawerApi.getData() as { roleId: string; roleName: string };
      角色ID.value = 数据.roleId;
      角色名.value = 数据.roleName;
      勾选.value = [];
      加载授权数据();
    }
  },
});

/** 平铺节点按 parentId 组树；孤儿节点（父已删）挂到根，避免丢失。 */
function 组树(节点列表: MenuNode[]): MenuNode[] {
  const 根: MenuNode[] = [];
  const 按ID = new Map<string, MenuNode>();
  for (const 节点 of 节点列表) {
    节点.children = [];
    节点.key = 节点.id;
    // 按钮节点在标题里带标记，避免和页面菜单混淆
    节点.title =
      节点.menuType === 'BUTTON' ? `${节点.menuName}（按钮）` : 节点.menuName;
    按ID.set(节点.id, 节点);
  }
  for (const 节点 of 节点列表) {
    const 父 = 节点.parentId ? 按ID.get(节点.parentId) : undefined;
    if (父 && 父 !== 节点) {
      父.children!.push(节点);
    } else {
      根.push(节点);
    }
  }
  return 根;
}

async function 加载授权数据() {
  // 菜单节点和已授权 ID 没有依赖关系，并行请求省一次往返
  const [节点列表, 已授权] = await Promise.all([
    requestClient.get<MenuNode[]>('/system/menu/nodes'),
    requestClient.get<string[]>(`/system/role/${角色ID.value}/menu-ids`),
  ]);
  树数据.value = 组树(节点列表);
  勾选.value = 已授权;
}

async function 保存() {
  保存中.value = true;
  try {
    await requestClient.put(`/system/role/${角色ID.value}/menus`, {
      menuIds: 勾选.value,
    });
    message.success('授权已保存');
    drawerApi.close();
  } finally {
    保存中.value = false;
  }
}

const 抽屉标题 = computed(() => `菜单授权 · ${角色名.value || ''}`);
</script>

<template>
  <Drawer :title="抽屉标题">
    <div class="h-full overflow-auto">
      <Tree
        v-model:checkedKeys="勾选"
        :tree-data="树数据"
        block-node
        checkable
        default-expand-all
      />
    </div>
  </Drawer>
</template>
