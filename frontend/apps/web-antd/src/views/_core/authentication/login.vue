<script lang="ts" setup>
// 登录页：账号 + 密码。
// 去掉了 Vben 演示用的「选择演示账号」下拉和滑块验证码——本系统接的是真实后端，
// 演示账号会诱导误操作，验证码一期不做（03 第四节：用登录限流而不是验证码）。
// 首次登录或密码被管理员重置时，登录成功后会强制弹出改密框，改完才进首页。
import type { VbenFormSchema } from '@vben/common-ui';

import { computed, ref } from 'vue';

import { AuthenticationLogin, z } from '@vben/common-ui';
import { $t } from '@vben/locales';
import { Form, FormItem, Input, Modal, message } from 'ant-design-vue';

import { useAuthStore } from '#/store';
import { 系统名称 } from '#/preferences';

defineOptions({ name: 'Login' });

const authStore = useAuthStore();

const formSchema = computed((): VbenFormSchema[] => {
  return [
    {
      component: 'VbenInput',
      componentProps: {
        placeholder: $t('authentication.usernameTip'),
      },
      fieldName: 'username',
      label: $t('authentication.username'),
      rules: z.string().min(1, { message: $t('authentication.usernameTip') }),
    },
    {
      component: 'VbenInputPassword',
      componentProps: {
        placeholder: $t('authentication.password'),
      },
      fieldName: 'password',
      label: $t('authentication.password'),
      rules: z.string().min(1, { message: $t('authentication.passwordTip') }),
    },
  ];
});

// ---------------- 强制改密 ----------------

const 改密弹窗 = ref(false);
const 原密码 = ref('');
const 新密码 = ref('');
const 确认密码 = ref('');

/** 登录表单提交：被要求改密时弹框，否则 authStore 内部已经跳转首页。 */
async function 提交登录(表单值: Record<string, any>) {
  try {
    const 结果 = await authStore.authLogin(表单值);
    if (结果?.needChangePassword) {
      原密码.value = 表单值.password ?? '';
      新密码.value = '';
      确认密码.value = '';
      改密弹窗.value = true;
    }
  } catch {
    // 登录失败的提示由请求拦截器统一弹出，这里不需要重复提示
  }
}

async function 提交改密() {
  if (新密码.value.length < 8) {
    message.warning('新密码至少 8 位');
    return;
  }
  if (新密码.value !== 确认密码.value) {
    message.warning('两次输入的新密码不一致');
    return;
  }
  try {
    await authStore.changePasswordAndEnter(原密码.value, 新密码.value);
    改密弹窗.value = false;
    message.success('密码已修改');
  } catch {
    // 原密码错误、新密码强度不足等由拦截器提示，弹窗保持打开让用户重试
  }
}
</script>

<template>
  <div>
    <AuthenticationLogin
      :form-schema="formSchema"
      :loading="authStore.loginLoading"
      :show-code-login="false"
      :show-forget-password="false"
      :show-qrcode-login="false"
      :show-register="false"
      :show-third-party-login="false"
      :title="系统名称"
      subtitle="芯源中试平台 · 需求全生命周期管理"
      @submit="提交登录"
    />

    <!-- 强制改密：不允许关闭、不允许点遮罩关闭，只能改完或退出登录 -->
    <Modal
      v-model:open="改密弹窗"
      :closable="false"
      :confirm-loading="authStore.loginLoading"
      :keyboard="false"
      :mask-closable="false"
      cancel-text="退出登录"
      ok-text="修改并进入系统"
      title="首次登录，请修改密码"
      @cancel="authStore.logout(true)"
      @ok="提交改密"
    >
      <p class="mb-3 text-xs opacity-70">
        账号初始密码是统一口令，必须先改成自己的密码才能进入系统。
      </p>
      <Form layout="vertical">
        <FormItem label="原密码" required>
          <Input.Password v-model:value="原密码" />
        </FormItem>
        <FormItem label="新密码" required extra="至少 8 位，不能是纯数字">
          <Input.Password v-model:value="新密码" />
        </FormItem>
        <FormItem label="确认新密码" required>
          <Input.Password v-model:value="确认密码" />
        </FormItem>
      </Form>
    </Modal>
  </div>
</template>
