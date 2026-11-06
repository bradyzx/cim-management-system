<script lang="ts" setup>
/**
 * 需求文档编辑抽屉。
 *
 * 编辑的是一份深拷贝，点「保存」才回写：中途关掉不会污染页面上的展示数据。
 * 第 12 节（AI 预审）整节只读 —— 那是系统的结论，人改了就失去「AI 预审」的意义。
 *
 * 编号类字段（功能 F-xx、验收 seq）不接受手填：删行后自动重排。
 * 校验要求两者都从 1 连续递增，让人手动维护迟早会跳号，
 * 而跳号的后果是下游（点检项、用例覆盖率）指错，且不会报错。
 */
import type {
  TicketDoc,
  TicketDocSections,
} from '#/api/ticket';

import { ref, watch } from 'vue';

import {
  Button,
  Divider,
  Drawer,
  Input,
  InputNumber,
  Select,
  message,
} from 'ant-design-vue';

import { saveTicketDocApi } from '#/api/ticket';

const props = defineProps<{
  doc: TicketDoc;
  open: boolean;
  ticketId: string;
}>();

const emit = defineEmits<{
  saved: [];
  'update:open': [boolean];
}>();

/** 保存中，防重复提交 */
const 保存中 = ref(false);

/** 编辑副本 */
const 草稿 = ref<TicketDocSections>(空结构());

/** 优先级选项，与契约 §8.1.3 一致 */
const 优先级选项 = ['P0', 'P1', 'P2', 'P3'].map((值) => ({ label: 值, value: 值 }));

/** 功能类型选项 */
const 类型选项 = [
  { label: '新增', value: 'NEW' },
  { label: '修改', value: 'MODIFY' },
  { label: '下线', value: 'DELETE' },
];

/** 非功能类别选项，与契约 §8.1.2 一致 */
const 类别选项 = [
  { label: '性能', value: 'PERF' },
  { label: '兼容性', value: 'COMPAT' },
  { label: '安全', value: 'SECURITY' },
  { label: '数据保留', value: 'RETENTION' },
  { label: '其他', value: 'OTHER' },
];

/**
 * 12 节的空结构。每节都给默认值，模板里就不会出现 undefined 取属性。
 */
function 空结构(): TicketDocSections {
  return {
    s1_name: { content: '' },
    s2_asis: { content: '' },
    s3_pain: { content: '', metrics: [] },
    s4_tobe: { content: '' },
    s5_functions: { items: [] },
    s6_nfr: { items: [] },
    s7_scope: { systems: [], depts: [] },
    s8_priority: {
      level: '',
      reason: '',
      scores: { compliance: 0, impact: 0, urgency: 0 },
    },
    s9_effort: {
      aiEstimated: 1,
      confirmedBy: null,
      maxDays: null,
      minDays: null,
      suggestReleaseDate: null,
    },
    s10_acceptance: { items: [] },
    s11_attachments: { items: [] },
    s12_ai_review: {
      completeness: 0,
      dupScore: 0,
      risks: [],
      unclarified: [],
      verdict: 'NEW',
    },
  };
}

/**
 * 把当前文档拷进草稿。用 JSON 往返而不是直接引用：
 * 直接引用会让输入框边打字边改页面上的展示值，取消也回不去。
 */
function 重置草稿() {
  const 原 = props.doc.docJson?.sections ?? {};
  const 合并 = { ...空结构(), ...JSON.parse(JSON.stringify(原)) } as TicketDocSections;
  // 老数据可能缺某一节或某一节的子数组，逐节补默认值，避免模板报错
  合并.s3_pain.metrics ??= [];
  合并.s5_functions.items ??= [];
  合并.s6_nfr.items ??= [];
  合并.s7_scope.systems ??= [];
  合并.s7_scope.depts ??= [];
  合并.s8_priority.scores ??= { compliance: 0, impact: 0, urgency: 0 };
  合并.s9_effort ??= 空结构().s9_effort;
  合并.s10_acceptance.items ??= [];
  合并.s11_attachments.items ??= [];
  草稿.value = 合并;
}

/**
 * 功能清单重排编号：F-01、F-02 连续。
 */
function 重排功能编号() {
  const 项 = 草稿.value.s5_functions.items;
  for (let i = 0; i < 项.length; i++) {
    // 断言非空：上面初始化和下面新增都会给值，这里只是让类型收窄
    项[i]!.code = `F-${String(i + 1).padStart(2, '0')}`;
  }
}

/**
 * 验收标准重排序号：从 1 连续递增。
 */
