<script lang="ts" setup>
// 看板与统计：全状态聚合报表（03 文档 6.1 节）。
// 报表口径（09 清单）：等待时间 / 处理时间拆分；满意度分母只算已评价，未评价单列一行。
// 骨架占位：接口为 /api/report/*，随报表模块开发填充。
import { Page } from '@vben/common-ui';
import { Card, List, ListItem, ListItemMeta } from 'ant-design-vue';

const 规划要点 = [
  '状态分布：漏斗图，从提交到关闭逐环节单量',
  '效率分析：各环节等待 / 处理时间拆分（6 个节点时间戳相减）',
  'SLA 超期：按工作日历折算的超期单清单（SlaCalculator 口径）',
  '满意度分布：分母只算已评价，「未评价」单独一行',
];
</script>

<template>
  <Page title="看板与统计" description="全状态聚合报表">
    <Card>
      <template #title>骨架占位 · 业务功能开发中</template>
      <List :data-source="规划要点">
        <template #renderItem="{ item }">
          <ListItem>
            <ListItemMeta :title="item" />
          </ListItem>
        </template>
      </List>
    </Card>
  </Page>
</template>
