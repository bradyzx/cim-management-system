<script lang="ts" setup>
/**
 * 需求池：已完成需求评审、等待派单的单据。
 *
 * 与需求列表共用 TicketTable，只是把状态锁死在「待分配」。
 * Leader 的可见范围里本来就包含「全部待分配单」，跨部门提的需求也能在这里被派出去。
 */
import { Page } from '@vben/common-ui';

import TicketTable from '../components/ticket-table.vue';
</script>

<template>
  <Page description="已完成评审、等待派单的需求" title="需求池">
    <TicketTable :fixed-query="{ status: 'PENDING_ASSIGN' }" />
  </Page>
</template>