function 重排验收序号() {
  const 项 = 草稿.value.s10_acceptance.items;
  for (let i = 0; i < 项.length; i++) {
    项[i]!.seq = i + 1;
  }
}

function 新增功能() {
  草稿.value.s5_functions.items.push({ code: '', priority: 'P2', text: '', type: 'NEW' });
  重排功能编号();
}

function 新增验收() {
  草稿.value.s10_acceptance.items.push({ seq: 0, text: '' });
  重排验收序号();
}

/**
 * 数字输入框的值转成 JSON 里的 null/number。
 *
 * antd 清空输入框时给 undefined，而契约里「未评估」要存成 null，
 * 统一在这里转换，模板里不再各自判断。
 *
 * @param 值 输入框回传值
 */
function 转数字(值: number | string | undefined): null | number {
  return typeof 值 === 'number' ? 值 : null;
}

/**
 * 文本输入框的值转成 JSON 里的 null/string。
 *
 * @param 值 输入框回传值
 */
function 转文本(值: number | string | undefined): null | string {
  if (typeof 值 !== 'string') {
    return null;
  }
  return 值.trim() === '' ? null : 值;
}

/**
 * 保存。校验交给后端：前端只拦「明显没填」的空行，
 * 编排规则（编号连续、seq 连续）以后端返回的 missingMessages 为准。
 */
async function 保存() {
  保存中.value = true;
  try {
    await saveTicketDocApi(props.ticketId, {
      schemaVersion: 1,
      sections: 草稿.value,
    });
    message.success('需求文档已保存');
    emit('saved');
    emit('update:open', false);
  } catch {
    // 失败提示由请求层统一弹，这里保留抽屉让用户改完再存
  } finally {
    保存中.value = false;
  }
}

/** 抽屉打开即重新拷一份，关掉时不做任何事 */
watch(
  () => props.open,
  (开) => {
    if (开) {
      重置草稿();
    }
  },
);
</script>

