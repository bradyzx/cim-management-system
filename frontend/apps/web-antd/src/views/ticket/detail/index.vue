<script lang="ts" setup>
/**
 * 需求单详情页（03 文档 6.1 节说的「整套系统的核心页」）。
 *
 * 五个数据源分开拉：
 * - `/detail`       单据正文与时间线，必须成功，失败则整页不可用；
 * - `/actions`      当前用户能做什么，按人算，不能缓存；
 * - `/doc`          12 节需求文档，BUG/INC 单可能没有，失败不影响其余；
 * - `/attachments`  附件列表；
 * - `/attach-rules` 本环节还缺什么材料。
 *
 * 后四个互不阻塞：任何一个接口挂了，其余内容仍然要看得到。
 * 按钮可用性只认 `/actions`，页面不自己判角色 —— 判两遍必然和真实规则对不上。
 */
import type {
  TicketActionItem,
  TicketActionParams,
  TicketAttachRuleStatus,
  TicketAttachment,
  TicketDetail,
  TicketDoc,
  TicketFlowStep,
} from '#/api/ticket';

import { computed, onMounted, ref } from 'vue';
import { useRoute } from 'vue-router';

import { Page } from '@vben/common-ui';

import {
  Button,
  Card,
  Col,
  Result,
  Row,
  Space,
  Tag,
  message,
} from 'ant-design-vue';

import {
  getTicketDetailApi,
  getTicketDocApi,
  getTicketFlowStepsApi,
  listTicketActionsApi,
  listTicketAttachRulesApi,
  listTicketAttachmentsApi,
  submitTicketActionApi,
} from '#/api/ticket';

import TicketActions from './components/ticket-actions.vue';
import TicketSteps from './components/ticket-steps.vue';
import TicketConfirmProgress from './components/ticket-confirm-progress.vue';
import TicketAttachments from './components/ticket-attachments.vue';
import TicketBasic from './components/ticket-basic.vue';
import TicketCases from './components/ticket-cases.vue';
import TicketCheckItems from './components/ticket-check-items.vue';
import TicketContent from './components/ticket-content.vue';
import TicketDocView from './components/ticket-doc.vue';
import TicketTimeline from './components/ticket-timeline.vue';

const 路由 = useRoute();

/** 单据 ID 来自路由 /req/detail/:id */
const 单据id = computed(() => String(路由.params.id ?? ''));

const 单据 = ref<null | TicketDetail>(null);
const 动作列表 = ref<TicketActionItem[]>([]);
const 附件列表 = ref<TicketAttachment[]>([]);
const 缺口列表 = ref<TicketAttachRuleStatus[]>([]);
/** 步骤条数据：每步的人与材料，悬停时才看，但一次取回十步 */
const 步骤列表 = ref<TicketFlowStep[]>([]);
/** 12 节需求文档；BUG/INC 单或接口失败时为 null */
const 需求文档 = ref<null | TicketDoc>(null);
const 加载中 = ref(false);
const 提交中 = ref(false);

/** 头部折叠：收起后只留标题行 + 动作按钮 + 刷新，滚动时上下文占用最小 */
const 折叠 = ref(false);
const 错误 = ref('');

/** 动作条组件引用，提交成功后用它关弹窗 */
const 动作条 = ref<InstanceType<typeof TicketActions> | null>(null);

/** 单据是否已关闭。关闭后附件区与动作区都只读 */
const 已关闭 = computed(() => 单据.value?.status === 'CLOSED');

/**
 * 是否显示上线点检卡片。
 *
 * 只在点检及之后的环节出现：早期单据看到一张永远空的卡片没意义，
 * 而点检清单是上线后维护的，提前展示反而误导人以为要现在填。
 */
const 显示点检 = computed(() =>
  ['CLOSED', 'EVALUATING', 'IT_INSPECTION'].includes(单据.value?.status ?? ''),
);

/** 页面标题 */
const 页面标题 = computed(() =>
  单据.value?.ticketNo ? `需求单 ${单据.value.ticketNo}` : '需求单详情',
);

/**
 * 加载整页数据。
 *
 * 正文失败就整页报错；动作、附件、缺口三项用 allSettled，
 * 单个接口挂掉只影响自己那块，不至于让用户连单据内容都看不到。
 */
