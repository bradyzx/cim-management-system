<script lang="ts" setup>
/**
 * 详情页·主链步骤条。
 *
 * 展示规则（用户 2026-11-04 拍板）：
 * - 主链节点从左到右横排在详情最上方；
 * - 已经过的节点在**右下角**打 ✔ 角标；
 * - 未到的节点整列灰色；
 * - 纯审批环节不单列节点：三方确认合并为「需求确认」，需求评审、
 *   设计评审、IT 点检这些"停留即审批"的状态归入其所属主链节点，
 *   否则 28 个节点全铺开就是一排挤不下的小字。
 *
 * 状态 → 主链的映射一张表说了算。新增状态时先想清楚它属于哪一步，
 * 不在表里的状态会退回第一步并在控制台警告，不会静默乱标。
 */
import { computed } from 'vue';

/** 主链节点定义 */
interface 主链节点 {
  key: string;
  label: string;
  /** 归入本节点的状态码（含该环节的审批/等待态） */
  states: string[];
}

/** 需求主链。顺序即流转顺序，改这里即可调整步骤条 */
const 主链: 主链节点[] = [
  {
    key: 'PROPOSE',
    label: '提出需求',
    states: ['DRAFT', 'SUBMITTED', 'AI_ANALYZING', 'AI_CLARIFYING', 'REQ_DOC_DRAFTED'],
  },
  // 三方确认（MIT / 平台 / 上级）在流程上是审批，展示上合并为一步；
  // v0.7 并行化后三方不分先后，合并后的状态码是 CONFIRMING
  {
    key: 'CONFIRM',
    label: '需求确认',
    states: ['CONFIRMING'],
  },
  { key: 'ASSIGN', label: '派单', states: ['PENDING_ASSIGN', 'ASSIGNED'] },
  // 设计评审三个环节都算「设计还在确认」，不单列；并行化后状态码是 DSGN_REVIEWING
  {
    key: 'DESIGN',
    label: '设计',
    states: ['DESIGNING', 'DSGN_REVIEWING'],
  },
  { key: 'DEV', label: '开发', states: ['DEVELOPING', 'SELF_TEST'] },
  // 部署是 UAT 的前置动作，合并展示
  { key: 'UAT', label: 'UAT 验收', states: ['DEPLOYING', 'UAT'] },
  // 待上线审批归入「上线」——上线审批本身不单列
  { key: 'RELEASE', label: '上线', states: ['RELEASE_PENDING', 'RELEASED'] },
  // 运行监控与 IT 点检是一段验收期，合并为「运行」
  { key: 'RUN', label: '运行', states: ['MONITORING', 'IT_INSPECTION'] },
  { key: 'EVAL', label: '评价', states: ['EVALUATING'] },
  { key: 'CLOSE', label: '关闭', states: ['CLOSED'] },
];

const props = defineProps<{
  /** 单据当前状态码 */
  status: string;
  /** 挂起前的状态码，挂起时步骤条要停回原节点 */
  suspendedFrom?: null | string;
}>();

/** 是否处于挂起（含挂起待评审） */
const 挂起中 = computed(
  () => props.status === 'SUSPENDED' || props.status === 'SUSPENDED_REVIEW',
);

/** 当前停在哪一步。找不到时退回第一步并警告，避免静默标错 */
const 当前步骤 = computed(() => {
  if (挂起中.value && props.suspendedFrom) {
    for (const [序号, 节点] of 主链.entries()) {
      if (节点.states.includes(props.suspendedFrom)) {
        return 序号;
      }
    }
  }
  for (const [序号, 节点] of 主链.entries()) {
    if (节点.states.includes(props.status)) {
      return 序号;
    }
  }
  console.warn(`[ticket-steps] 状态 ${props.status} 未映射到主链，步骤条停在第一步`);
  return 0;
});

/** 全部走完（已关闭）时，最后一步也打勾 */
const 全部完成 = computed(() => props.status === 'CLOSED');

/**
 * 节点与 label 的样式。完成 / 当前 / 未来三态，挂起时当前节点标红。
 *
 * @param 序号 节点序号
 * @return 各部位的样式类
 */
function 节点样式(序号: number) {
  const 完成 = 序号 < 当前步骤.value || (全部完成.value && 序号 === 当前步骤.value);
  const 是当前 = 序号 === 当前步骤.value && !全部完成.value;
  return {
    圆: 完成
      ? 'border-blue-500 bg-blue-500 text-white'
      : 是当前
        ? 挂起中.value
          ? 'border-red-500 bg-white text-red-500 ring-2 ring-red-200'
          : 'border-blue-500 bg-white text-blue-600 ring-2 ring-blue-200'
        : 'border-gray-300 bg-white text-gray-400',
    角标: 完成 ? '' : 'hidden',
    文字: 是当前
      ? 挂起中.value
        ? 'text-red-500 font-medium'
        : 'text-blue-600 font-medium'
      : 完成
        ? 'text-gray-700 dark:text-gray-200'
        : 'text-gray-400',
    连线: 序号 < 当前步骤.value ? 'bg-blue-500' : 'bg-gray-200 dark:bg-gray-700',
  };
}
</script>

<template>
  <!-- 窄屏时允许横向滚动：10 个节点压成小字比折成两行好认 -->
  <div class="overflow-x-auto pb-1">
    <div class="flex min-w-[720px] items-start">
      <template v-for="(节点, 序号) in 主链" :key="节点.key">
        <div class="flex w-20 shrink-0 flex-col items-center">
          <div class="relative">
            <div
              class="flex h-7 w-7 items-center justify-center rounded-full border text-xs transition-colors"
              :class="节点样式(序号).圆"
            >
              {{ 序号 + 1 }}
            </div>
            <!-- 右下角完成角标：绿底白勾，只出现在已走过的节点上 -->
            <span
              class="absolute -bottom-0.5 -right-1 flex h-3.5 w-3.5 items-center justify-center rounded-full border border-white bg-green-500 text-[9px] leading-none text-white"
              :class="节点样式(序号).角标"
            >
              ✓
            </span>
          </div>
          <span class="mt-1.5 whitespace-nowrap text-center text-xs" :class="节点样式(序号).文字">
            {{ 节点.label }}
            <template v-if="序号 === 当前步骤 && 挂起中">（挂起）</template>
          </span>
        </div>
        <!-- 连线与圆点中心对齐：圆 28px，中心在 14px -->
        <div
          v-if="序号 < 主链.length - 1"
          class="mt-3.5 h-0.5 flex-1 rounded"
          :class="节点样式(序号).连线"
        />
      </template>
    </div>
  </div>
</template>
