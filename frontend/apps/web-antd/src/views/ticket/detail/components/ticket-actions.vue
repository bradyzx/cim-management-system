<script lang="ts" setup>
/**
 * 详情页·动作按钮条与动作弹窗。
 *
 * 按钮从 `/actions` 来，前端**不做第二次权限判断**：后端已经把不属于当前用户的动作
 * 过滤掉了，`enabled=false` 只表示「属于你但现在还不能点」，并带 `blockers` 说明原因。
 *
 * 弹窗字段按动作码配置。哪些动作要哪些字段，与后端 PreconditionChecker 一一对应；
 * 那边加一个必填项，这里必须同步加，否则会出现「界面填完了后端还说缺字段」。
 */
import type { TicketActionItem, TicketActionParams } from '#/api/ticket';

import { computed, ref } from 'vue';

import {
  Button,
  DatePicker,
  Form,
  FormItem,
  Input,
  InputNumber,
  Modal,
  Rate,
  Select,
  Space,
  Textarea,
  Tooltip,
  message,
} from 'ant-design-vue';

import { requestClient } from '#/api/request';

const props = defineProps<{
  /** 当前用户可执行的动作 */
  actions: TicketActionItem[];
  /** 单据当前状态，作为乐观并发的校验值 */
  status: string;
  /** 父组件正在提交 */
  submitting: boolean;
}>();

const emit = defineEmits<{ submit: [payload: TicketActionParams] }>();

/** 弹窗表单字段 */
interface 表单字段 {
  key: string;
  label: string;
  type: 'datetime' | 'number' | 'rate' | 'select' | 'text' | 'textarea';
  options?: { label: string; value: string }[];
  placeholder?: string;
  required?: boolean;
  tip?: string;
}

/** 下拉选项 */
interface 选项 {
  label: string;
  value: string;
}

const 弹窗可见 = ref(false);
const 当前动作 = ref<null | TicketActionItem>(null);
// 动态表单，每个字段的值类型由字段配置决定（数字 / 字符串 / 日期串），
// 这里只能放宽类型；提交前按字段类型组装，不依赖 any 做业务判断
const 表单值 = ref<Record<string, any>>({});
const 备注 = ref('');
// 用 undefined 而不是 null：antd Select 的 SelectValue 不接受 null，
// 传 null 会让 v-model 的类型检查失败
const 原因 = ref<string | undefined>(undefined);

const 原因选项 = ref<选项[]>([]);
const 用户选项 = ref<选项[]>([]);
/** 只在用到时才拉，避免进详情页就发两个用不上的请求 */
let 原因已加载 = false;
let 用户已加载 = false;

/**
 * 评审结论「继续」可选的目标状态。
 *
 * 一期固定四项：评审组只可能把单子打回需求池、设计、需求评审或三方确认，
 * 不允许「原地继续」—— 那等于评审没有结论。
 */
const 评审目标选项: 选项[] = [
  { label: '待分配', value: 'PENDING_ASSIGN' },
  { label: '设计中', value: 'DESIGNING' },
  { label: '需求评审', value: 'REQ_REVIEW' },
  { label: '三方确认', value: 'CONFIRMING' },
];

/** 动作码到表单字段的映射 */
const 动作字段表 = computed<Record<string, 表单字段[]>>(() => ({
  ASSIGN: [
    {
      key: 'assigneeId',
      label: '承办人',
      type: 'select',
      required: true,
      options: 用户选项.value,
      placeholder: '选择承办人',
    },
    { key: 'estimatedHours', label: '预估工时（小时）', type: 'number', required: true },
  ],
  MONITOR_PASS: [
    {
      key: 'monitorNote',
      label: '监控结论',
      type: 'textarea',
      required: true,
      tip: '不少于 20 字，写清看了哪些指标、有没有异常',
    },
  ],
  EVAL_PASS: [{ key: 'satisfactionScore', label: '满意度', type: 'rate', required: true }],
  EVAL_FAIL: [
    { key: 'evalComment', label: '不符合原因', type: 'textarea', required: true },
  ],
  SUSPEND: [
    { key: 'suspendReason', label: '挂起原因', type: 'textarea', required: true },
    { key: 'expectResumeAt', label: '预计恢复时间', type: 'datetime', required: true },
  ],
  REVIEW_CONTINUE: [
    {
      key: 'targetStatus',
      label: '回到哪一步',
      type: 'select',
      required: true,
      options: 评审目标选项,
    },
  ],
  UAT_RAISE_BUG: [{ key: 'bugTitle', label: '缺陷标题', type: 'text', required: true }],
}));

/** 当前动作需要渲染的字段 */
const 当前字段 = computed<表单字段[]>(() => {
  if (!当前动作.value) {
    return [];
  }
  return 动作字段表.value[当前动作.value.action] ?? [];
});

/** 当前动作是否必须选退回原因 */
const 需要原因 = computed(() => Boolean(当前动作.value?.returnPoint));

/**
 * 阻塞说明。多条 blocker 全部列出来，只显示第一条会让人补完一个又被拦一次。
 *
 * @param 动作 动作
 * @returns 提示文本
 */
function 阻塞说明(动作: TicketActionItem): string {
  return 动作.blockers.map((项) => 项.message).join('；');
}

/**
 * 打开动作弹窗。
 *
 * @param 动作 动作
 */
async function 打开(动作: TicketActionItem) {
  当前动作.value = 动作;
  表单值.value = {};
  备注.value = '';
  原因.value = undefined;

  if (动作.returnPoint) {
    await 确保原因选项();
  }
  if (动作.action === 'ASSIGN') {
    await 确保用户选项();
  }
  弹窗可见.value = true;
}