async function 加载() {
  if (!单据id.value) {
    错误.value = '缺少单据 ID，无法加载';
    return;
  }
  加载中.value = true;
  错误.value = '';
  try {
    单据.value = await getTicketDetailApi(单据id.value);
  } catch (异常) {
    错误.value = 异常 instanceof Error ? 异常.message : '单据加载失败';
    加载中.value = false;
    return;
  }

  const [动作结果, 附件结果, 缺口结果, 文档结果, 步骤结果] = await Promise.allSettled([
    listTicketActionsApi(单据id.value),
    listTicketAttachmentsApi(单据id.value),
    listTicketAttachRulesApi(单据id.value),
    getTicketDocApi(单据id.value),
    getTicketFlowStepsApi(单据id.value),
  ]);
  动作列表.value = 动作结果.status === 'fulfilled' ? 动作结果.value : [];
  附件列表.value = 附件结果.status === 'fulfilled' ? 附件结果.value : [];
  缺口列表.value = 缺口结果.status === 'fulfilled' ? 缺口结果.value : [];
  // 文档失败不弹错：BUG/INC 单本来就没有需求文档，接口会按「不存在」拒绝
  需求文档.value = 文档结果.status === 'fulfilled' ? 文档结果.value : null;
  // 步骤条挂掉只影响悬停提示，步骤本身仍按状态画（步骤为空时整条不渲染）
  步骤列表.value = 步骤结果.status === 'fulfilled' ? 步骤结果.value : [];
  加载中.value = false;
}

/**
 * 执行动作。
 *
 * 失败不在这里弹提示：请求层的 errorMessageResponseInterceptor 已经统一弹过，
 * 再弹一次就是同一句话出现两遍。
 *
 * @param 载荷 动作码与参数
 */
async function 执行动作(载荷: TicketActionParams) {
  提交中.value = true;
  try {
    const 结果 = await submitTicketActionApi(单据id.value, 载荷);
    // 提示语由后端按动作语义生成（并行确认会告知还差哪几方），
    // 不再用「已 + 状态标签」拼接——环节名拼出来是病句（如「已平台确认」）
    message.success(结果.hint || `已流转至「${结果.statusLabel}」`);
    动作条.value?.关闭弹窗();
    await 加载();
  } catch {
    // 保留在弹窗里，用户改完还能再提交，不用重新展开
  } finally {
    提交中.value = false;
  }
}

onMounted(加载);
</script>

