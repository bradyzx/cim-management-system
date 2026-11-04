<script lang="ts" setup>
/**
 * 详情页·需求内容。
 *
 * As Is / To Be 并排展示：这两个字段是评审时唯一必须逐字读的内容，
 * 竖着排会让人来回滚动，横着放才能一眼比对差异。
 * 验收标准单独一段并标注是否已锁定 —— 锁定后修改要走变更流程，必须显眼。
 */
import type { TicketDetail } from '#/api/ticket';

import { computed } from 'vue';

import { Col, Row, Tag } from 'ant-design-vue';

const props = defineProps<{ detail: TicketDetail }>();

/** 是否锁定验收标准 */
const 已锁定 = computed(() => props.detail.acceptanceLocked === 1);

/** 是否有监控结论 */
const 有监控结论 = computed(() => Boolean(props.detail.monitorNote));
</script>

<template>
  <div class="space-y-4">
    <div v-if="detail.description">
      <div class="mb-1 text-xs font-medium text-gray-500 dark:text-gray-400">需求简述</div>
      <div class="whitespace-pre-wrap text-sm leading-6">{{ detail.description }}</div>
    </div>

    <Row :gutter="12">
      <Col :xs="24" :md="12">
        <div class="mb-1 text-xs font-medium text-gray-500 dark:text-gray-400">现状 As Is</div>
        <div
          class="min-h-20 rounded border border-gray-200 bg-gray-50 p-3 text-sm leading-6 whitespace-pre-wrap dark:border-gray-700 dark:bg-gray-800"
        >
          {{ detail.contentAsis || '未填写' }}
        </div>
      </Col>
      <Col :xs="24" :md="12">
        <div class="mb-1 text-xs font-medium text-gray-500 dark:text-gray-400">期望 To Be</div>
        <div
          class="min-h-20 rounded border border-blue-200 bg-blue-50 p-3 text-sm leading-6 whitespace-pre-wrap dark:border-blue-900 dark:bg-blue-950"
        >
          {{ detail.contentTobe || '未填写' }}
        </div>
      </Col>
    </Row>

    <div>
      <div class="mb-1 flex items-center gap-2">
        <span class="text-xs font-medium text-gray-500 dark:text-gray-400">验收标准</span>
        <Tag :color="已锁定 ? 'success' : 'default'">
          {{ 已锁定 ? '已锁定，变更需走流程' : '未锁定，设计确认后锁定' }}
        </Tag>
      </div>
      <div
        class="rounded border border-gray-200 p-3 text-sm leading-6 whitespace-pre-wrap dark:border-gray-700"
      >
        {{ detail.acceptanceCriteria || '暂未填写验收标准' }}
      </div>
    </div>

    <div v-if="有监控结论">
      <div class="mb-1 text-xs font-medium text-gray-500 dark:text-gray-400">运行监控结论</div>
      <div
        class="rounded border border-gray-200 p-3 text-sm leading-6 whitespace-pre-wrap dark:border-gray-700"
      >
        {{ detail.monitorNote }}
      </div>
    </div>
  </div>
</template>
