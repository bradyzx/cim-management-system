<script lang="ts" setup>
/**
 * 需求列表：REQ / BUG / INC 全类型单据综合查询。
 *
 * 主体是共享的 TicketTable，本页只负责标题与说明。
 * 可见范围由后端按角色算（03 4.2.3），前端不传、也传不了范围参数。
 */
import { Page } from '@vben/common-ui';

import TicketTable from '../components/ticket-table.vue';
</script>

<template>
  <Page description="REQ / BUG / INC 全类型单据综合查询" title="需求列表">
    <TicketTable show-create />
  </Page>
</template>
