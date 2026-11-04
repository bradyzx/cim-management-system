<script lang="ts" setup>
/**
 * 缺陷与故障。
 *
 * 需要一次查两种类型（BUG + INC），而当前列表接口的 ticketType 只支持单值。
 * 要么后端支持逗号分隔的多类型，要么本页做两个 Tab 各查一次。
 * 定下来之前先用需求列表的筛选看，不在本页放半成品。
 */
import { Page } from '@vben/common-ui';
import { Card, List, ListItem, ListItemMeta } from 'ant-design-vue';

const 待实现要点 = [
  '后端 ticketType 支持多值，或本页拆「缺陷 / 故障」两个 Tab',
  '故障单要显著标出「关联需求」与「是否已关闭」，未关闭会让上游需求暂停计时',
  '缺陷单要带上游需求单号，按需求聚合看缺陷密度',
];
</script>

<template>
  <Page description="缺陷与故障单" title="缺陷与故障">
    <Card title="待接入多类型查询">
      <List :data-source="待实现要点">
        <template #renderItem="{ item }">
          <ListItem>
            <ListItemMeta :title="item" />
          </ListItem>
        </template>
      </List>
    </Card>
  </Page>
</template>
