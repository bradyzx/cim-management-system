<script lang="ts" setup>
/**
 * 技术架构：把 03-技术方案与约束.md 的选型、分层、数据纪律、AI 降级、
 * 请求链路、版本矩阵与一期/二期边界摊成一页，供评审与讲解时直接翻。
 *
 * 页面是纯静态展示，不连任何接口：版本号以 pom 与 package.json 实测为准，
 * 改选型时改这一份数据，别在别的页面再抄一份。
 *
 * 左侧目录吸附在滚动区顶部，点一项换一屏；每屏独立成段，
 * 讲解时一屏一个话题，不会半屏停在两张图中间。
 */
import { ref } from 'vue';

import { Page } from '@vben/common-ui';

import { Alert, Card, Table, Tag } from 'ant-design-vue';

// ---------------- 左侧目录 ----------------

/** 目录：key 与下方各段的 v-if 对应，no 只做编号展示 */
const SECTIONS = [
  { key: 'overview', no: '01', label: '选型总览' },
  { key: 'arch', no: '02', label: '总体架构' },
  { key: 'frontend', no: '03', label: '前端' },
  { key: 'backend', no: '04', label: '后端' },
  { key: 'data', no: '05', label: '数据层' },
  { key: 'ai', no: '06', label: 'AI 与集成' },
  { key: 'chain', no: '07', label: '运行链路' },
  { key: 'versions', no: '08', label: '版本矩阵' },
  { key: 'boundary', no: '09', label: '边界' },
];

/** 当前展示的目录项，默认第一项 */
const active = ref('overview');

// ---------------- 01 选型总览 ----------------

/** 十二项选型：层 → 选了谁（含版本）→ 为什么是它 */
const 选型 = [
  { layer: '客户端', choice: 'Edge 156 / Chrome 157', reason: '内网统一 Chromium 内核，无 IE 包袱，不做降级构建' },
  { layer: '前端框架', choice: 'Vue 3.5.41 · Vben 5.7.0', reason: '指定模板，pnpm 单仓多应用基座' },
  { layer: '工程化', choice: 'pnpm 11.16 · Turbo 2.10 · Vite 8.3', reason: '单仓构建，冷启动与热更都在秒级' },
  { layer: '语言', choice: 'TypeScript 6.0 · script setup', reason: '类型约束在编译期暴露，禁 Options API' },
  { layer: 'UI 与样式', choice: 'Ant Design Vue 4.2.6 · Tailwind 4.3.3', reason: '企业级表格与表单开箱即用，五套变体只用 web-antd' },
  { layer: '状态与路由', choice: 'Pinia 4.0 · vue-router 5.2', reason: '与 Vben 既有约定一致，不为新页面另起一套' },
  { layer: '后端框架', choice: 'Spring Boot 3.5.6（编译 17 · 运行 21）', reason: '单体单进程，内网低运维，50 人规模足够' },
  { layer: '持久层', choice: 'MyBatis-Plus 3.5.12 + jsqlparser', reason: '分页方言按 DbType 自动切换，换库不改业务代码' },
  { layer: '数据库', choice: 'SQLite 3.46.1.3（WAL）→ Oracle', reason: '先零依赖上线，再迁企业库，双份 DDL 同源' },
  { layer: '鉴权', choice: 'JJWT 0.12.6 · jBCrypt 0.4', reason: '一个拦截器加自签 Token，不引 Spring Security' },
  { layer: 'AI 分析', choice: '自研 AnalysisProvider（规则版一期）', reason: '分词 + 权重 + 同义词，零外部依赖；LLM 走配置增强' },
  { layer: '部署形态', choice: '5666 / 8081 · context-path /api', reason: '前端静态包 + 后端 jar，附件落盘，无中间件' },
];

const 选型列 = [
  { dataIndex: 'layer', title: '层', width: 110 },
  { dataIndex: 'choice', title: '选型与版本', width: 320 },
  { dataIndex: 'reason', title: '为什么是它' },
];

// ---------------- 02 总体架构 ----------------

/** 芯片：mono 表示版本号/代码名用等宽字体；hero 表示这一层的主角 */
interface Chip {
  hero?: boolean;
  mono?: boolean;
  text: string;
}

/** 一层主链：chips 是这层用到的技术，flow 是流到下一层的东西 */
interface Layer {
  chips: Chip[];
  code: string;
  flow?: string;
  highlight?: boolean;
  name: string;
}

