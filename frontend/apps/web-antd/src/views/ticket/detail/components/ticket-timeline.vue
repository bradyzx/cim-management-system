<script lang="ts" setup>
/**
 * 详情页·流转时间线。
 *
 * 倒序展示：用户看这张单时最关心「最近一次发生了什么、为什么退回」，
 * 正序会让他每次都要滚到底部。审计要正序的场景由导出报表承担。
 *
 * 时间线只读，内容全部来自 req_flow_log，不允许前端拼装或补全。
 */
import type { TicketTimelineItem } from '#/api/ticket';

import { computed } from 'vue';

import { Empty, Tag, Timeline, TimelineItem } from 'ant-design-vue';

const props = defineProps<{ items: TicketTimelineItem[] }>();

/** 倒序副本，不改动父组件传进来的数组 */
const 时间线 = computed(() => [...props.items].reverse());

/**
 * 时间点颜色：退回标红、系统动作灰、正常推进蓝。
 *
 * @param 条 时间线条目
 * @returns antd Timeline 的颜色名
 */
function 点颜色(条: TicketTimelineItem): string {
  if (条.reasonCode) {
    return 'red';
  }
  if (条.operatorName === '系统') {
    return 'gray';
  }
  return 'blue';
}

/**
 * 只保留到分钟。秒对审批留痕没有意义，全写出来反而让一行变长。
 *
 * @param 时间 后端时间串
 * @returns 截断后的时间
 */
function 短时间(时间?: null | string): string {
  return 时间 ? 时间.slice(0, 16) : '-';
}

/**
 * 状态名后面补上经办人：`设计中(李四)`。
 *
 * 光看「设计中 → 设计评审中」不知道谁在设计、谁接着评审，补上名字才读得通。
 * 没有名字（单据刚进这一步、还没人动手）就只给状态名，不留一对空括号。
 *
 * @param 状态 状态中文名
 * @param 人名 该状态的经办人，可为空
 * @returns 带括号的展示文本
 */
function 带人名(状态: string, 人名?: null | string): string {
  return 人名 ? `${状态}(${人名})` : 状态;
}
</script>

<template>
  <Empty v-if="时间线.length === 0" description="暂无流转记录" />
  <!-- 不做内部滚动：卡片里再套滚动区，滚一点顶部就被容器裁半，
       看起来像文字被遮（用户实际报过）。时间线随页面自然展开，
       22 条约 1000px，页面本身就在滚动，多这一段没有成本。 -->
  <Timeline v-else class="mt-2">
    <TimelineItem v-for="条 in 时间线" :key="条.id" :color="点颜色(条)">
      <div class="text-xs text-gray-500 dark:text-gray-400">
        {{ 短时间(条.createdAt) }} · {{ 条.operatorName || '系统' }}
      </div>
      <div class="mt-1 flex flex-wrap items-center gap-2 text-sm">
        <span class="font-medium">{{ 条.actionLabel }}</span>
        <span class="text-gray-500 dark:text-gray-400">
          {{ 带人名(条.fromStatusLabel || '—', 条.fromOwner) }} →
          {{ 带人名(条.toStatusLabel, 条.toOwner) }}
        </span>
        <Tag v-if="条.returnSeq" color="orange">第 {{ 条.returnSeq }} 次退回</Tag>
        <Tag v-if="条.reasonLabel" color="red">{{ 条.reasonLabel }}</Tag>
      </div>
      <div
        v-if="条.remark"
        class="mt-1 text-xs leading-5 whitespace-pre-wrap text-gray-600 dark:text-gray-300"
      >
        {{ 条.remark }}
      </div>
    </TimelineItem>
  </Timeline>
</template>