<template>
  <Page>
    <Result v-if="错误" status="warning" :sub-title="错误" title="单据加载失败" />

    <Card v-else-if="加载中 && !单据">
      <div class="py-12 text-center text-sm text-gray-400">加载中…</div>
    </Card>

    <template v-else-if="单据">
      <div class="flex flex-col gap-4">
      <!-- 头部卡片吸顶：滚动时标题、步骤条、动作按钮始终可见。
           position 用内联样式而不是 sticky 类：antd 的 css-in-js 给 .ant-card
           默认注入 position: relative，注入顺序晚于 Tailwind，同特异性下会把
           .sticky 盖掉（top-0、z-20 不冲突所以能生效，唯独 position 不行）。 -->
      <Card class="top-0 z-20" style="position: sticky">
        <div class="flex items-center justify-between gap-3">
          <div class="flex min-w-0 flex-1 items-center gap-3">
            <span class="shrink-0 text-lg font-semibold">{{ 页面标题 }}</span>
            <!-- 折叠后动作按钮提到标题行：吸顶最小形态下仍能直接操作 -->
            <TicketActions
              v-if="折叠"
              class="min-w-0"
              :actions="动作列表"
              :status="单据.status"
              :submitting="提交中"
              @submit="执行动作"
            />
          </div>
          <Space class="shrink-0" :size="8">
            <Button @click="折叠 = !折叠">
              {{ 折叠 ? '展开 ▼' : '收起 ▲' }}
            </Button>
            <Button :loading="加载中" @click="加载">刷新</Button>
          </Space>
        </div>

        <template v-if="!折叠">
        <div class="flex flex-wrap items-start justify-between gap-3">
          <div class="min-w-0">
            <div class="flex flex-wrap items-center gap-2">
              <Tag :color="单据.statusCssClass || 'default'">{{ 单据.statusLabel }}</Tag>
              <Tag :color="单据.priorityCssClass || 'default'">{{ 单据.priorityLabel }}</Tag>
              <Tag>{{ 单据.ticketTypeLabel }}</Tag>
              <span class="min-w-0 break-words text-base font-semibold">
                {{ 单据.title }}
              </span>
            </div>
            <div class="mt-2 text-xs text-gray-500 dark:text-gray-400">
              {{ 单据.ticketNo }}
              <template v-if="单据.projectName"> · {{ 单据.projectName }}</template>
              <template v-if="单据.systemCode"> / {{ 单据.systemCode }}</template>
              <template v-if="单据.reporterName"> · 提出人 {{ 单据.reporterName }}</template>
              <template v-if="单据.returnCount"> · 累计退回 {{ 单据.returnCount }} 次</template>
            </div>
          </div>
        </div>

        <!-- 主链步骤条：过一步打一个 ✔，未到的置灰，审批环节并入所在步骤。
             鼠标移到节点上看这一步谁在处理、要交什么、还缺什么、交给谁 -->
        <TicketSteps
          v-if="步骤列表.length > 0"
          class="mt-4 border-t border-gray-200 pt-3 dark:border-gray-700"
          :steps="步骤列表"
          :status="单据.status"
          :suspended-from="单据.suspendedFrom"
        />

        <!-- 并行确认进度：三方确认 / 设计评审不分先后，这里展示还差谁 -->
        <TicketConfirmProgress
          v-if="单据.status === 'CONFIRMING' || 单据.status === 'DSGN_REVIEWING'"
          class="mt-3"
          :status="单据.status"
          :mit-passed="单据.mitPassed"
          :platform-passed="单据.platformPassed"
          :superior-passed="单据.superiorPassed"
          :dsgn-it-passed="单据.dsgnItPassed"
          :dsgn-platform-passed="单据.dsgnPlatformPassed"
          :dsgn-user-passed="单据.dsgnUserPassed"
        />

        <div class="mt-4 border-t border-gray-200 pt-3 dark:border-gray-700">
          <TicketActions
            v-if="!折叠"
            ref="动作条"
            :actions="动作列表"
            :status="单据.status"
            :submitting="提交中"
            @submit="执行动作"
          />
        </div>
        </template>
      </Card>

      <!-- 主栏：基本信息 → 需求内容 → 需求文档 → 附件。交付物紧跟需求正文，
           上传或核对材料时不用横向跳到另一栏。
           右栏只放流转时间线，与正文并列，读单据时不被长列表打断。 -->
      <Row :gutter="16">
        <Col :lg="16" :xs="24">
          <div class="flex flex-col gap-4">
            <Card size="small" title="基本信息">
              <TicketBasic :detail="单据" />
            </Card>
            <Card size="small" title="需求内容">
              <TicketContent :detail="单据" />
            </Card>
            <Card size="small" title="需求文档">
              <TicketDocView
                :doc="需求文档"
                :ticket-id="单据.id"
                @refresh="加载"
              />
            </Card>
            <!-- 测试用例紧跟需求文档：验收标准在第 10 节，用例逐条对应它，
                 两块挨着才看得出「哪条标准还没测」 -->
            <Card size="small" title="测试用例">
              <TicketCases
                :acceptance-locked="单据.acceptanceLocked"
                :ticket-id="单据.id"
              />
            </Card>
            <!-- 上线点检只在点检及之后的环节出现，早期单据不给空卡片 -->
            <Card v-if="显示点检" size="small" title="上线点检">
              <TicketCheckItems :ticket-id="单据.id" />
            </Card>
            <Card size="small" title="附件">
              <TicketAttachments
                :attachments="附件列表"
                :closed="已关闭"
                :rules="缺口列表"
                :ticket-id="单据.id"
                @refresh="加载"
              />
            </Card>
          </div>
        </Col>

        <Col :lg="8" :xs="24">
          <div class="flex flex-col gap-4">
            <Card :title="`流转时间线（${单据.timeline.length}）`" size="small">
              <TicketTimeline :items="单据.timeline" />
            </Card>
          </div>
        </Col>
      </Row>
      </div>
    </template>
  </Page>
</template>