/** 四层主链：flow 写的是「从这一层流到下一层的是什么」，不是调用关系 */
const 分层: Layer[] = [
  {
    code: 'L0',
    name: '客户端',
    chips: [
      { text: 'Edge 156', mono: true },
      { text: 'Chrome 157', mono: true },
      { text: '办公 PC · 车间终端 · 看板机' },
      { text: '免兼容降级' },
    ],
    flow: 'HTTPS · 静态资源 gzip',
  },
  {
    code: 'L1',
    name: '前端',
    chips: [
      { text: 'Vue 3.5 SPA · Vben web-antd' },
      { text: 'Pinia 4.0', mono: true },
      { text: 'vue-router 5.2', mono: true },
      { text: 'RequestClient', mono: true },
      { text: 'Tailwind 4.3', mono: true },
      { text: 'Vite 8.3', mono: true },
      { text: '三件套：视图 + 路由模块 + i18n' },
    ],
    flow: 'HTTP JSON · /api · Bearer Token',
  },
  {
    code: 'L2',
    name: '后端',
    highlight: true,
    chips: [
      { text: 'Spring Boot 3.5.6 · 内嵌 Tomcat 8081', hero: true },
      { text: 'Controller' },
      { text: 'Service 事务' },
      { text: 'Mapper' },
      { text: 'JwtInterceptor', mono: true },
      { text: 'AnalysisProvider', mono: true },
      { text: 'NotifyProvider', mono: true },
      { text: '@Scheduled', mono: true },
      { text: 'R<T> 统一响应 · 全局异常' },
    ],
    flow: 'JDBC · SQL（分页方言 DbType）',
  },
  {
    code: 'L3',
    name: '数据',
    chips: [
      { text: 'SQLite 3.46 · WAL · ./data/cim.db', mono: true },
      { text: '附件落文件系统，库存相对路径' },
      { text: '45 表 · 双份 DDL 同源' },
      { text: 'Oracle · 二期，只改建表脚本与一行方言' },
    ],
  },
];

/** 外部集成：一期全部预留，接口与字段已占位但不实现 */
const 外部集成 = [
  { name: 'AD 域账号', detail: '只解决「你是谁」，AuthProvider 预留' },
  { name: 'HR 主数据', detail: '人员 / 部门 / 岗位同步，岗位不直接当权限' },
  { name: '钉钉机器人', detail: '通知通道，站内信先顶上' },
  { name: '大模型服务', detail: 'LOCAL / INTRANET / CLOUD 三档，只改配置' },
];

// ---------------- 03 前端 ----------------

/** 前端技术清单：四组各管一件事，版本号同 01 的表 */
const 前端分组 = [
  {
    en: 'BUILD',
    items: ['pnpm 11.16 workspaces', 'Turborepo 2.10', 'Vite 8.3', 'Node 22.22.2', '内部依赖 workspace:*', '第三方版本集中 catalog'],
    name: '工程化',
  },
  {
    en: 'VUE',
    items: ['Vue 3.5.41 SFC', 'vue-router 5.2', 'Pinia 4.0', 'script setup + Composition API', '禁 Options API'],
    name: '框架与状态',
  },
  {
    en: 'VIEW',
    items: ['Ant Design Vue 4.2.6', 'Tailwind 4.3.3', '路径别名 #/* · @vben/*', 'antd 组件上不用 Tailwind 布局类'],
    name: 'UI 与样式',
  },
  {
    en: 'API',
    items: ['RequestClient 统一封装', 'src/api/<域>/', '不各自 new axios', '$t() 全量国际化'],
    name: '请求与约定',
  },
];

// ---------------- 04 后端 ----------------

/** 主动不引入的依赖：每一项都是评估后放弃，不是遗漏 */
const 未引入 = [
  { name: 'Spring Security', why: '一个 JwtInterceptor 足够，权限判定走配置表' },
  { name: 'Lombok', why: 'IDE 与编译插件版本耦合，生成代码调试时看不见' },
  { name: 'Redis', why: '50 人规模，Token 无状态校验不依赖缓存' },
  { name: 'MQ / 注册中心', why: '单进程内异步用线程池与 @Scheduled' },
];

