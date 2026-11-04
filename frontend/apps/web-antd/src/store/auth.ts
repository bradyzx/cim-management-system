import type { Recordable, UserInfo } from '@vben/types';

import { ref } from 'vue';
import { useRouter } from 'vue-router';

import { LOGIN_PATH } from '@vben/constants';
import { preferences } from '@vben/preferences';
import { resetAllStores, useAccessStore, useUserStore } from '@vben/stores';

import { notification } from 'ant-design-vue';
import { defineStore } from 'pinia';

import {
  changePasswordApi,
  getAccessCodesApi,
  getUserInfoApi,
  loginApi,
  logoutApi,
} from '#/api';
import { $t } from '#/locales';

export const useAuthStore = defineStore('auth', () => {
  const accessStore = useAccessStore();
  const userStore = useUserStore();
  const router = useRouter();

  const loginLoading = ref(false);
  /** 首次登录或被管理员重置过密码：必须先改密才放行进系统。 */
  const needChangePassword = ref(false);
  /** 改密成功后要继续执行的登录收尾动作（拉用户信息、跳首页）。 */
  const pendingAfterChange = ref<null | (() => Promise<void>)>(null);

  /**
   * 异步处理登录操作
   * @param params 登录表单数据
   * @returns needChangePassword=true 表示要弹改密框，此时不能跳转
   */
  async function authLogin(
    params: Recordable<any>,
    onSuccess?: () => Promise<void> | void,
  ) {
    let userInfo: null | UserInfo = null;
    try {
      loginLoading.value = true;
      const 登录结果 = await loginApi(params);

      if (!登录结果.accessToken) {
        return { needChangePassword: false, userInfo };
      }
      accessStore.setAccessToken(登录结果.accessToken);

      // 要求改密的账号先停在登录页：token 已生效，改密接口可以直接调用
      if (登录结果.needChangePassword) {
        needChangePassword.value = true;
        pendingAfterChange.value = async () => {
          await 进入系统(onSuccess);
        };
        return { needChangePassword: true, userInfo };
      }

      userInfo = await 进入系统(onSuccess);
      return { needChangePassword: false, userInfo };
    } finally {
      loginLoading.value = false;
    }
  }

  /**
   * 登录收尾：拉用户信息与权限码，再按需跳转。
   * 单独抽出来是为了让「正常登录」与「改密后继续」共用同一段，避免两处各写一遍。
   */
  async function 进入系统(onSuccess?: () => Promise<void> | void) {
    const [fetchUserInfoResult, accessCodes] = await Promise.all([
      fetchUserInfo(),
      getAccessCodesApi(),
    ]);

    userStore.setUserInfo(fetchUserInfoResult);
    accessStore.setAccessCodes(accessCodes);

    if (accessStore.loginExpired) {
      accessStore.setLoginExpired(false);
    } else {
      onSuccess
        ? await onSuccess?.()
        : await router.push(
            fetchUserInfoResult.homePath || preferences.app.defaultHomePath,
          );
    }

    if (fetchUserInfoResult?.realName) {
      notification.success({
        description: `${$t('authentication.loginSuccessDesc')}:${fetchUserInfoResult?.realName}`,
        duration: 3,
        message: $t('authentication.loginSuccess'),
      });
    }
    return fetchUserInfoResult;
  }

  /** 强制改密：改成功接着完成登录；失败由页面捕获后提示，停留在改密框。 */
  async function changePasswordAndEnter(旧密码: string, 新密码: string) {
    loginLoading.value = true;
    try {
      await changePasswordApi({ newPassword: 新密码, oldPassword: 旧密码 });
      needChangePassword.value = false;
      const 后续动作 = pendingAfterChange.value;
      pendingAfterChange.value = null;
      if (后续动作) {
        await 后续动作();
      }
    } finally {
      loginLoading.value = false;
    }
  }

  async function logout(redirect: boolean = true) {
    try {
      await logoutApi();
    } catch {
      // 不做任何处理
    }
    resetAllStores();
    accessStore.setLoginExpired(false);

    // 已经在登录页时不能再带 redirect：此时 currentRoute.fullPath 就是登录页本身，
    // 再编码一层会得到「登录页?redirect=编码后的登录页」，下一次又在这个基础上再包一层，
    // 反复登出会让 URL 逐跳变长，且没有上限。
    // On the login page the current route is the login page itself, so carrying it as
    // `redirect` would nest one more encoded layer on every repeat.
    const currentRoute = router.currentRoute.value;
    const alreadyOnLogin = currentRoute.path === LOGIN_PATH;

    // 回登录页带上当前路由地址
    await router.replace({
      path: LOGIN_PATH,
      query:
        redirect && !alreadyOnLogin
          ? { redirect: encodeURIComponent(currentRoute.fullPath) }
          : {},
    });
  }

  async function fetchUserInfo() {
    const userInfo = await getUserInfoApi();
    userStore.setUserInfo(userInfo);
    return userInfo;
  }

  function $reset() {
    loginLoading.value = false;
    needChangePassword.value = false;
    pendingAfterChange.value = null;
  }

  return {
    $reset,
    authLogin,
    changePasswordAndEnter,
    fetchUserInfo,
    loginLoading,
    logout,
    needChangePassword,
  };
});
