<script lang="ts" setup>
/**
 * 详情页·基本信息。
 *
 * 分三块：归属关系、时间与工时、当前时限。
 * 挂起与评价是「有条件才出现」的块，没发生过就不占版面 ——
 * 详情页把 20 多个字段平铺出来，用户反而找不到关键信息。
 */
import type { TicketDetail } from '#/api/ticket';

import { computed } from 'vue';

import { Descriptions, DescriptionsItem, Tag } from 'ant-design-vue';

import { 计算时限, 展示 } from '../../utils';

const props = defineProps<{ detail: TicketDetail }>();

/** 一条展示项 */
interface 信息项 {
  label: string;
  value: string;
}

/** 归属关系 */
const 归属项 = computed<信息项[]>(() => {
  const d = props.detail;
  const 系统 = [d.systemCode, d.systemName].filter(Boolean).join(' ');
  return [
    { label: '单据类型', value: 展示(d.ticketTypeLabel) },
    { label: '提出人', value: 展示(d.reporterName) },
    { label: '录入人', value: 展示(d.creatorName) },
    { label: '需求 Owner', value: 展示(d.ownerName) },
    { label: '承办人', value: 展示(d.assigneeName) },
    { label: '提出部门', value: 展示(d.deptName) },
    { label: '所属项目', value: 展示(d.projectName) },
    { label: '所属系统', value: 展示(系统) },
    { label: '模块', value: 展示(d.moduleCode) },
  ];
});

/** 时间与工时 */
const 时间项 = computed<信息项[]>(() => {
  const d = props.detail;
  return [
    { label: '提出日期', value: 展示(d.proposeDate) },
    { label: '期望完成', value: 展示(d.expectDueDate) },
    { label: '预估工时', value: d.estimatedHours ? `${d.estimatedHours} 小时` : '-' },
    { label: '实际工时', value: d.actualHours ? `${d.actualHours} 小时` : '-' },
    { label: '提交时间', value: 展示(d.submittedAt) },
    { label: '派单时间', value: 展示(d.assignedAt) },
    { label: '提交设计', value: 展示(d.designAt) },
    { label: '进入开发', value: 展示(d.devAt) },
    { label: '上线时间', value: 展示(d.releaseAt) },
  ];
});

/** 当前时限；超期标红 */
const 时限 = computed(() => 计算时限(props.detail.slaDueAt));

/** 是否有挂起信息 */
const 有挂起 = computed(
  () => Boolean(props.detail.suspendReason) || Boolean(props.detail.suspendedFrom),
);

/** 是否有评价信息 */
const 有评价 = computed(() => Boolean(props.detail.evalResult));
</script>

<template>
  <div>
    <div class="mb-1 text-xs font-medium text-gray-500 dark:text-gray-400">归属关系</div>
    <Descriptions :column="{ xl: 3, lg: 2, sm: 2, xs: 1 }" bordered size="small">
      <DescriptionsItem v-for="项 in 归属项" :key="项.label" :label="项.label">
        {{ 项.value }}
      </DescriptionsItem>
    </Descriptions>

    <div class="mt-4 mb-1 text-xs font-medium text-gray-500 dark:text-gray-400">时间与工时</div>
    <Descriptions :column="{ xl: 3, lg: 2, sm: 2, xs: 1 }" bordered size="small">
      <DescriptionsItem v-for="项 in 时间项" :key="项.label" :label="项.label">
        {{ 项.value }}
      </DescriptionsItem>
    </Descriptions>

    <div class="mt-4 mb-1 text-xs font-medium text-gray-500 dark:text-gray-400">当前时限</div>
    <Descriptions :column="{ xl: 3, lg: 2, sm: 2, xs: 1 }" bordered size="small">
      <DescriptionsItem label="应完成时间">
        <span>{{ 展示(detail.slaDueAt) }}</span>
        <Tag v-if="时限" :color="时限.超期 ? 'error' : 'processing'" class="ml-2">
          {{ 时限.文本 }}
        </Tag>
      </DescriptionsItem>
      <DescriptionsItem label="累计超期">
        <span v-if="detail.slaOverdueHours && detail.slaOverdueHours > 0" class="text-red-500">
          {{ detail.slaOverdueHours }} 小时
        </span>
        <span v-else>无</span>
      </DescriptionsItem>
      <DescriptionsItem label="累计退回">
        <Tag :color="detail.returnCount && detail.returnCount > 0 ? 'warning' : 'default'">
          {{ detail.returnCount ?? 0 }} 次
        </Tag>
      </DescriptionsItem>
    </Descriptions>

    <template v-if="有挂起">
      <div class="mt-4 mb-1 text-xs font-medium text-gray-500 dark:text-gray-400">挂起信息</div>
      <Descriptions :column="{ lg: 2, sm: 1, xs: 1 }" bordered size="small">
        <DescriptionsItem label="挂起前状态">
          {{ 展示(detail.suspendedFromLabel) }}
        </DescriptionsItem>
        <DescriptionsItem label="预计恢复">
          {{ 展示(detail.expectResumeAt) }}
        </DescriptionsItem>
        <DescriptionsItem label="挂起原因" :span="2">
          {{ 展示(detail.suspendReason) }}
        </DescriptionsItem>
      </Descriptions>
    </template>

    <template v-if="有评价">
      <div class="mt-4 mb-1 text-xs font-medium text-gray-500 dark:text-gray-400">上线评价</div>
      <Descriptions :column="{ lg: 2, sm: 1, xs: 1 }" bordered size="small">
        <DescriptionsItem label="评价结论">
          <Tag :color="detail.evalResult === 'PASS' ? 'success' : 'error'">
            {{ detail.evalResult === 'PASS' ? '符合' : '不符合' }}
          </Tag>
        </DescriptionsItem>
        <DescriptionsItem label="满意度">
          {{ detail.satisfactionScore ? `${detail.satisfactionScore} / 5` : '-' }}
        </DescriptionsItem>
        <DescriptionsItem label="评价说明" :span="2">
          {{ 展示(detail.evalComment) }}
        </DescriptionsItem>
      </Descriptions>
    </template>
  </div>
</template>