/** 后端四层职责：减掉依赖之后剩下的全部 */
const 后端四层 = [
  { tag: 'W', name: 'Web 层 · 接请求', detail: 'starter-web 内嵌 Tomcat · 8081 · context-path /api · 开发期 CORS 放行 5666' },
  { tag: 'B', name: '业务层 · 管规则', detail: 'Service 事务边界 · R<T> 与 PageResult 统一响应 · GlobalExceptionHandler · 状态机引擎集中在 TicketFlowService' },
  { tag: 'P', name: '持久层 · 换库不改码', detail: 'MyBatis-Plus 分页插件 · 按 DbType 切 SQLite / Oracle · map-underscore-to-camel-case' },
  { tag: 'D', name: '数据层 · 双份 DDL', detail: 'schema-sqlite.sql 与 schema-oracle.sql 字段一一对应，check_schema.py 校验一致；附件存文件系统，库里只放相对路径' },
];

// ---------------- 05 数据层 ----------------

/** 字段类型映射：两份建表脚本逐字段对照写，不是事后翻译 */
const 类型映射 = [
  { logic: '主键 / 编码', oracle: 'VARCHAR2(64)', sqlite: 'TEXT' },
  { logic: '短文本', oracle: 'VARCHAR2(n)', sqlite: 'TEXT' },
  { logic: '长文本 / 富文本', oracle: 'CLOB', sqlite: 'TEXT' },
  { logic: '整数', oracle: 'NUMBER(10)', sqlite: 'INTEGER' },
  { logic: '布尔', oracle: 'NUMBER(1)', sqlite: 'INTEGER（0/1）' },
  { logic: '工时 / 金额', oracle: 'NUMBER(10,2)', sqlite: 'REAL' },
  { logic: '日期时间', oracle: 'TIMESTAMP(6)', sqlite: 'TEXT（ISO8601）' },
  { logic: 'JSON 快照', oracle: 'CLOB', sqlite: 'TEXT' },
];

const 类型映射列 = [
  { dataIndex: 'logic', title: '逻辑类型' },
  { dataIndex: 'sqlite', title: 'SQLite', width: 150 },
  { dataIndex: 'oracle', title: 'Oracle', width: 170 },
];

/** 换库纪律：违反任何一条，迁库时都是逐条改代码 */
const 数据纪律 = [
  { name: '主键用 32 位 UUID 字符串，不用自增', why: 'SQLite 的 AUTOINCREMENT 与 Oracle 的 SEQUENCE 语义不同；业务号单独一列（REQ-2026-000123）。雪花 ID 在 JS 里会丢精度，主键必须是 String' },
  { name: '可空字段一律写 NULL，绝不明写空串', why: "Oracle 里 '' = NULL，长度校验、IS NOT NULL、唯一索引全部会因此出错" },
  { name: '时间由应用层生成', why: '不用 DEFAULT CURRENT_TIMESTAMP、不用触发器，换库行为完全一致' },
  { name: '不写方言语法与手写 LIMIT', why: "分页交给 MyBatis-Plus 插件；不用 INSERT OR REPLACE、PRAGMA、datetime('now')" },
];

// ---------------- 06 AI 与集成 ----------------

/** 提单与分析解耦：降级两级，任何一级都不阻塞流转 */
const AI降级 = [
  { name: '提交单据 → 先落库', why: '状态 SUBMITTED，此时提单已经成功' },
  { name: '异步触发分析', why: '状态 AI_ANALYZING，单线程队列、限 2 并发，超时不反压提交' },
  { name: '一级降级：规则版', why: 'LLM 超时或输出非 JSON，回落到分词 + 权重 + 同义词，标 RULE_FALLBACK' },
  { name: '二级降级：留标记放行', why: '两级都失败置 REQ_DOC_DRAFTED 并标 ai_degraded=1，人工照常处理' },
];

/** 大模型三档：三档不是三套代码，是三行配置 */
const 模型三档 = [
  { base: '127.0.0.1:11434/v1', preset: 'LOCAL' },
  { base: '内网模型服务 /v1', preset: 'INTRANET' },
  { base: 'api.xxx.com/v1', preset: 'CLOUD' },
];

const 三档列 = [
  { dataIndex: 'preset', title: 'preset', width: 110 },
  { dataIndex: 'base', title: 'base-url' },
];

// ---------------- 07 运行链路 ----------------