<template>
  <Drawer
    :open="open"
    :width="820"
    title="编辑需求文档"
    @update:open="emit('update:open', $event)"
  >
    <template #extra>
      <Button :loading="保存中" type="primary" @click="保存">保存</Button>
    </template>

    <div class="space-y-5">
      <div class="text-xs text-gray-500 dark:text-gray-400">
        只改结构化内容，Markdown 正文保存时由系统重新渲染。
        功能清单编号与验收标准序号会自动重排，不用手填。
      </div>

      <!-- 1 需求名称 -->
      <div>
        <div class="mb-1 text-sm font-medium">一、需求名称</div>
        <Input v-model:value="草稿.s1_name.content" placeholder="能一眼看懂，30 字以内" />
      </div>

      <!-- 2 业务背景 -->
      <div>
        <div class="mb-1 text-sm font-medium">二、业务背景</div>
        <Input.TextArea
          v-model:value="草稿.s2_asis.content"
          :rows="3"
          placeholder="现在怎么做、谁在做、多久做一次"
        />
      </div>

      <!-- 3 痛点与影响 -->
      <div>
        <div class="mb-1 text-sm font-medium">三、痛点与影响</div>
        <Input.TextArea
          v-model:value="草稿.s3_pain.content"
          :rows="3"
          placeholder="不写「效率低」，写清楚差在哪、慢多久"
        />
        <div class="mt-2 space-y-2">
          <div
            v-for="(指标, i) in 草稿.s3_pain.metrics"
            :key="i"
            class="flex items-center gap-2"
          >
            <Input
              v-model:value="指标.name"
              placeholder="指标名，如 排查耗时"
              style="width: 180px; flex: none"
            />
            <Input
              v-model:value="指标.value"
              placeholder="数值，如 2 小时/批次"
              style="flex: 1 1 0; min-width: 0"
            />
            <Button danger size="small" @click="草稿.s3_pain.metrics.splice(i, 1)">
              删除
            </Button>
          </div>
          <Button size="small" @click="草稿.s3_pain.metrics.push({ name: '', value: '' })">
            + 添加量化指标
          </Button>
        </div>
      </div>

      <!-- 4 期望目标 -->
      <div>
        <div class="mb-1 text-sm font-medium">四、期望目标</div>
        <Input.TextArea
          v-model:value="草稿.s4_tobe.content"
          :rows="3"
          placeholder="希望变成什么样。只写目标，不写实现方式"
        />
      </div>

      <Divider style="margin: 8px 0" />

      <!-- 5 功能清单 -->
      <div>
        <div class="mb-1 text-sm font-medium">
          五、功能清单
          <span class="ml-2 text-xs font-normal text-gray-400">
            编号自动生成，一旦发布不再改动
          </span>
        </div>
        <div class="space-y-2">
          <div
            v-for="(功能, i) in 草稿.s5_functions.items"
            :key="i"
            class="flex items-start gap-2"
          >
            <Input :value="功能.code" disabled style="width: 76px; flex: none" />
            <Input
              v-model:value="功能.text"
              placeholder="一句话可验收，不写「优化体验」"
              style="flex: 1 1 0; min-width: 0"
            />
            <Select
              v-model:value="功能.priority"
              :options="优先级选项"
              style="width: 88px; flex: none"
            />
            <Select
              v-model:value="功能.type"
              :options="类型选项"
              style="width: 100px; flex: none"
            />
            <Button
              danger
              size="small"
              @click="
                草稿.s5_functions.items.splice(i, 1);
                重排功能编号();
              "
            >
              删除
            </Button>
          </div>
        </div>
        <Button class="mt-2" size="small" @click="新增功能">+ 添加功能</Button>
      </div>

      <!-- 6 非功能要求 -->
      <div>
        <div class="mb-1 text-sm font-medium">六、非功能要求</div>
        <div class="space-y-2">
          <div
            v-for="(项, i) in 草稿.s6_nfr.items"
            :key="i"
            class="flex items-start gap-2"
          >
            <Select
              v-model:value="项.category"
              :options="类别选项"
              style="width: 120px; flex: none"
            />
            <Input
              v-model:value="项.text"
              placeholder="可判定的要求，如 单批次 5000 点加载 ≤3 秒"
              style="flex: 1 1 0; min-width: 0"
            />
            <Button danger size="small" @click="草稿.s6_nfr.items.splice(i, 1)">
              删除
            </Button>
          </div>
        </div>
        <Button
          class="mt-2"
          size="small"
          @click="草稿.s6_nfr.items.push({ category: 'PERF', text: '' })"
        >
          + 添加要求
        </Button>
      </div>

      <Divider style="margin: 8px 0" />

      <!-- 7 联动范围 -->
      <div>
        <div class="mb-1 text-sm font-medium">七、联动范围</div>
        <div class="space-y-2">
          <div
            v-for="(系统, i) in 草稿.s7_scope.systems"
            :key="`s${i}`"
            class="flex items-center gap-2"
          >
            <Input
              v-model:value="系统.systemCode"
              placeholder="系统编码"
              style="width: 120px; flex: none"
            />
            <Input
              v-model:value="系统.impact"
              placeholder="影响说明，不写「涉及 MES」这种废话"
              style="flex: 1 1 0; min-width: 0"
            />
            <Button danger size="small" @click="草稿.s7_scope.systems.splice(i, 1)">
              删除
            </Button>
          </div>
          <div
            v-for="(部门, i) in 草稿.s7_scope.depts"
            :key="`d${i}`"
            class="flex items-center gap-2"
          >
            <Input
              v-model:value="部门.deptCode"
              placeholder="部门编码"
              style="width: 120px; flex: none"
            />
            <Input
              v-model:value="部门.impact"
              placeholder="影响说明"
              style="flex: 1 1 0; min-width: 0"
            />
            <Button danger size="small" @click="草稿.s7_scope.depts.splice(i, 1)">
              删除
            </Button>
          </div>
          <div class="flex gap-2">
            <Button
              size="small"
              @click="草稿.s7_scope.systems.push({ systemCode: '', impact: '' })"
            >
              + 系统
            </Button>
            <Button size="small" @click="草稿.s7_scope.depts.push({ deptCode: '', impact: '' })">
              + 部门
            </Button>
          </div>
        </div>
      </div>

      <!-- 8 优先级 -->
      <div>
        <div class="mb-1 text-sm font-medium">八、优先级</div>
        <div class="flex flex-wrap items-center gap-2">
          <Select
            v-model:value="草稿.s8_priority.level"
            :options="优先级选项"
            placeholder="最终等级"
            style="width: 120px"
          />
          <span class="text-xs text-gray-500">影响</span>
          <InputNumber v-model:value="草稿.s8_priority.scores.impact" :max="3" :min="0" />
          <span class="text-xs text-gray-500">紧急</span>
          <InputNumber v-model:value="草稿.s8_priority.scores.urgency" :max="3" :min="0" />
          <span class="text-xs text-gray-500">合规</span>
          <InputNumber v-model:value="草稿.s8_priority.scores.compliance" :max="3" :min="0" />
        </div>
        <Input
          v-model:value="草稿.s8_priority.reason"
          class="mt-2"
          placeholder="定这个等级的理由。等级与打分不一致时必须写清"
        />
      </div>

      <!-- 9 工作量 -->
      <div>
        <div class="mb-1 text-sm font-medium">
          九、工作量区间
          <span class="ml-2 text-xs font-normal text-gray-400">
            派单默认工时取区间中值，不必写「约十天」
          </span>
        </div>
        <div class="flex flex-wrap items-center gap-2">
          <!-- 第 9 节的三个值在 JSON 里可以是 null（未评估），
               但 antd 的 InputNumber / Input 不接受 null，这里做一次转换 -->
          <span class="text-xs text-gray-500">最小</span>
          <InputNumber
            :min="0"
            :precision="2"
            :step="0.5"
            :value="草稿.s9_effort.minDays ?? undefined"
            style="width: 110px; flex: none"
            @update:value="(值) => (草稿.s9_effort.minDays = 转数字(值))"
          />
          <span class="text-xs text-gray-500">~</span>
          <InputNumber
            :min="0"
            :precision="2"
            :step="0.5"
            :value="草稿.s9_effort.maxDays ?? undefined"
            style="width: 110px; flex: none"
            @update:value="(值) => (草稿.s9_effort.maxDays = 转数字(值))"
          />
          <span class="text-xs text-gray-500">最大（人天）</span>
          <Input
            placeholder="建议上线日期 yyyy-MM-dd"
            style="width: 200px; flex: none"
            :value="草稿.s9_effort.suggestReleaseDate ?? undefined"
            @update:value="(值) => (草稿.s9_effort.suggestReleaseDate = 转文本(值))"
          />
        </div>
      </div>

      <Divider style="margin: 8px 0" />

      <!-- 10 验收标准 -->
      <div>
        <div class="mb-1 text-sm font-medium">
          十、验收标准
          <span class="ml-2 text-xs font-normal text-gray-400">
            序号自动生成，UAT 覆盖率按它逐条对应用例
          </span>
        </div>
        <div class="space-y-2">
          <div
            v-for="(条, i) in 草稿.s10_acceptance.items"
            :key="i"
            class="flex items-center gap-2"
          >
            <Input :value="条.seq" disabled style="width: 60px; flex: none" />
            <Input
              v-model:value="条.text"
              placeholder="一条可判定的验收标准"
              style="flex: 1 1 0; min-width: 0"
            />
            <Button
              danger
              size="small"
              @click="
                草稿.s10_acceptance.items.splice(i, 1);
                重排验收序号();
              "
            >
              删除
            </Button>
          </div>
        </div>
        <Button class="mt-2" size="small" @click="新增验收">+ 添加验收标准</Button>
      </div>

      <!-- 11 附件清单 -->
      <div>
        <div class="mb-1 text-sm font-medium">
          十一、附件清单
          <span class="ml-2 text-xs font-normal text-gray-400">
            用于核对「清单里有、附件区没传」
          </span>
        </div>
        <div class="space-y-2">
          <div
            v-for="(项, i) in 草稿.s11_attachments.items"
            :key="i"
            class="flex items-center gap-2"
          >
            <Input
              v-model:value="项.name"
              placeholder="附件名称"
              style="flex: 1 1 0; min-width: 0"
            />
            <Input
              v-model:value="项.attachType"
              placeholder="附件类型码"
              style="width: 160px; flex: none"
            />
            <Button danger size="small" @click="草稿.s11_attachments.items.splice(i, 1)">
              删除
            </Button>
          </div>
        </div>
        <Button
          class="mt-2"
          size="small"
          @click="草稿.s11_attachments.items.push({ attachType: '', name: '' })"
        >
          + 添加附件项
        </Button>
      </div>

      <!-- 12 AI 预审：只读 -->
      <div>
        <div class="mb-1 text-sm font-medium">
          十二、AI 预审结论
          <span class="ml-2 text-xs font-normal text-gray-400">系统生成，不可编辑</span>
        </div>
        <div
          class="rounded border border-gray-200 p-3 text-sm text-gray-600 dark:border-gray-700 dark:text-gray-300"
        >
          查重相似度 {{ 草稿.s12_ai_review.dupScore }} ·
          结论 {{ 草稿.s12_ai_review.verdict }} ·
          完整度 {{ 草稿.s12_ai_review.completeness }} 分
          <div v-if="草稿.s12_ai_review.risks.length > 0" class="mt-1 text-xs">
            风险：{{ 草稿.s12_ai_review.risks.join('；') }}
          </div>
        </div>
      </div>
    </div>
  </Drawer>
</template>
