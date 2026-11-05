<script lang="ts" setup>
/**
 * 详情页·并行确认进度条。
 *
 * v0.7 并行化后，三方确认（MIT / 平台 / 上级）与设计评审（IT / 平台 / 用户）
 * 不分先后，单据停在 CONFIRMING / DSGN_REVIEWING 时，各方各自确认、各自置位。
 * 本组件把各方的通过状态摆出来：已过的打绿勾，未过的灰显，让每个人
 * 一眼看到「还差谁」——这正是单据停留在此环节时最想回答的问题。
 */
import { computed } from 'vue';

/** 一方的展示状态 */
interface 参与方 {
  /** 方名，如 MIT / 平台 / 上级 */
  名称: string;
  /** 是否已通过 */
  已通过: boolean;
}

const props = defineProps<{
  /** 单据当前状态码 */
  status: string;
  /** 各方通过标志，缺省视为未通过 */
  mitPassed?: null | number;
  platformPassed?: null | number;
  superiorPassed?: null | number;
  dsgnItPassed?: null | number;
  dsgnPlatformPassed?: null | number;
  dsgnUserPassed?: null | number;
}>();

/** 标志值转布尔：后端用 0/1，历史行可能是 null */
function 置位(flag?: null | number): boolean {
  return flag === 1;
}

/** 三方确认阶段的参与方；不在该阶段时为空 */
const 确认方 = computed<参与方[]>(() => {
  if (props.status !== 'CONFIRMING') {
    return [];
  }
  return [
    { 名称: 'MIT', 已通过: 置位(props.mitPassed) },
    { 名称: '平台', 已通过: 置位(props.platformPassed) },
    { 名称: '上级', 已通过: 置位(props.superiorPassed) },
  ];
});

/** 设计评审阶段的参与方；不在该阶段时为空 */
const 评审方 = computed<参与方[]>(() => {
  if (props.status !== 'DSGN_REVIEWING') {
    return [];
  }
  return [
    { 名称: 'IT', 已通过: 置位(props.dsgnItPassed) },
    { 名称: '平台', 已通过: 置位(props.dsgnPlatformPassed) },
    { 名称: '用户', 已通过: 置位(props.dsgnUserPassed) },
  ];
});

const 参与方列表 = computed(() =>
  确认方.value.length > 0 ? 确认方.value : 评审方.value,
);

const 已过数量 = computed(
  () => 参与方列表.value.filter((参与方) => 参与方.已通过).length,
);
</script>

<template>
  <div v-if="参与方列表.length > 0" class="flex flex-wrap items-center gap-3">
    <span class="text-xs text-gray-500 dark:text-gray-400">
      {{ 已过数量 }}/{{ 参与方列表.length }} 已确认：
    </span>
    <template v-for="参与方 in 参与方列表" :key="参与方.名称">
      <span
        class="inline-flex items-center gap-1 rounded-full border px-2.5 py-0.5 text-xs"
        :class="
          参与方.已通过
            ? 'border-green-300 bg-green-50 text-green-700 dark:border-green-700 dark:bg-green-900/30 dark:text-green-300'
            : 'border-gray-300 bg-white text-gray-400 dark:border-gray-600 dark:bg-transparent dark:text-gray-500'
        "
      >
        <span v-if="参与方.已通过">✓</span>
        {{ 参与方.名称 }}{{ 参与方.已通过 ? ' 已确认' : ' 待确认' }}
      </span>
    </template>
  </div>
</template>
