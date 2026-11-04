import {
  defineOverridesPreferences,
  definePreferencesExtension,
} from '@vben/preferences';

interface WebAntdPreferencesExtension {
  defaultTableSize: number;
  enableFormFullscreen: boolean;
  reportTitle: string;
  tenantMode: 'multi' | 'single';
}

/**
 * 品牌版本号。改动 logo、系统名、默认主题、侧边栏默认行为这类「出厂设置」时递增，
 * main.ts 会据此清一次浏览器里的旧偏好缓存。
 * 背景：Vben 的偏好是「缓存优先」，不清缓存的话代码里的新默认值对老用户永远不生效。
 */
export const 品牌版本号 = 'cim-5';

/** 系统名称写死，不依赖 VITE_APP_TITLE：改 .env 后没重启 dev server 也不会显示旧名。 */
export const 系统名称 = 'CIM需求管理系统';

/**
 * @description 项目配置文件
 * 只需要覆盖项目中的一部分配置，不需要的配置不用覆盖，会自动使用默认配置
 * !!! 更改配置后请清空缓存，否则可能不生效
 */
export const overridesPreferences = defineOverridesPreferences({
  // overrides
  app: {
    name: 系统名称,
    // 菜单与路由由后端 /menu/all 动态下发（seed 的 sys_menu），不用前端静态路由
    accessMode: 'backend',
  },
  logo: {
    // 使用上海芯源创新中心 logo（public/logo.png），替换 Vben 默认外链图标
    source: '/logo.png',
  },
  theme: {
    // 系统默认明亮主题；用户仍可在偏好设置里自行切换（缓存优先，不会被每次启动覆盖）
    mode: 'light',
  },
  sidebar: {
    // 两种状态都由底部按钮切换、且都保持固定：
    //   收起 = 64px 窄条，只显示 logo 与顶级菜单图标；
    //   展开 = 224px 完整菜单。
    // Vben 的 expandOnHover 语义与直觉相反：
    //   true  = 悬停既不展开整条、移开也不收起（子菜单以浮层形式弹出）——正是要的「两态固定」；
    //   false = 悬停自动展开整条、移开自动收起——会变成「打开也不固定」。
    collapsed: false,
    collapsedButton: true,
    collapsedShowTitle: false,
    collapseWidth: 64,
    expandOnHover: true,
    // 上次点顶栏按钮可能把整条侧边栏隐藏了（hidden=true 会被缓存），这里复位
    hidden: false,
  },
  copyright: {
    // 页脚版权信息，替换掉 Vben 默认的演示版权
    companyName: '上海芯源创新中心',
    companySiteLink: '',
    date: '2026',
    enable: true,
    icp: '',
  },
});

export const preferencesExtension =
  definePreferencesExtension<WebAntdPreferencesExtension>({
    tabLabel: 'preferences.antd.tabLabel',
    title: 'preferences.antd.title',
    fields: [
      {
        component: 'switch',
        defaultValue: true,
        key: 'enableFormFullscreen',
        label: 'preferences.antd.fields.enableFormFullscreen.label',
        tip: 'preferences.antd.fields.enableFormFullscreen.tip',
      },
      {
        component: 'select',
        defaultValue: 'single',
        key: 'tenantMode',
        label: 'preferences.antd.fields.tenantMode.label',
        options: [
          {
            label: 'preferences.antd.fields.tenantMode.options.single.label',
            value: 'single',
          },
          {
            label: 'preferences.antd.fields.tenantMode.options.multi.label',
            value: 'multi',
          },
        ],
      },
      {
        component: 'number',
        componentProps: {
          max: 200,
          min: 10,
          step: 10,
        },
        defaultValue: 20,
        key: 'defaultTableSize',
        label: 'preferences.antd.fields.defaultTableSize.label',
      },
      {
        component: 'input',
        defaultValue: '',
        key: 'reportTitle',
        label: 'preferences.antd.fields.reportTitle.label',
        placeholder: 'preferences.antd.fields.reportTitle.placeholder',
      },
    ],
  });