/** 七跳：intercept 标出仅有的两处拦截（蓝框），其余一路放行 */
const 链路 = [
  { desc: '内网 Edge 发起', intercept: false, name: '浏览器', no: '01' },
  { desc: '组装请求', intercept: false, name: 'Vue SPA', no: '02' },
  { desc: '/api → 8081', intercept: false, name: 'Vite 代理', no: '03' },
  { desc: '校验 Token', intercept: true, name: 'JWT 拦截', no: '04' },
  { desc: '接收 DTO', intercept: false, name: 'Controller', no: '05' },
  { desc: '规则与取数', intercept: false, name: 'Service · Mapper', no: '06' },
  { desc: 'WAL 读写', intercept: false, name: 'SQLite', no: '07' },
];

/** 请求 / 响应示例：讲「业务失败也是 HTTP 200」这一条 */
const 链路示例 = `POST /api/ticket/{id}/action
Authorization: Bearer <accessToken>
Content-Type: application/json

{ "action": "SUBMIT_DESIGN", "expectStatus": "DESIGNING",
  "requestId": "9f2c1e7a-...", "remark": "设计文档已上传" }

── 前置不满足，HTTP 仍然是 200 ─────────────────
{ "code": -1, "bizCode": "ATTACH_REQUIRED",
  "message": "本环节需上传：功能设计文档 FDS",
  "details": [ { "attachType": "FDS", "uploaded": 0 } ] }

── 通过 ────────────────────────────────────────
{ "code": 0, "data": { "fromStatus": "DESIGNING",
    "toStatus": "DSGN_REVIEW_IT", "statusLabel": "设计·IT 评审" } }`;

/** 链路上的三条统一收口，前端组件层面只认这三种形状 */
const 链路收口 = [
  { detail: '401 / 403 是仅有的两个用 HTTP 状态码表达的失败；业务错误统一 code:-1 配 200，前端只判 code。', name: '拦截点一 · 鉴权' },
  { detail: '上传接口必须请求级覆盖 multipart/form-data，boundary 由浏览器补；实例默认的 JSON 头会让后端报「缺少必填参数」。', name: '拦截点二 · 上传' },
  { detail: '响应体 R<T>、分页字段 items / total、时间由服务端赋值。', name: '三处统一收口' },
];

// ---------------- 08 版本矩阵 ----------------

/** 三列版本：编译目标与运行版本分开写，评审时按列核对 */
const 版本分组 = [
  {
    key: 'runtime',
    rows: [
      ['JDK', 'Temurin 21.0.13 LTS · 编译目标 17'],
      ['Node · pnpm', '22.22.2 · 11.16'],
      ['Maven', '3.9.9'],
      ['浏览器基线', 'Edge 156 · Chrome 157'],
      ['端口', '前端 5666 · 后端 8081 · /api'],
    ],
    title: '运行时与工具',
  },
  {
    key: 'frontend',
    rows: [
      ['Vben Admin', '根包 5.7.0 · web-antd 5.8.0'],
      ['Vue · 路由 · 状态', '3.5.41 · 5.2 · 4.0'],
      ['Vite · TypeScript', '8.3 · 6.0'],
      ['Ant Design Vue', '4.2.6'],
      ['Tailwind · Turborepo', '4.3.3 · 2.10'],
    ],
    title: '前端',
  },
  {
    key: 'backend',
    rows: [
      ['Spring Boot', '3.5.6'],
      ['MyBatis-Plus', '3.5.12'],
      ['SQLite JDBC', '3.46.1.3'],
      ['JJWT · jBCrypt', '0.12.6 · 0.4'],
      ['附件上限', '50 MB / 单次 60 MB'],
    ],
    title: '后端',
  },
];

// ---------------- 09 边界 ----------------

/** 三条纪律：换库、附件、密钥，剩下的都是它们的具体化 */
const 工程纪律 = [
  { detail: '主键应用层生成、时间应用层赋值、分页走插件，脚本里不出现任何数据库特有语法。', name: '换库只改方言配置', no: '01' },
  { detail: '文件落文件系统、按节点归档并带类型；库里存路径与校验值，换存储不动数据。', name: '附件库里只存相对路径', no: '02' },
  { detail: '配置里写空串，实际值运行时注入。AI 只给建议，任何模型故障都不能阻断提单与流转。', name: '密钥走环境变量', no: '03' },
];

/** 已落地（实线）与二期预留（虚线）的对照，评审时据此拍资源 */
const 已落地 = [
  { area: 'FRONTEND', detail: 'Vben web-antd 基座 + 8 个业务页面骨架' },
  { area: 'BACKEND', detail: 'Spring Boot 单体 · 状态机引擎 · JWT 鉴权' },
  { area: 'DATA', detail: '45 表双份 DDL · 附件节点归档 · 种子数据' },
];

