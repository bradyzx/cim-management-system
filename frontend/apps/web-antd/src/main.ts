import { initPreferences } from '@vben/preferences';
import { unmountGlobalLoading } from '@vben/utils';

import { 品牌版本号, overridesPreferences, preferencesExtension } from './preferences';

/**
 * 品牌升级时清一次旧偏好缓存。
 * Vben 的偏好是「缓存优先」：浏览器里存过的 app.name / logo / 侧边栏配置
 * 会盖住代码里的新默认值，导致改了 logo 和系统名却看不到变化。
 * 只在品牌版本号变化时清一次，用户的个人偏好（主题、布局宽度等）之后照常保留。
 */
function 清理过期品牌缓存(namespace: string) {
  const 版本键 = `${namespace}-brand-version`;
  if (localStorage.getItem(版本键) === 品牌版本号) {
    return;
  }
  const 偏好前缀 = `${namespace}-preferences`;
  for (const 键 of Object.keys(localStorage)) {
    if (键.startsWith(偏好前缀)) {
      localStorage.removeItem(键);
    }
  }
  localStorage.setItem(版本键, 品牌版本号);
}

/**
 * 应用初始化完成之后再进行页面加载渲染
 */
async function initApplication() {
  // name用于指定项目唯一标识
  // 用于区分不同项目的偏好设置以及存储数据的key前缀以及其他一些需要隔离的数据
  const env = import.meta.env.PROD ? 'prod' : 'dev';
  const appVersion = import.meta.env.VITE_APP_VERSION;
  const namespace = `${import.meta.env.VITE_APP_NAMESPACE}-${appVersion}-${env}`;

  清理过期品牌缓存(namespace);

  // app偏好设置初始化
  await initPreferences({
    extension: preferencesExtension,
    namespace,
    overrides: overridesPreferences,
  });

  // 启动应用并挂载
  // vue应用主要逻辑及视图
  const { bootstrap } = await import('./bootstrap');
  await bootstrap(namespace);

  // 移除并销毁loading
  unmountGlobalLoading();
}

initApplication();
