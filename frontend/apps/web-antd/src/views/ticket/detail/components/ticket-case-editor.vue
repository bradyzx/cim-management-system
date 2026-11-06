<script lang="ts" setup>
/**
 * 测试用例编辑抽屉。
 *
 * 步骤用「一行一步」的文本域，而不是可增删的行：写用例时人是连着想的，
 * 逐行敲比反复点「添加一行」快；反过来读的时候一行一步也清楚。
 *
 * 编号、来源验收标准、轮次都不在这里改 —— 前两个是覆盖率与 BUG 关联的引用键，
 * 改了覆盖率就能随手造假。
 */
import type { CaseSaveParams, TestCaseItem } from '#/api/ticket';

import { ref, watch } from 'vue';

import {
  Button,
  Drawer,
  Input,
  Select,
  Switch,
  message,
} from 'ant-design-vue';

import { saveTestCaseApi } from '#/api/ticket';

const props = defineProps<{
  item: null | TestCaseItem;
  open: boolean;
}>();

const emit = defineEmits<{
  saved: [];
  'update:open': [boolean];
}>();

const 保存中 = ref(false);

/** 编辑副本 */
const 草稿 = ref({
  title: '',
  precond: '',
  步骤文本: '',
  expected: '',
  priority: 'P1',
  focusFlag: 0,
  focusReason: '',
});

const 优先级选项 = ['P0', 'P1', 'P2'].map((值) => ({ label: 值, value: 值 }));

/**
 * 把用例拷进草稿。步骤数组拼成多行文本。
 */
function 重置草稿() {
  const 源 = props.item;
  草稿.value = {
    title: 源?.title ?? '',
    precond: 源?.precond ?? '',
    步骤文本: (源?.steps ?? []).join('\n'),
    expected: 源?.expected ?? '',
    priority: 源?.priority ?? 'P1',
    focusFlag: 源?.focusFlag ?? 0,
    focusReason: 源?.focusReason ?? '',
  };
}

/**
 * 保存。
 */
async function 保存() {
  if (!props.item) {
    return;
  }
  if (!草稿.value.title.trim()) {
    message.warning('用例标题不能为空');
    return;
  }
  if (草稿.value.focusFlag === 1 && !草稿.value.focusReason.trim()) {
    message.warning('标了重点关注就必须写清为什么，否则测试的人不知道该盯什么');
    return;
  }

  const 步骤 = 草稿.value.步骤文本
    .split('\n')
    .map((行) => 行.trim())
    .filter((行) => 行.length > 0);

  const 参数: CaseSaveParams = {
    title: 草稿.value.title.trim(),
    precond: 草稿.value.precond,
    steps: 步骤,
    expected: 草稿.value.expected,
    priority: 草稿.value.priority,
    focusFlag: 草稿.value.focusFlag,
    focusReason: 草稿.value.focusFlag === 1 ? 草稿.value.focusReason : '',
  };

  保存中.value = true;
  try {
    await saveTestCaseApi(props.item.id, 参数);
    message.success('用例已保存');
    emit('saved');
    emit('update:open', false);
  } catch {
    // 失败提示由请求层统一弹，抽屉保留让用户改完再存
  } finally {
    保存中.value = false;
  }
}

watch(
  () => props.open,
  (开) => {
    if (开) {
      重置草稿();
    }
  },
);
</script>

<template>
  <Drawer
    :open="open"
    :width="720"
    :title="item ? `编辑用例 ${item.caseNo}` : '编辑用例'"
    @update:open="emit('update:open', $event)"
  >
    <template #extra>
      <Button :loading="保存中" type="primary" @click="保存">保存</Button>
    </template>

    <div class="space-y-4">
      <div
        v-if="item"
        class="rounded bg-gray-50 px-3 py-2 text-xs text-gray-500 dark:bg-gray-800 dark:text-gray-400"
      >
        来源：第 {{ item.criteriaIndex ?? '-' }} 条验收标准
        <template v-if="item.sourceCriteria"> · {{ item.sourceCriteria }}</template>
        <div class="mt-1">
          这条用例由 {{ item.origin === 'HUMAN' ? '人工' : '规则模板' }} 生成，
          保存后来源会标为人工。
        </div>
      </div>

      <div>
        <div class="mb-1 text-sm font-medium">用例标题</div>
        <Input v-model:value="草稿.title" placeholder="能看出测什么，不写「测试功能」" />
      </div>

      <div>
        <div class="mb-1 text-sm font-medium">前置条件</div>
        <Input.TextArea
          v-model:value="草稿.precond"
          :rows="2"
          placeholder="测之前需要准备好什么数据、什么权限"
        />
      </div>

      <div>
        <div class="mb-1 text-sm font-medium">
          测试步骤
          <span class="ml-2 text-xs font-normal text-gray-400">一行一步</span>
        </div>
        <Input.TextArea
          v-model:value="草稿.步骤文本"
          :rows="5"
          placeholder="1. 准备数据…&#10;2. 执行操作…&#10;3. 核对结果…"
        />
        <div class="mt-2 flex gap-2">
          <Button
            size="small"
            @click="
              草稿.步骤文本 = [
                '准备数据：',
                '执行操作：',
                '核对结果：',
              ].join('\n')
            "
          >
            套用模板
          </Button>
        </div>
      </div>

      <div>
        <div class="mb-1 text-sm font-medium">预期结果</div>
        <Input.TextArea
          v-model:value="草稿.expected"
          :rows="2"
          placeholder="什么算通过。写「正常显示」等于没写"
        />
      </div>

      <div class="flex flex-wrap items-center gap-3">
        <span class="text-sm font-medium">优先级</span>
        <Select
          v-model:value="草稿.priority"
          :options="优先级选项"
          style="width: 100px"
        />
        <span class="ml-2 text-sm font-medium">重点关注</span>
        <Switch
          :checked="草稿.focusFlag === 1"
          @change="(值: any) => (草稿.focusFlag = 值 ? 1 : 0)"
        />
      </div>

      <div v-if="草稿.focusFlag === 1">
        <div class="mb-1 text-sm font-medium">
          为什么重点关注
          <span class="ml-2 text-xs font-normal text-orange-500">必填</span>
        </div>
        <Input.TextArea
          v-model:value="草稿.focusReason"
          :rows="2"
          placeholder="说清要盯什么，如「涉及批量写入，历史出现过并发覆盖」"
        />
      </div>
    </div>
  </Drawer>
</template>