/** 拉退回原因字典。字典改标签时界面自动跟着变，不硬编码。 */
async function 确保原因选项() {
  if (原因已加载) {
    return;
  }
  try {
    const 列表 = await requestClient.get<选项[]>('/dict/data', {
      params: { code: 'return_reason' },
    });
    原因选项.value = 列表 ?? [];
    原因已加载 = true;
  } catch {
    message.error('退回原因字典加载失败，请刷新重试');
  }
}

/**
 * 拉可选承办人。后端只返回持有开发角色的启用用户。
 *
 * 不在前端自己按角色过滤：角色数据在库里随时会变，前端硬编码一份等于两处维护；
 * 而且派给非开发角色，单据会卡死在「待接收」——承办人没有接收按钮，
 * 谁都推不动。所以过滤与校验都放后端，这里只管取。
 */
async function 确保用户选项() {
  if (用户已加载) {
    return;
  }
  try {
    const 列表 = await requestClient.get<{
      deptName?: null | string;
      id: string;
      realName: string;
    }[]>('/system/user/assignees');
    用户选项.value = (列表 ?? []).map((用户) => ({
      label: 用户.deptName ? `${用户.realName}（${用户.deptName}）` : 用户.realName,
      value: 用户.id,
    }));
    用户已加载 = true;
    if (用户选项.value.length === 0) {
      message.warning('当前没有可派单的开发人员，请先在用户管理里分配「MIT 开发人员」角色');
    }
  } catch {
    message.error('开发人员列表加载失败，请刷新重试');
  }
}

/**
 * 提交前校验。只校验必填，格式问题交给后端的业务校验。
 *
 * @returns 提示文本，通过返回 null
 */
function 校验(): null | string {
  for (const 字段 of 当前字段.value) {
    const 值 = 表单值.value[字段.key];
    if (字段.required && (值 === '' || 值 === null || 值 === undefined)) {
      return `请填写${字段.label}`;
    }
  }
  if (需要原因.value && !原因.value) {
    return '请选择退回原因';
  }
  return null;
}

/** 提交动作 */
function 提交() {
  const 提示 = 校验();
  if (提示) {
    message.warning(提示);
    return;
  }
  if (!当前动作.value) {
    return;
  }

  const 附加: Record<string, unknown> = {};
  for (const 字段 of 当前字段.value) {
    const 值 = 表单值.value[字段.key];
    if (值 !== '' && 值 !== null && 值 !== undefined) {
      附加[字段.key] = 值;
    }
  }

  emit('submit', {
    action: 当前动作.value.action,
    expectStatus: props.status,
    extra: 附加,
    reasonCode: 原因.value ?? undefined,
    remark: 备注.value || undefined,
  });
}

/** 供父组件在提交成功后关闭弹窗 */
function 关闭弹窗() {
  弹窗可见.value = false;
}

defineExpose({ 关闭弹窗 });
</script>

<template>
  <div>
    <Space wrap>
      <Tooltip v-for="(动作, 序号) in actions" :key="动作.action" :title="阻塞说明(动作)">
        <span>
          <Button
            :danger="Boolean(动作.returnPoint)"
            :disabled="!动作.enabled"
            :type="序号 === 0 && !动作.returnPoint ? 'primary' : 'default'"
            @click="打开(动作)"
          >
            {{ 动作.label }}
          </Button>
        </span>
      </Tooltip>
    </Space>
    <span v-if="actions.length === 0" class="text-sm text-gray-400">
      当前环节没有你可执行的动作
    </span>

    <Modal
      v-model:open="弹窗可见"
      :confirm-loading="submitting"
      :title="当前动作?.label"
      :width="520"
      destroy-on-close
      ok-text="确认提交"
      @ok="提交"
    >
      <Form layout="vertical" class="pt-2">
        <FormItem v-if="需要原因" label="退回原因" required>
          <Select
            v-model:value="原因"
            :options="原因选项"
            placeholder="请选择退回原因（必填，不允许只写「不行」）"
          />
        </FormItem>

        <FormItem
          v-for="字段 in 当前字段"
          :key="字段.key"
          :help="字段.tip"
          :label="字段.label"
          :required="字段.required"
        >
          <Select
            v-if="字段.type === 'select'"
            v-model:value="表单值[字段.key]"
            :options="字段.options"
            :placeholder="字段.placeholder ?? `请选择${字段.label}`"
            show-search
            option-filter-prop="label"
          />
          <InputNumber
            v-else-if="字段.type === 'number'"
            v-model:value="表单值[字段.key]"
            :min="0.5"
            :step="0.5"
            class="w-full"
            placeholder="请输入小时数"
          />
          <Rate v-else-if="字段.type === 'rate'" v-model:value="表单值[字段.key]" />
          <DatePicker
            v-else-if="字段.type === 'datetime'"
            v-model:value="表单值[字段.key]"
            class="w-full"
            show-time
            value-format="YYYY-MM-DD HH:mm:ss"
          />
          <Textarea
            v-else-if="字段.type === 'textarea'"
            v-model:value="表单值[字段.key]"
            :rows="3"
            :placeholder="字段.placeholder ?? `请填写${字段.label}`"
          />
          <Input
            v-else
            v-model:value="表单值[字段.key]"
            :placeholder="字段.placeholder ?? `请填写${字段.label}`"
          />
        </FormItem>

        <FormItem label="备注">
          <Textarea v-model:value="备注" :rows="2" placeholder="可补充说明，会记入流转时间线" />
        </FormItem>
      </Form>
    </Modal>
  </div>
</template>