const 二期预留 = [
  { area: 'DATABASE', detail: '迁 Oracle —— 兼容纪律已从第一天守起' },
  { area: 'AI', detail: '切 LLM 三档 · 向量检索 —— 接口已抽象，只加实现类' },
  { area: 'INTEGRATION', detail: 'AD 登录 · HR 同步 · 钉钉通知 —— 字段与接口已占位' },
];
</script>

<template>
  <Page title="技术架构" description="前端 Vue 3 + Vben Admin，后端 Spring Boot 单体，数据先 SQLite 后 Oracle">
    <div class="flex items-start gap-4">
      <!-- 左侧目录：吸附在滚动区顶部。position 用内联样式写 sticky，
           antd 给 .ant-card 注入的 position: relative 会盖掉 Tailwind 的 sticky 类。 -->
      <Card class="w-[172px] shrink-0" :body-style="{ padding: '8px' }" style="position: sticky; top: 8px">
        <div class="flex flex-col gap-1">
          <button
            v-for="s in SECTIONS"
            :key="s.key"
            type="button"
            class="flex items-baseline gap-2 rounded-md px-3 py-2 text-left text-[13px] transition-colors"
            :class="
              active === s.key
                ? 'bg-primary/10 font-semibold text-primary'
                : 'text-gray-600 hover:bg-gray-100 dark:text-gray-300 dark:hover:bg-gray-700/40'
            "
            @click="active = s.key"
          >
            <span class="font-mono text-[11px] text-gray-400">{{ s.no }}</span>
            <span>{{ s.label }}</span>
          </button>
        </div>
      </Card>

      <div class="flex min-w-0 flex-1 flex-col gap-4">
        <!-- 01 选型总览 -->
        <template v-if="active === 'overview'">
          <Card>
            <template #title>十二项选型，一张表看完</template>
            <template #extra>
              <span class="text-xs text-gray-400">版本号来自 pom 与 package.json 实测</span>
            </template>
            <Table
              :columns="选型列"
              :data-source="选型"
              :pagination="false"
              bordered
              size="small"
            />
          </Card>
          <Alert
            show-icon
            type="info"
            message="Oracle、大模型、AD / HR、钉钉四项属于预留，没有混进「已选」，统一放在「边界」一节单独交代。"
          />
        </template>

        <!-- 02 总体架构 -->
        <template v-else-if="active === 'arch'">
          <Card>
            <template #title>四层主链，一条路走到底</template>
            <div class="flex items-stretch gap-4">
              <div class="flex min-w-0 flex-1 flex-col">
                <template v-for="l in 分层" :key="l.code">
                  <div
                    class="flex items-stretch overflow-hidden rounded-md border"
                    :class="l.highlight ? 'border-primary/40 bg-primary/[0.02] dark:border-primary/50' : 'border-gray-200 dark:border-gray-700'"
                  >
                    <div
                      class="flex w-[84px] shrink-0 flex-col items-center justify-center gap-0.5 border-r"
                      :class="
                        l.highlight
                          ? 'border-primary/40 bg-primary text-white'
                          : 'border-gray-200 bg-gray-100 dark:border-gray-700 dark:bg-gray-800'
                      "
                    >
                      <span class="font-mono text-[10px] opacity-70">{{ l.code }}</span>
                      <span class="text-sm font-semibold">{{ l.name }}</span>
                    </div>
                    <div class="flex min-w-0 flex-1 flex-wrap items-center gap-1.5 p-3">
                      <Tag
                        v-for="c in l.chips"
                        :key="c.text"
                        :color="c.hero ? 'processing' : undefined"
                        :style="c.mono ? { fontFamily: 'ui-monospace, Menlo, monospace' } : undefined"
                      >
                        {{ c.text }}
                      </Tag>
                    </div>
                  </div>
                  <div
                    v-if="l.flow"
                    class="flex items-center gap-2 py-1.5 pl-[42px] text-xs text-gray-400"
                  >
                    <span>↓</span>
                    <span>{{ l.flow }}</span>
                  </div>
                </template>
              </div>

              <!-- 外部集成：虚线表示一期不实现，别当成已完成 -->
              <div
                class="flex w-[264px] shrink-0 flex-col rounded-lg border border-dashed border-gray-300 p-4 dark:border-gray-600"
              >
                <div class="mb-1 text-[13px] font-semibold">外部集成 · 一期全部预留</div>
                <div
                  v-for="e in 外部集成"
                  :key="e.name"
                  class="border-b border-gray-100 py-2.5 last:border-0 dark:border-gray-700"
                >
                  <div class="text-[13px] font-medium">{{ e.name }}</div>
                  <div class="mt-0.5 text-xs leading-relaxed text-gray-500 dark:text-gray-400">
                    {{ e.detail }}
                  </div>
                </div>
              </div>
            </div>
          </Card>
        </template>

        <!-- 03 前端 -->
        <template v-else-if="active === 'frontend'">
          <Card>
            <template #title>Vben 单体仓库，只用 web-antd</template>
            <template #extra>
              <span class="text-xs text-gray-400">五套 UI 变体只启用一套，Nitro Mock 已关闭，直连 Java 后端</span>
            </template>
            <div class="flex flex-col divide-y divide-gray-100 dark:divide-gray-700">
              <div
                v-for="g in 前端分组"
                :key="g.name"
                class="flex items-center gap-4 py-3 first:pt-0 last:pb-0"
              >
                <div class="w-[118px] shrink-0">
                  <div class="text-[13px] font-semibold">{{ g.name }}</div>
                  <div class="font-mono text-[10px] text-gray-400">{{ g.en }}</div>
                </div>
                <div class="flex min-w-0 flex-1 flex-wrap gap-1.5">
                  <Tag v-for="i in g.items" :key="i">{{ i }}</Tag>
                </div>
              </div>
            </div>
          </Card>
          <Alert
            show-icon
            type="warning"
            message="新增一个页面 = 视图组件 + 路由模块 + i18n key，三件套缺一不可。"
          />
        </template>

        <!-- 04 后端 -->
        <template v-else-if="active === 'backend'">
          <div class="flex items-stretch gap-4">
            <Card class="w-[330px] shrink-0">
              <template #title>没有引入什么</template>
              <div class="mb-1 text-xs text-gray-400">每一项都是评估后主动放弃，不是遗漏</div>
              <div
                v-for="(n, i) in 未引入"
                :key="n.name"
                class="border-b border-gray-100 py-2.5 last:border-0 dark:border-gray-700"
              >
                <div class="flex gap-2">
                  <span class="font-mono text-xs text-gray-400">
                    {{ String(i + 1).padStart(2, '0') }}
                  </span>
                  <span class="text-[13px] font-semibold">{{ n.name }}</span>
                </div>
                <div class="mt-0.5 pl-7 text-xs leading-relaxed text-gray-500 dark:text-gray-400">
                  {{ n.why }}
                </div>
              </div>
              <div class="mt-3 flex flex-wrap gap-1.5">
                <Tag>编译 17</Tag>
                <Tag>运行 21</Tag>
                <Tag>Maven 3.9.9</Tag>
              </div>
            </Card>

            <Card class="min-w-0 flex-1">
              <template #title>四层职责</template>
              <div
                v-for="b in 后端四层"
                :key="b.tag"
                class="flex gap-3 border-b border-gray-100 py-3 last:border-0 dark:border-gray-700"
              >
                <span
                  class="flex h-6 w-6 shrink-0 items-center justify-center rounded-full bg-primary/10 font-mono text-xs font-bold text-primary"
                >
                  {{ b.tag }}
                </span>
                <div class="min-w-0">
                  <div class="text-[13px] font-semibold">{{ b.name }}</div>
                  <div class="mt-0.5 text-xs leading-relaxed text-gray-500 dark:text-gray-400">
                    {{ b.detail }}
                  </div>
                </div>
              </div>
            </Card>
          </div>
          <Alert
            show-icon
            type="info"
            message="全项目只刻意保留两个抽象层：AnalysisProvider 与 NotifyProvider，各自只暴露一个接口，换引擎、换通道都不动业务代码。"
          />
        </template>

        <!-- 05 数据层 -->
        <template v-else-if="active === 'data'">
          <div class="flex items-stretch gap-4">
            <Card class="min-w-0 flex-1">
              <template #title>字段类型映射</template>
              <div class="mb-2 text-xs text-gray-400">两份建表脚本逐字段对照写，不是事后翻译</div>
              <Table
                :columns="类型映射列"
                :data-source="类型映射"
                :pagination="false"
                bordered
                size="small"
              />
              <div
                class="mt-3 border-t border-gray-100 pt-3 text-xs leading-relaxed text-gray-500 dark:border-gray-700 dark:text-gray-400"
              >
                纯日期列（不含时间）存 VARCHAR2(10) 的 ISO8601 字符串而非 DATE：字符串的字典序等于时间序，两库排序行为一致；Oracle
                的 DATE 隐含时分秒，等值比较常常查不出数据。
              </div>
            </Card>

            <Card class="min-w-0 flex-1">
              <template #title>四条反直觉的纪律</template>
              <div class="mb-1 text-xs text-gray-400">违反任何一条，迁库时都是逐条改代码</div>
              <div
                v-for="(d, i) in 数据纪律"
                :key="d.name"
                class="border-b border-gray-100 py-2.5 last:border-0 dark:border-gray-700"
              >
                <div class="flex gap-2">
                  <span class="font-mono text-xs text-gray-400">
                    {{ String(i + 1).padStart(2, '0') }}
                  </span>
                  <span class="text-[13px] font-semibold">{{ d.name }}</span>
                </div>
                <div class="mt-0.5 pl-7 text-xs leading-relaxed text-gray-500 dark:text-gray-400">
                  {{ d.why }}
                </div>
              </div>
            </Card>
          </div>
          <Alert
            show-icon
            type="info"
            message="换库那天只动两处：两份建表脚本 + 一行 DbType 配置。当前 45 张表，双库结构一致性由脚本校验，不靠人眼。"
          />
        </template>

        <!-- 06 AI 与集成 -->
        <template v-else-if="active === 'ai'">
          <div class="flex items-stretch gap-4">
            <Card class="min-w-0 flex-1">
              <template #title>提单与分析解耦</template>
              <div class="mb-1 text-xs text-gray-400">降级两级，任何一级都不阻塞流转</div>
              <div
                v-for="(s, i) in AI降级"
                :key="s.name"
                class="border-b border-gray-100 py-2.5 last:border-0 dark:border-gray-700"
              >
                <div class="flex gap-2">
                  <span class="font-mono text-xs text-gray-400">
                    {{ String(i + 1).padStart(2, '0') }}
                  </span>
                  <span class="text-[13px] font-semibold">{{ s.name }}</span>
                </div>
                <div class="mt-0.5 pl-7 text-xs leading-relaxed text-gray-500 dark:text-gray-400">
                  {{ s.why }}
                </div>
              </div>
              <div class="mt-3 flex flex-wrap items-center gap-1.5">
                <Tag>AnalysisProvider</Tag>
                <span class="text-xs text-gray-500 dark:text-gray-400">
                  唯一出口，业务层不认底层是规则还是 LLM
                </span>
              </div>
            </Card>

            <Card class="w-[400px] shrink-0">
              <template #title>大模型三档接入</template>
              <div class="mb-2 text-xs text-gray-400">三档不是三套代码，是三行配置</div>
              <Table
                :columns="三档列"
                :data-source="模型三档"
                :pagination="false"
                bordered
                size="small"
              />
              <div class="mt-2 text-xs leading-relaxed text-gray-500 dark:text-gray-400">
                三者都提供 OpenAI 兼容的 /v1/chat/completions，切档只改 preset 与 base-url。
              </div>
              <div
                class="mt-3 border-t border-gray-100 pt-3 text-xs leading-relaxed text-gray-500 dark:border-gray-700 dark:text-gray-400"
              >
                一期 enabled: false，规则版就能跑完整流程；模型准备好再打开开关。通知侧同理：站内信先落地，钉钉与邮件留空壳。
              </div>
            </Card>
          </div>
          <Alert
            show-icon
            type="warning"
            message="三条硬纪律：密钥只走环境变量不进 git；提示词外置并记录 prompt_version；模型超时不许反压提单能力。"
          />
        </template>

        <!-- 07 运行链路 -->
        <template v-else-if="active === 'chain'">
          <Card>
            <template #title>一次请求：七跳，两处拦截</template>
            <div class="flex items-center gap-2">
              <template v-for="(n, i) in 链路" :key="n.no">
                <div
                  class="flex min-w-0 flex-1 flex-col gap-1 rounded-md border px-3 py-2.5"
                  :class="
                    n.intercept
                      ? 'border-primary/50 bg-primary/5 dark:border-primary/60 dark:bg-primary/10'
                      : 'border-gray-200 dark:border-gray-700'
                  "
                >
                  <span class="font-mono text-[10px] text-gray-400">{{ n.no }}</span>
                  <span class="text-[13px] font-semibold">{{ n.name }}</span>
                  <span class="text-[11px] text-gray-500 dark:text-gray-400">{{ n.desc }}</span>
                </div>
                <span v-if="i < 链路.length - 1" class="shrink-0 text-gray-300">→</span>
              </template>
            </div>

            <div class="mt-4 flex items-stretch gap-4">
              <pre
                class="m-0 min-w-0 flex-1 overflow-auto rounded-md bg-gray-50 p-4 text-xs leading-relaxed text-gray-700 dark:bg-gray-900 dark:text-gray-300"
                style="font-family: ui-monospace, Menlo, monospace; white-space: pre"
              >{{ 链路示例 }}</pre>
              <div class="flex w-[340px] shrink-0 flex-col gap-3">
                <div
                  v-for="c in 链路收口"
                  :key="c.name"
                  class="rounded-md border border-gray-200 p-3 dark:border-gray-700"
                >
                  <div class="flex items-center gap-2">
                    <span class="h-3 w-[3px] rounded bg-primary"></span>
                    <span class="text-[13px] font-semibold text-primary">{{ c.name }}</span>
                  </div>
                  <div class="mt-1 text-xs leading-relaxed text-gray-500 dark:text-gray-400">
                    {{ c.detail }}
                  </div>
                </div>
              </div>
            </div>
          </Card>
          <Alert
            show-icon
            type="info"
            message="协议一路向下：HTTP 静态资源 → /api JSON（Bearer Token）→ JDBC / SQL。方向全篇统一，不画返回线造成交叉。"
          />
        </template>

        <!-- 08 版本矩阵 -->
        <template v-else-if="active === 'versions'">
          <div class="flex items-stretch gap-4">
            <Card v-for="g in 版本分组" :key="g.key" class="min-w-0 flex-1">
              <template #title>{{ g.title }}</template>
              <div
                v-for="r in g.rows"
                :key="r[0]"
                class="flex items-baseline justify-between gap-3 border-b border-gray-100 py-2 last:border-0 dark:border-gray-700"
              >
                <span class="shrink-0 text-xs text-gray-400">{{ r[0] }}</span>
                <span class="text-right text-[13px] font-medium">{{ r[1] }}</span>
              </div>
            </Card>
          </div>
          <Alert
            show-icon
            type="info"
            message="java.version=17 只是编译目标，进程跑在 JDK 21；Vben 根包与 app 包版本号不一致，对外引用口径待统一。"
          />
        </template>

        <!-- 09 边界 -->
        <template v-else-if="active === 'boundary'">
          <div class="flex items-stretch gap-4">
            <Card v-for="d in 工程纪律" :key="d.no" class="min-w-0 flex-1">
              <div class="font-mono text-sm font-bold text-primary">{{ d.no }}</div>
              <div class="mt-1 text-[13.5px] font-semibold">{{ d.name }}</div>
              <div class="mt-1 text-xs leading-relaxed text-gray-500 dark:text-gray-400">
                {{ d.detail }}
              </div>
            </Card>
          </div>

          <div class="flex items-stretch gap-4">
            <Card class="min-w-0 flex-1">
              <template #title>当前已落地（实线）</template>
              <div
                v-for="p in 已落地"
                :key="p.area"
                class="flex gap-3 border-b border-gray-100 py-2.5 last:border-0 dark:border-gray-700"
              >
                <span class="w-[86px] shrink-0 font-mono text-[11px] text-gray-400">{{ p.area }}</span>
                <span class="text-[13px]">{{ p.detail }}</span>
              </div>
            </Card>
            <Card class="min-w-0 flex-1">
              <template #title>二期预留（虚线）</template>
              <div
                v-for="p in 二期预留"
                :key="p.area"
                class="flex gap-3 border-b border-gray-100 py-2.5 last:border-0 dark:border-gray-700"
              >
                <span class="w-[86px] shrink-0 font-mono text-[11px] text-gray-400">{{ p.area }}</span>
                <span class="text-[13px]">{{ p.detail }}</span>
              </div>
            </Card>
          </div>

          <Alert
            show-icon
            type="warning"
            message="待确认三处：Vben 版本号对外口径 · JDK 17/21 的表述方式 · 附件类型字典是扩项还是按阶段归并。"
          />
        </template>
      </div>
    </div>
  </Page>
</template>
