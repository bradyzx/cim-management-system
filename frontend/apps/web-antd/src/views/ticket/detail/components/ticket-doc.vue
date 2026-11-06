<script lang="ts" setup>
/**
 * 详情页·需求文档（12 节）。
 *
 * 展示的优先级是「能评审」而不是「好看」：每节都带标题与「待补全」标记，
 * 缺哪节一眼看到；右上角常驻一句「确认还差几处」，避免用户点了确认才被拦。
 *
 * 数据全部来自 `/ticket/{id}/doc`：`missingSections` 是后端按契约 §8.1.7 算的，
 * 前端不重复算一遍校验规则 —— 规则改一次改两处，必然对不上。
 */
import type { TicketDoc } from '#/api/ticket';

import { computed, ref } from 'vue';

import { Button, Drawer, Tag } from 'ant-design-vue';

import TicketDocEditor from './ticket-doc-editor.vue';

const props = defineProps<{
  /** 文档，接口失败或单据还没有文档时为 null */
  doc: null | TicketDoc;
  ticketId: string;
}>();

const emit = defineEmits<{ refresh: [] }>();

/** 编辑抽屉开关 */
const 编辑中 = ref(false);
/** Markdown 正文抽屉开关 */
const 正文中 = ref(false);

/** 12 节。结构损坏时后端返回空对象，这里兜底成空，模板靠可选链取不到就显示待补充 */
const 节 = computed(() => props.doc?.docJson?.sections ?? {});

/** 待补全的节键名集合，用于给对应小节打标 */
const 缺失 = computed(() => new Set(props.doc?.missingSections ?? []));

/** 是否已确认锁定 */
const 已确认 = computed(() => Boolean(props.doc?.confirmedAt));

/** 节标题样式：12 个小节复用，避免每处抄一遍长 class */
const 标题类 = 'mb-1 flex items-center gap-2 text-xs font-medium text-gray-500 dark:text-gray-400';
/** 节内容框样式 */
const 框类 =
  'rounded border border-gray-200 p-3 text-sm leading-6 whitespace-pre-wrap dark:border-gray-700';
/** 表格单元格样式 */
const 格类 = 'border-b border-gray-100 px-2 py-1 text-left dark:border-gray-800';

/**
 * 取纯文本节的内容。
 *
 * @param key 节键名
 */
function 取文本(key: 's1_name' | 's2_asis' | 's4_tobe') {
  return 节.value[key]?.content ?? '';
}
</script>

