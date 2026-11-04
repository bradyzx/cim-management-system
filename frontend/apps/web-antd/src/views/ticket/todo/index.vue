<script lang="ts" setup>
/**
 * 我的待办。
 *
 * 正确口径是「当前用户在该状态至少有一个 enabled=true 的动作」，与 /actions 同一套判定，
 * 不能简单等于「我是承办人」—— 评审人、确认人、提出人都可能有待办。
 *
 * 这个口径要对每条单跑一遍动作判定，后端还需要 `GET /ticket/todo` 做批量化，
 * 一期先不实现，避免在这里放一个口径不对的近似列表。
 */
import { Page } from '@vben/common-ui';
import { Card, List, ListItem, ListItemMeta } from 'ant-design-vue';

const 待实现要点 = [
  '口径：当前用户在该单至少有一个 enabled=true 的动作（与 /actions 同源）',
  '需要后端 GET /ticket/todo 批量判定，不能在前端循环调 /actions',
  '排序按「停留时长」倒序，最久的排最前',
  '待办项要带「到期动作」提示，如「待你 MIT 确认」',
];
</script>

<template>
  <Page description="需要你处理的单据" title="我的待办">
    <Card title="待接入 GET /ticket/todo">
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
