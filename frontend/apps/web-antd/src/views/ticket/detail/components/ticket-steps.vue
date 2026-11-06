<script lang="ts" setup>
/**
 * 详情页 · 主链步骤条。
 *
 * 展示规则（用户 2026-11-04 拍板）：
 * - 主链节点从左到右横排在详情最上方；
 * - 已经过的节点在**右下角**打 ✔ 角标；
 * - 未到的节点整列灰色；
 * - 纯审批环节不单列节点：三方确认合并为「需求确认」，需求评审并进「派单」，
 *   设计评审并进「设计」，否则 28 个节点全铺开就是一排挤不下的小字。
 *
 * 2026-11-05 追加：鼠标移到节点上显示这一步的**人**与**材料**。
 * 节点集合、进度、人名、材料全部来自 `/ticket/{id}/flow-steps`，
 * 前端不再自己维护「状态 → 第几步」的映射 —— 那份映射以前只在前端有，
 * 后端捞日志时用不了，两边各写一份必然对不上。
 */
import type { TicketFlowStep } from '#/api/ticket';

import { computed } from 'vue';

import { Popover } from 'ant-design-vue';

const props = defineProps<{
  /** 十步数据，由详情页一次取回 */
  steps: TicketFlowStep[];
  /** 单据当前状态码 */
  status: string;
  /** 挂起前的状态码，挂起时步骤条要停回原节点 */
  suspendedFrom?: null | string;
}>();

/** 是否处于挂起（含挂起待评审） */
const 挂起中 = computed(
  () => props.status === 'SUSPENDED' || props.status === 'SUSPENDED_REVIEW',
);

/** 当前停在第几步。后端已按挂起前状态定位过，这里不再重算 */
const 当前步骤 = computed(() => props.steps.findIndex((项) => 项.state === 'CURRENT'));

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

/**
 * 悬停卡片里的行。
 *
 * 「处理」与「预计」互斥：已经有人处理过就说处理，没人动过才说预计 ——
 * 两个都写会让人以为这单有两个人同时在办。
 *
 * @param 步 步骤数据
 * @return 若干「标签 + 内容」行
 */
function 悬停行(步: TicketFlowStep) {
  const 行: { 值: string; 标签: string; 缺?: boolean }[] = [];

  if (步.handlers.length > 0) {
    行.push({ 标签: '处理', 值: 步.handlers.join('、') });
  } else if (步.plannedHandlers.length > 0) {
    行.push({ 标签: '预计', 值: 步.plannedHandlers.join('、') });
  } else {
    行.push({ 标签: '处理', 值: '系统自动' });
  }

  if (步.need.length > 0) {
    行.push({ 标签: '需要', 值: 步.need.join('；') });
  }
  行.push({ 标签: '已传', 值: 步.uploaded.length > 0 ? 步.uploaded.join('；') : '——' });
  行.push({
    标签: '还缺',
    值: 步.missing.length > 0 ? 步.missing.join('、') : '无缺口',
    缺: 步.missing.length > 0,
  });
  行.push({
    标签: '交给',
    值: 步.handoff.length > 0 ? 步.handoff.join('、') : '流程终点',
  });

  return 行;
}

/**
 * 悬停卡片右上角的状态字。
 *
 * @param 步 步骤数据
 * @return 已完成 / 进行中 / 未开始
 */
function 状态字(步: TicketFlowStep) {
  if (步.state === 'DONE') {
    return '已完成';
  }
  return 步.state === 'CURRENT' ? '进行中' : '未开始';
}
</script>

<template>
  <!-- 窄屏时允许横向滚动：10 个节点压成小字比折成两行好认 -->
  <div class="overflow-x-auto pb-1">
    <div class="flex min-w-[720px] items-start">
      <template v-for="(步, 序号) in steps" :key="步.key">
        <Popover placement="bottom" trigger="hover" :mouse-enter-delay="0.15">
          <template #content>
            <div class="step-pop">
              <div class="step-pop__head">
                <span class="step-pop__title">{{ 步.label }}</span>
                <span
                  class="step-pop__state"
                  :class="`step-pop__state--${步.state.toLowerCase()}`"
                >
                  {{ 状态字(步) }}
                </span>
              </div>
              <div class="step-pop__nodes">{{ 步.nodeCodes.join(' / ') }}</div>
              <div v-for="行 in 悬停行(步)" :key="行.标签" class="step-pop__row">
                <span class="step-pop__label">{{ 行.标签 }}</span>
                <span class="step-pop__value" :class="{ 'step-pop__value--lack': 行.缺 }">
                  {{ 行.值 }}
                </span>
              </div>
            </div>
          </template>

          <!-- 光标用小手不是问号：这是可点的流程节点，不是「查看帮助」 -->
          <div class="flex w-20 shrink-0 cursor-pointer flex-col items-center">
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
            <span
              class="mt-1.5 whitespace-nowrap text-center text-xs"
              :class="节点样式(序号).文字"
            >
              {{ 步.label }}
              <template v-if="序号 === 当前步骤 && 挂起中">（挂起）</template>
            </span>
          </div>
        </Popover>

        <!-- 连线与圆点中心对齐：圆 28px，中心在 14px -->
        <div
          v-if="序号 < steps.length - 1"
          class="mt-3.5 h-0.5 flex-1 rounded"
          :class="节点样式(序号).连线"
        />
      </template>
    </div>
  </div>
</template>

<style scoped>
.step-pop {
  width: 320px;
  padding: 2px 0;
}

.step-pop__head {
  display: flex;
  align-items: baseline;
  gap: 8px;
}

.step-pop__title {
  color: #1f2328;
  font-size: 13px;
  font-weight: 600;
}

.step-pop__state {
  padding: 1px 6px;
  border-radius: 10px;
  font-size: 11px;
  line-height: 16px;
}

.step-pop__state--done {
  background: #eef6ee;
  color: #2c7a38;
}

.step-pop__state--current {
  background: #e8f3fa;
  color: #1e6e9b;
}

.step-pop__state--todo {
  background: #f1f1ec;
  color: #8a949e;
}

.step-pop__nodes {
  margin: 2px 0 6px;
  color: #8a949e;
  font-size: 11px;
}

.step-pop__row {
  display: flex;
  gap: 8px;
  padding: 3px 0;
  border-top: 1px dashed #ecece6;
}

.step-pop__label {
  flex-shrink: 0;
  width: 32px;
  color: #8a949e;
  font-size: 12px;
  line-height: 18px;
}

.step-pop__value {
  color: #1f2328;
  font-size: 12px;
  line-height: 18px;
  word-break: break-word;
}

.step-pop__value--lack {
  color: #c0553b;
}
</style>