<template>
  <div v-if="!doc" class="py-6 text-center text-sm text-gray-400">
    该单据还没有需求文档
  </div>

  <div v-else class="space-y-4">
    <!-- 顶部状态条：版本、生成方式、确认情况、离确认还差什么 -->
    <div
      class="flex flex-wrap items-center gap-2 rounded bg-gray-50 px-3 py-2 text-xs dark:bg-gray-800"
    >
      <Tag>v{{ doc.version }}</Tag>
      <Tag :color="doc.aiDegraded === 1 ? 'warning' : 'default'">
        {{ doc.aiDegraded === 1 ? '降级模板空壳' : doc.aiModel || '规则模板生成' }}
      </Tag>
      <Tag v-if="已确认" color="success">
        已确认{{ doc.confirmedByName ? ` · ${doc.confirmedByName}` : '' }}
        {{ doc.confirmedAt ? ` · ${doc.confirmedAt}` : '' }}
      </Tag>
      <Tag v-else color="default">待提出人确认</Tag>

      <span
        v-if="!已确认 && doc.missingMessages.length > 0"
        class="text-orange-600 dark:text-orange-400"
      >
        确认前还差 {{ doc.missingMessages.length }} 处：{{ doc.missingMessages.join('；') }}
      </span>
      <span v-else-if="!已确认" class="text-green-600 dark:text-green-400">
        已满足确认条件
      </span>

      <div class="ml-auto flex gap-2">
        <Button size="small" @click="正文中 = true">Markdown 正文</Button>
        <Button
          v-if="doc.editable"
          size="small"
          type="primary"
          @click="编辑中 = true"
        >
          编辑文档
        </Button>
      </div>
    </div>

    <!-- 1 需求名称 -->
    <div>
      <div :class="标题类">
        <span>一、需求名称</span>
        <Tag v-if="缺失.has('s1_name')" color="error">待补全</Tag>
      </div>
      <div :class="框类">{{ 取文本('s1_name') || '待补充' }}</div>
    </div>

    <!-- 2 业务背景 -->
    <div>
      <div :class="标题类">
        <span>二、业务背景</span>
        <Tag v-if="缺失.has('s2_asis')" color="error">待补全</Tag>
      </div>
      <div :class="框类">{{ 取文本('s2_asis') || '待补充' }}</div>
    </div>

    <!-- 3 痛点与影响：正文 + 量化指标 -->
    <div>
      <div :class="标题类">
        <span>三、痛点与影响</span>
        <Tag v-if="缺失.has('s3_pain')" color="error">待补全</Tag>
      </div>
      <div :class="框类">
        <div>{{ 节.s3_pain?.content || '待补充' }}</div>
        <div v-if="(节.s3_pain?.metrics ?? []).length > 0" class="mt-2 flex flex-wrap gap-2">
          <Tag v-for="指标 in 节.s3_pain?.metrics" :key="指标.name" color="blue">
            {{ 指标.name }}：{{ 指标.value }}
          </Tag>
        </div>
      </div>
    </div>

    <!-- 4 期望目标 -->
    <div>
      <div :class="标题类">
        <span>四、期望目标</span>
        <Tag v-if="缺失.has('s4_tobe')" color="error">待补全</Tag>
      </div>
      <div :class="框类">{{ 取文本('s4_tobe') || '待补充' }}</div>
    </div>

    <!-- 5 功能清单：编号是三处结构化数据（点检项、变更比对、验收引用）的锚点 -->
    <div>
      <div :class="标题类">
        <span>五、功能清单</span>
        <Tag v-if="缺失.has('s5_functions')" color="error">待补全</Tag>
        <span class="text-gray-400">
          （点检项草稿与用例覆盖率都从这里取）
        </span>
      </div>
      <div v-if="(节.s5_functions?.items ?? []).length > 0" class="rounded border border-gray-200 dark:border-gray-700">
        <table class="w-full text-sm">
          <thead>
            <tr class="text-xs text-gray-500 dark:text-gray-400">
              <th :class="格类">编号</th>
              <th :class="格类">功能</th>
              <th :class="格类">优先级</th>
              <th :class="格类">类型</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="功能 in 节.s5_functions?.items" :key="功能.code">
              <td :class="格类">{{ 功能.code }}</td>
              <td :class="格类">{{ 功能.text }}</td>
              <td :class="格类">{{ 功能.priority || '-' }}</td>
              <td :class="格类">{{ 功能.type || '-' }}</td>
            </tr>
          </tbody>
        </table>
      </div>
      <div v-else :class="框类">待补充</div>
    </div>

    <!-- 6 非功能要求 -->
    <div>
      <div :class="标题类">
        <span>六、非功能要求</span>
        <Tag v-if="缺失.has('s6_nfr')" color="error">待补全</Tag>
      </div>
      <div v-if="(节.s6_nfr?.items ?? []).length > 0" :class="框类">
        <div v-for="(项, i) in 节.s6_nfr?.items" :key="i">
          · [{{ 项.category }}] {{ 项.text }}
        </div>
      </div>
      <div v-else :class="框类">无</div>
    </div>

    <!-- 7 联动范围 -->
    <div>
      <div :class="标题类">
        <span>七、联动范围</span>
        <Tag v-if="缺失.has('s7_scope')" color="error">待补全</Tag>
      </div>
      <div :class="框类">
        <div v-for="(系统, i) in 节.s7_scope?.systems" :key="`s${i}`">
          · 系统 {{ 系统.systemCode }}：{{ 系统.impact || '影响待补充' }}
        </div>
        <div v-for="(部门, i) in 节.s7_scope?.depts" :key="`d${i}`">
          · 部门 {{ 部门.deptCode }}：{{ 部门.impact || '影响待补充' }}
        </div>
        <div
          v-if="
            (节.s7_scope?.systems ?? []).length === 0 &&
            (节.s7_scope?.depts ?? []).length === 0
          "
        >
          待补充
        </div>
      </div>
    </div>

    <!-- 8 优先级 -->
    <div>
      <div :class="标题类">
        <span>八、优先级</span>
        <Tag v-if="缺失.has('s8_priority')" color="error">待补全</Tag>
      </div>
      <div :class="框类">
        <div>
          最终等级：{{ 节.s8_priority?.level || '待定' }}
          <span class="ml-3 text-xs text-gray-500">
            打分 影响 {{ 节.s8_priority?.scores?.impact ?? 0 }} /
            紧急 {{ 节.s8_priority?.scores?.urgency ?? 0 }} /
            合规 {{ 节.s8_priority?.scores?.compliance ?? 0 }}
          </span>
        </div>
        <div class="mt-1">理由：{{ 节.s8_priority?.reason || '待补充' }}</div>
      </div>
    </div>

    <!-- 9 工作量：AI 估算必须带水印，否则会被当成已确认值 -->
    <div>
      <div :class="标题类">
        <span>九、工作量区间</span>
        <Tag v-if="缺失.has('s9_effort')" color="error">待补全</Tag>
        <Tag v-if="节.s9_effort?.aiEstimated === 1" color="warning">
          AI 估算，需 MIT 确认
        </Tag>
      </div>
      <div :class="框类">
        <template v-if="节.s9_effort?.minDays && 节.s9_effort?.maxDays">
          {{ 节.s9_effort.minDays }} ~ {{ 节.s9_effort.maxDays }} 人天
          <span v-if="节.s9_effort.suggestReleaseDate" class="ml-3">
            建议上线：{{ 节.s9_effort.suggestReleaseDate }}
          </span>
        </template>
        <template v-else>待补充</template>
      </div>
    </div>

    <!-- 10 验收标准：seq 即覆盖率分母的键 -->
    <div>
      <div :class="标题类">
        <span>十、验收标准</span>
        <Tag v-if="缺失.has('s10_acceptance')" color="error">待补全</Tag>
        <span class="text-gray-400">（UAT 覆盖率的分母，逐条对应用例）</span>
      </div>
      <div v-if="(节.s10_acceptance?.items ?? []).length > 0" :class="框类">
        <div v-for="条 in 节.s10_acceptance?.items" :key="条.seq">
          {{ 条.seq }}. {{ 条.text }}
        </div>
      </div>
      <div v-else :class="框类">待补充</div>
    </div>

    <!-- 11 附件清单：用于核对「清单里有、附件区没传」 -->
    <div>
      <div :class="标题类">
        <span>十一、附件清单</span>
        <Tag v-if="缺失.has('s11_attachments')" color="error">待补全</Tag>
      </div>
      <div v-if="(节.s11_attachments?.items ?? []).length > 0" :class="框类">
        <div v-for="(项, i) in 节.s11_attachments?.items" :key="i">
          · {{ 项.name }}（{{ 项.attachType }}）
        </div>
      </div>
      <div v-else :class="框类">无</div>
    </div>

    <!-- 12 AI 预审：给评审人看，不给人改 -->
    <div>
      <div :class="标题类">
        <span>十二、AI 预审结论</span>
        <Tag v-if="缺失.has('s12_ai_review')" color="error">待补全</Tag>
      </div>
      <div :class="框类">
        <div>
          查重相似度 {{ 节.s12_ai_review?.dupScore ?? 0 }} ·
          结论 {{ 节.s12_ai_review?.verdict || 'NEW' }} ·
          完整度 {{ 节.s12_ai_review?.completeness ?? 0 }} 分
        </div>
        <div v-if="(节.s12_ai_review?.risks ?? []).length > 0" class="mt-1">
          风险提示：
          <span v-for="(风险, i) in 节.s12_ai_review?.risks" :key="i">
            {{ i > 0 ? '；' : '' }}{{ 风险 }}
          </span>
        </div>
        <div v-if="(节.s12_ai_review?.unclarified ?? []).length > 0" class="mt-1">
          未澄清项：
          <span v-for="(项, i) in 节.s12_ai_review?.unclarified" :key="i">
            {{ i > 0 ? '；' : '' }}{{ 项 }}
          </span>
        </div>
      </div>
    </div>

    <!-- 编辑抽屉：保存成功后刷新整页，让「还差几处」与按钮状态同步 -->
    <TicketDocEditor
      v-model:open="编辑中"
      :doc="doc"
      :ticket-id="ticketId"
      @saved="emit('refresh')"
    />

    <!-- Markdown 正文：给需要复制/贴到别处的人一个出口 -->
    <Drawer
      v-model:open="正文中"
      :body-style="{ padding: '16px' }"
      title="Markdown 正文"
      :width="760"
    >
      <pre class="whitespace-pre-wrap text-xs leading-6">{{ doc.contentMd }}</pre>
    </Drawer>
  </div>
</template>
