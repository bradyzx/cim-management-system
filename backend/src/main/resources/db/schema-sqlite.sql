-- =====================================================================
-- 需求全生命周期管理系统 · 表结构（SQLite）
-- 版本：v1.7    依据：03-技术方案与约束 v0.12 / 02-流程与退回机制 v0.6 / 04 / 06 / 07 / 08 / 10
--
-- v1.7 变更（v0.7 并行化：三方确认与设计评审不分先后，27 态改 23 态）：
--   1. req_ticket 补 6 列：mit_passed / platform_passed / superior_passed /
--      dsgn_it_passed / dsgn_platform_passed / dsgn_user_passed
--   2. CONF_MIT / CONF_PLATFORM / CONF_SUPERIOR 合并为 CONFIRMING，
--      DSGN_REVIEW_IT / DSGN_REVIEW_PLATFORM / DSGN_REVIEW_USER 合并为 DSGN_REVIEWING，
--      旧 6 码只留字典翻译历史；退回点 ⑦⑩⑪ 合并为 ⑱ RT_CONFS、⑫⑬⑭ 合并为 ⑲ RT_DSGN_REVIEW
--
-- v1.6 变更（对齐 02 v0.6 的 27 态与 17 个退回点，详见 10 号提案）：
--   1. 新建 5 表：req_project / req_system（项目—系统两级归属）、req_doc（AI 需求文档 12 节）、
--      req_ai_clarify（澄清问答）、req_check_item（重点功能点检清单）
--   2. req_ticket 补 12 列：project_id / system_id / mit_skipped / platform_skipped / doc_confirmed_at /
--      run_stable_days / ai_degraded / ai_clarify_round / monitor_note / related_inc_id /
--      uat_waiver / uat_waiver_reason
--   3. sys_dept 补 dept_type：三方确认靠它定位 MIT Leader 与平台负责人，不补就是死单
--   4. req_ticket.released_at 改名 release_at：02/03 全文用 release_at，且它是满 7 天的起算点，
--      一列两名必然出错。注意 req_env_lock.released_at 是「环境锁释放时间」，语义不同，不改
--   5. 两个算法缺口不靠加列解决：满 7 天的 INC 暂停用「区间相减」推导，
--      挂起初点取 req_flow_log 最后一条 SUSPEND 的 operated_at（理由见 10 号提案第六章）
--
-- v1.5 变更（闭合 09 清单第四节 5 个硬缺口中的表结构项）：
--   1. req_flow_log 补 return_point（退回点码）：同一退回点第 3 次升级的判定依据，
--      只看 return_seq 分不清不同退回点各退了几次；按点计次直接对 flow_log 聚合查询，不冗余进主表
--   2. 新建 req_acceptance_criteria（验收标准子表）：验收标准逐条结构化，
--      req_test_case.criteria_index 才有稳定指向，「用例覆盖率 <100% 不许 UAT 通过」的分母才算得出来
--   3. req_attach_rule.status 语义定死为 to_status（流转到达该状态时校验），
--      req_attachment.stage 记上传时的 from_status（注释已写明，代码按此实现）
--   4. SLA 按工作日历逐日累减（SlaCalculator 实现，无表结构变更）
--   5. 动作-角色-前置状态判定矩阵先落代码常量（无表结构变更，二期进 req_flow_action 配置表）
--
-- v1.4 变更：req_ticket 补 9 列，支撑新增的「上级确认 / 需求评审 / 上线评价」三环节——
--   superior_id / superior_confirmed_at / superior_skipped（上级确认，本人是 Leader 时 skipped=1）
--   review_passed_at（需求评审通过，评审材料由 req_attach_rule 强制）
--   eval_result / satisfaction_score / eval_comment / evaluated_at / evaluated_by（上线评价）
--   状态机 16 态 → 19 态，新增 LEADER_CONFIRM / REQ_REVIEW / EVALUATING（见 02 v0.4）
--
-- 全局纪律（换 Oracle 时必须遵守，见 03 第三节）：
--   1. 主键一律 32 位 UUID 字符串，不用自增，不用数字型雪花 ID（JS 会丢精度）
--   2. 时间由应用层生成，不用 DEFAULT CURRENT_TIMESTAMP、不用触发器
--   3. 布尔用 INTEGER 0/1，不建布尔列
--   4. 可空字段一律写 NULL，绝不明写空串 ''（Oracle 里 '' = NULL）
--   5. 不用 INSERT OR REPLACE / ON CONFLICT / datetime('now') / 手写 LIMIT
--   6. WAL 模式在连接串里开，不在脚本里写 PRAGMA
--   7. 不建物理外键，引用完整性由应用层保证 + 定期对账（理由见文件末尾）
--
-- 表总量：45 张（系统 10 / 权限 3 / 业务 16 / 主数据与文档 5 / 配置 11）
-- 列数与表数以 tools/check_schema.py 的实测输出为准，本文不写死（v1.6 起）
-- =====================================================================


-- =====================================================================
-- 一、系统基础（10 张）
-- =====================================================================

-- 用户。不物理删除，离职只置 status=DISABLED（历史单据要留痕）
CREATE TABLE sys_user (
  id                  TEXT PRIMARY KEY,                    -- UUID 32 位
  username            TEXT NOT NULL,                       -- 登录账号，唯一
  password_hash       TEXT,                                -- bcrypt；AD 用户为空
  real_name           TEXT NOT NULL,                       -- 姓名
  nickname            TEXT,
  email               TEXT,
  mobile              TEXT,
  avatar              TEXT,
  auth_type           TEXT NOT NULL DEFAULT 'LOCAL',       -- LOCAL / AD
  external_id         TEXT,                                -- AD 的 DN 或员工号
  employment_type     TEXT NOT NULL DEFAULT 'EMPLOYEE',    -- EMPLOYEE 正式 / INTERN 实习 / CONTRACTOR 外包
  hr_employment_code  TEXT,                                -- HR 原始用工形式码，备查
  primary_dept_id     TEXT,                                -- 主部门，仅用于展示；禁止用于权限计算
  status              TEXT NOT NULL DEFAULT 'ACTIVE',      -- ACTIVE / DISABLED / LOCKED
  must_change_password INTEGER NOT NULL DEFAULT 0,         -- 1 = 首次登录或密码被重置，必须先改密
  remark              TEXT,
  sync_source         TEXT NOT NULL DEFAULT 'LOCAL',       -- LOCAL / HR / AD
  sync_at             TEXT,
  last_login_at       TEXT,
  created_at          TEXT NOT NULL,
  updated_at          TEXT NOT NULL
);
CREATE UNIQUE INDEX uk_user_username ON sys_user (username);
CREATE INDEX idx_user_dept ON sys_user (primary_dept_id);


-- 部门。多层，ancestors 存路径串，如 /1/5/23/（末尾带斜杠）
-- 不物理删除，撤销只置 status=DISABLED（历史单据的 dept_id 要能 join 上）
CREATE TABLE sys_dept (
  id              TEXT PRIMARY KEY,
  dept_code       TEXT NOT NULL,                           -- 部门编码，HR 或手工定，唯一
  dept_name       TEXT NOT NULL,
  parent_id       TEXT,                                    -- 根为 NULL
  ancestors       TEXT NOT NULL DEFAULT '/',               -- 路径，如 /1/5/23/
  dept_level      INTEGER NOT NULL DEFAULT 1,              -- 层级，1 为根，建议 ≤ 6
  leader_user_id  TEXT,                                    -- 部门负责人
  dept_type       TEXT NOT NULL DEFAULT 'BUSINESS',        -- MIT / PLATFORM / BUSINESS / IT。v1.6 新增：三方确认靠它定位确认人
  sort            INTEGER NOT NULL DEFAULT 0,
  status          TEXT NOT NULL DEFAULT 'ACTIVE',          -- ACTIVE / DISABLED
  remark          TEXT,
  external_id     TEXT,                                    -- HR 部门编码
  sync_source     TEXT NOT NULL DEFAULT 'LOCAL',
  sync_at         TEXT,
  created_at      TEXT NOT NULL,
  updated_at      TEXT NOT NULL
);
CREATE UNIQUE INDEX uk_dept_code ON sys_dept (dept_code);
CREATE INDEX idx_dept_parent ON sys_dept (parent_id);
CREATE INDEX idx_dept_ancestors ON sys_dept (ancestors);


-- 人与部门的关系。一人可多部门，权限判断的唯一数据源
CREATE TABLE sys_user_dept (
  id              TEXT PRIMARY KEY,
  user_id         TEXT NOT NULL,
  dept_id         TEXT NOT NULL,
  is_primary      INTEGER NOT NULL DEFAULT 0,              -- 1 = 主部门，每人有且仅有一条
  post_type       TEXT NOT NULL DEFAULT 'PRIMARY',         -- PRIMARY 本职 / SECONDARY 兼岗
  hr_job_code     TEXT,                                    -- 该部门下的岗位编码，用于映射角色
  start_date      TEXT,                                    -- 生效起始
  end_date        TEXT,                                    -- 结束日期；离职/调岗填此列，不删记录
  status          TEXT NOT NULL DEFAULT 'ACTIVE',
  created_at      TEXT NOT NULL,
  updated_at      TEXT NOT NULL
);
CREATE INDEX idx_ud_user ON sys_user_dept (user_id);
CREATE INDEX idx_ud_dept ON sys_user_dept (dept_id);
CREATE UNIQUE INDEX uk_ud_unique ON sys_user_dept (user_id, dept_id, start_date);


-- 角色
CREATE TABLE sys_role (
  id          TEXT PRIMARY KEY,
  role_code   TEXT NOT NULL,                               -- REPORTER / MIT_DEV / MIT_LEADER / DEPT_MANAGER / ADMIN
  role_name   TEXT NOT NULL,
  is_system   INTEGER NOT NULL DEFAULT 0,                  -- 1 = 内置角色，不允许删除
  sort        INTEGER NOT NULL DEFAULT 0,
  status      TEXT NOT NULL DEFAULT 'ACTIVE',
  remark      TEXT,
  created_at  TEXT NOT NULL,
  updated_at  TEXT NOT NULL,
  is_deleted  INTEGER NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX uk_role_code ON sys_role (role_code);


-- 用户角色。dept_id 让角色按部门生效（兼岗关键）
CREATE TABLE sys_user_role (
  id          TEXT PRIMARY KEY,
  user_id     TEXT NOT NULL,
  role_id     TEXT NOT NULL,
  dept_id     TEXT,                                        -- NULL = 全局角色（仅 ADMIN 这类）
  created_at  TEXT NOT NULL
);
CREATE INDEX idx_ur_user ON sys_user_role (user_id);
CREATE UNIQUE INDEX uk_ur_unique ON sys_user_role (user_id, role_id, dept_id);


-- 菜单。type 区分目录/菜单/按钮/内嵌/外链，auth_code 做按钮级权限
CREATE TABLE sys_menu (
  id          TEXT PRIMARY KEY,
  parent_id   TEXT,                                        -- 根为 NULL
  menu_name   TEXT NOT NULL,
  menu_type   TEXT NOT NULL,                               -- DIR / MENU / BUTTON / EMBED / LINK
  path        TEXT,                                        -- 路由地址
  component   TEXT,                                        -- 视图组件路径
  redirect    TEXT,
  auth_code   TEXT,                                        -- 按钮权限码，如 ticket:assign
  icon        TEXT,
  sort        INTEGER NOT NULL DEFAULT 0,
  visible     INTEGER NOT NULL DEFAULT 1,
  keep_alive  INTEGER NOT NULL DEFAULT 1,
  link_url    TEXT,                                        -- 外链地址
  status      TEXT NOT NULL DEFAULT 'ACTIVE',
  created_at  TEXT NOT NULL,
  updated_at  TEXT NOT NULL,
  is_deleted  INTEGER NOT NULL DEFAULT 0
);
CREATE INDEX idx_menu_parent ON sys_menu (parent_id);
CREATE INDEX idx_menu_auth ON sys_menu (auth_code);


-- 角色菜单关联
CREATE TABLE sys_role_menu (
  id          TEXT PRIMARY KEY,
  role_id     TEXT NOT NULL,
  menu_id     TEXT NOT NULL,
  created_at  TEXT NOT NULL
);
CREATE UNIQUE INDEX uk_rm_unique ON sys_role_menu (role_id, menu_id);


-- 字典类型
CREATE TABLE sys_dict_type (
  id          TEXT PRIMARY KEY,
  dict_type   TEXT NOT NULL,
  dict_name   TEXT NOT NULL,
  remark      TEXT,
  status      TEXT NOT NULL DEFAULT 'ACTIVE',
  created_at  TEXT NOT NULL,
  updated_at  TEXT NOT NULL,
  is_deleted  INTEGER NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX uk_dict_type ON sys_dict_type (dict_type);


-- 字典数据
CREATE TABLE sys_dict_data (
  id          TEXT PRIMARY KEY,
  dict_type   TEXT NOT NULL,
  dict_label  TEXT NOT NULL,
  dict_value  TEXT NOT NULL,
  css_class   TEXT,                                        -- 前端标签颜色
  sort        INTEGER NOT NULL DEFAULT 0,
  status      TEXT NOT NULL DEFAULT 'ACTIVE',
  remark      TEXT,
  created_at  TEXT NOT NULL,
  updated_at  TEXT NOT NULL,
  is_deleted  INTEGER NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX uk_dict_data ON sys_dict_data (dict_type, dict_value);


-- 建表脚本版本记录。没有这张表，转 Oracle 和后续升级都没有抓手
-- checksum 是关键：能发现「脚本被改过但没重跑」，否则库里结构跟脚本对不上还查不出来
CREATE TABLE sys_schema_version (
  id              TEXT PRIMARY KEY,
  version         TEXT NOT NULL,                           -- 1.2.0
  script_name     TEXT NOT NULL,                           -- schema-sqlite.sql
  script_checksum TEXT,                                    -- 脚本 SHA-256 前 16 位
  db_dialect      TEXT NOT NULL DEFAULT 'SQLITE',          -- SQLITE / ORACLE
  applied_at      TEXT NOT NULL,                           -- 执行时间，应用层赋值
  applied_by      TEXT,                                    -- 执行人（手工 / 应用启动自检）
  cost_ms         INTEGER,                                 -- 执行耗时，异常慢的脚本要能看出来
  success         INTEGER NOT NULL DEFAULT 1,              -- 1 = 成功，0 = 失败
  error_msg       TEXT,                                    -- 失败原因，失败记录同样要留
  remark          TEXT,
  created_at      TEXT NOT NULL
);
CREATE UNIQUE INDEX uk_schema_ver ON sys_schema_version (version, db_dialect);


-- =====================================================================
-- 二、权限（3 张）
-- =====================================================================

-- 数据权限规则。改权限改这张表，不改代码（03 第 5.2 节）
CREATE TABLE sys_data_scope_rule (
  id           TEXT PRIMARY KEY,
  role_code    TEXT NOT NULL,
  ticket_type  TEXT NOT NULL DEFAULT '*',                  -- REQ / BUG / INC / *
  scope_type   TEXT NOT NULL,                              -- SELF/ASSIGNED/GROUP/DEPT/DEPT_AND_SUB/ALL/CUSTOM/NONE
  extra_json   TEXT,                                       -- 范围附加参数，如 {"groupId":"..."}
  readable     INTEGER NOT NULL DEFAULT 1,
  writable     INTEGER NOT NULL DEFAULT 1,                 -- 0 = 只读，如部门负责人
  enabled      INTEGER NOT NULL DEFAULT 1,
  remark       TEXT,
  created_at   TEXT NOT NULL,
  updated_at   TEXT NOT NULL
);
CREATE UNIQUE INDEX uk_scope_rule ON sys_data_scope_rule (role_code, ticket_type);


-- 自定义用户组（预留，跨部门临时授权用；一期只建表不做界面）
CREATE TABLE sys_user_group (
  id          TEXT PRIMARY KEY,
  group_code  TEXT NOT NULL,
  group_name  TEXT NOT NULL,
  remark      TEXT,
  status      TEXT NOT NULL DEFAULT 'ACTIVE',
  created_at  TEXT NOT NULL,
  updated_at  TEXT NOT NULL
);
CREATE UNIQUE INDEX uk_group_code ON sys_user_group (group_code);


CREATE TABLE sys_user_group_member (
  id          TEXT PRIMARY KEY,
  group_id    TEXT NOT NULL,
  user_id     TEXT NOT NULL,
  created_at  TEXT NOT NULL
);
CREATE UNIQUE INDEX uk_gm_unique ON sys_user_group_member (group_id, user_id);


-- =====================================================================
-- 三、业务单据（16 张）
-- =====================================================================

-- 单据主表。REQ / BUG / INC 共用，靠 ticket_type 区分
CREATE TABLE req_ticket (
  id                    TEXT PRIMARY KEY,
  ticket_no             TEXT NOT NULL,                     -- REQ-2026-000123
  ticket_type           TEXT NOT NULL,                     -- REQ / BUG / INC
  title                 TEXT NOT NULL,
  description           TEXT,                              -- 需求简述（一句话说清要做什么）
  content_asis          TEXT,                              -- 现状 As Is：现在是怎么做的、痛点在哪
  content_tobe          TEXT,                              -- 期望 To Be：希望变成什么样
  status                TEXT NOT NULL,                     -- 27 态，见 02 第四、五节
  priority              TEXT,                              -- P0 / P1 / P2 / P3
  reporter_id           TEXT NOT NULL,                     -- 提出人（算「谁提的需求最多」用这个）
  creator_id            TEXT NOT NULL,                     -- 录入人（可为代录的助理）
  owner_id              TEXT,                              -- 需求 Owner：业务侧责任人，负责内容准确与验收拍板
  dept_id               TEXT,                              -- 提交时提出人主部门快照，不随调岗变
  assignee_id           TEXT,
  system_code           TEXT,                              -- 所属系统，如 MES / EAP / FDC
  module_code           TEXT,                              -- 所属模块
  acceptance_criteria   TEXT,                              -- 验收标准
  acceptance_locked     INTEGER NOT NULL DEFAULT 0,        -- 1 = 设计确认后锁定
  acceptance_locked_at  TEXT,
  acceptance_locked_by  TEXT,
  roi_desc              TEXT,                              -- 需求 ROI 说明：省多少工时 / 影响多少人 / 带来什么
  roi_value             REAL,                              -- ROI 量化值（可空，供排序与报表；口径见 03 4.2.3）
  propose_date          TEXT,                              -- 提出日期 YYYY-MM-DD，业务口径，可早于提交时间（线下先提后录）
  expect_due_date       TEXT,                              -- 期望完成日期 YYYY-MM-DD，提出人填，派单与排期参考
  estimated_hours       REAL,                              -- 预估工时（小时，两位小数）
  actual_hours          REAL,
  parent_id             TEXT,                              -- 父单（BUG 挂 REQ、需求分解）
  ticket_source         TEXT NOT NULL DEFAULT 'WEB',       -- WEB / API
  dup_override          INTEGER NOT NULL DEFAULT 0,        -- 1 = AI 判重复但坚持新建
  override_reason       TEXT,
  return_count          INTEGER NOT NULL DEFAULT 0,        -- 累计退回次数
  suspended_from        TEXT,                              -- 挂起前的状态，恢复时用
  suspend_reason        TEXT,
  expect_resume_at      TEXT,
  close_reason          TEXT,                              -- DONE / REJECT / DUP / CANCEL
  closed_at             TEXT,
  submitted_at          TEXT,
  assigned_at           TEXT,
  design_at             TEXT,
  dev_at                TEXT,
  uat_at                TEXT,
  release_at            TEXT,                              -- 上线时间：满 7 天定时器的起算点（原 released_at，v1.6 统一命名）
  sla_due_at            TEXT,                              -- 当前状态应完成时间
  sla_overdue_hours     REAL NOT NULL DEFAULT 0,           -- 累计超期小时（退回不归零）
  branch_name_suggest   TEXT,                              -- 提单时生成的建议分支名
  superior_id           TEXT,                              -- 上级确认人快照（Leader/主任）；本人是 Leader 时为空
  superior_confirmed_at TEXT,                              -- 上级确认时间（LEADER_CONFIRM → REQ_REVIEW）
  superior_skipped      INTEGER NOT NULL DEFAULT 0,         -- 1 = 提出人本人即 Leader，按规则跳过确认
  review_passed_at      TEXT,                              -- 需求评审通过时间（REQ_REVIEW → PENDING_ASSIGN）
  eval_result           TEXT,                              -- 上线评价结论：PASS 符合 / FAIL 不符合
  satisfaction_score    REAL,                              -- 满意度 1~5，仅 eval_result=PASS 时填
  eval_comment          TEXT,                              -- FAIL 时必填的不符合原因；PASS 时为可选评语
  evaluated_at          TEXT,                              -- 评价提交时间
  evaluated_by          TEXT,                              -- 评价人（提出人或需求 Owner）
  -- ---- v1.6 新增：项目归属 / 流程标记 / 上线后验证 ----
  project_id            TEXT NOT NULL,                     -- 所属项目，提单时快照
  system_id             TEXT NOT NULL,                     -- 所属系统（主关联），提单页下拉按当前项目过滤
  mit_skipped           INTEGER NOT NULL DEFAULT 0,        -- 1 = 提出人本人即 MIT Leader，跳过该环节
  platform_skipped      INTEGER NOT NULL DEFAULT 0,        -- 1 = 提出人本人即平台负责人，跳过
  -- v0.7 并行化：三方确认与设计评审不分先后，各方通过后置 1；全 1 由服务层原子升级状态
  mit_passed            INTEGER NOT NULL DEFAULT 0,        -- 1 = MIT 已确认（CONFIRMING 阶段）
  platform_passed       INTEGER NOT NULL DEFAULT 0,        -- 1 = 平台已确认（CONFIRMING 阶段）
  superior_passed       INTEGER NOT NULL DEFAULT 0,        -- 1 = 上级已确认（CONFIRMING 阶段）
  dsgn_it_passed        INTEGER NOT NULL DEFAULT 0,        -- 1 = IT 评审已通过（DSGN_REVIEWING 阶段）
  dsgn_platform_passed  INTEGER NOT NULL DEFAULT 0,        -- 1 = 平台评审已通过（DSGN_REVIEWING 阶段）
  dsgn_user_passed      INTEGER NOT NULL DEFAULT 0,        -- 1 = 用户评审已通过（DSGN_REVIEWING 阶段）
  doc_confirmed_at      TEXT,                              -- 需求文档确认时间（N06，不单独占状态）
  run_stable_days       INTEGER NOT NULL DEFAULT 7,        -- 观察期天数：REQ 7 / BUG 3
  ai_degraded           INTEGER NOT NULL DEFAULT 0,        -- 1 = AI 超时降级为模板空壳
  ai_clarify_round      INTEGER NOT NULL DEFAULT 0,        -- 已进行的澄清轮次，上限 3
  monitor_note          TEXT,                              -- 运行监控结论，提交时校验 ≥20 字
  related_inc_id        TEXT,                              -- 关联故障单：未关闭则满 7 天计时暂停
  uat_waiver            INTEGER NOT NULL DEFAULT 0,        -- 1 = 用例覆盖率 <100% 由 Leader 豁免
  uat_waiver_reason     TEXT,                              -- 豁免理由，uat_waiver=1 时必填
  created_at            TEXT NOT NULL,
  updated_at            TEXT NOT NULL,
  updated_by            TEXT
);
CREATE UNIQUE INDEX uk_ticket_no ON req_ticket (ticket_no);
CREATE INDEX idx_ticket_status ON req_ticket (status);
CREATE INDEX idx_ticket_dept ON req_ticket (dept_id);
CREATE INDEX idx_ticket_reporter ON req_ticket (reporter_id);
CREATE INDEX idx_ticket_assignee ON req_ticket (assignee_id);
CREATE INDEX idx_ticket_type ON req_ticket (ticket_type);
CREATE INDEX idx_ticket_parent ON req_ticket (parent_id);
CREATE INDEX idx_ticket_sla ON req_ticket (sla_due_at);
CREATE INDEX idx_ticket_owner ON req_ticket (owner_id);
CREATE INDEX idx_ticket_due ON req_ticket (expect_due_date);


-- 验收标准子表。把主表的自由文本拆成逐条结构化，两个用途（09 第四节 #2）：
--   1. req_test_case.criteria_index 指向 seq，覆盖率分母 = 本表条数
--   2. UAT 通过校验「每条都有用例覆盖」才放行（06 第五节）
-- 主表 acceptance_criteria 保留原文，作为提交时的输入原稿；本表是结构化结果，提交时拆分生成
CREATE TABLE req_acceptance_criteria (
  id          TEXT PRIMARY KEY,
  ticket_id   TEXT NOT NULL,
  seq         INTEGER NOT NULL,                          -- 第几条，从 1 开始
  content     TEXT NOT NULL,                             -- 单条验收标准内容
  locked      INTEGER NOT NULL DEFAULT 0,                -- 1 = 已锁定（随主单 acceptance_locked）
  created_at  TEXT NOT NULL,
  updated_at  TEXT NOT NULL
);
CREATE INDEX idx_accrit_ticket ON req_acceptance_criteria (ticket_id, seq);


-- 流转时间线。只追加，不更新不删除
CREATE TABLE req_flow_log (
  id                TEXT PRIMARY KEY,
  ticket_id         TEXT NOT NULL,
  from_status       TEXT,
  to_status         TEXT NOT NULL,
  action            TEXT NOT NULL,                         -- 动作码，如 SUBMIT / ASSIGN / RETURN
  operator_id       TEXT,                                  -- 系统动作留空
  operator_dept_id  TEXT,                                  -- 操作时所在部门快照（兼岗留痕）
  reason_code       TEXT,                                  -- 退回原因分类
  remark            TEXT,
  return_point      TEXT,                                  -- 退回点码（如 RT_DESIGN），同一退回点第 3 次升级按它计次
  return_seq        INTEGER,                               -- 第几次退回（全局序号，展示用）
  created_at        TEXT NOT NULL
);
CREATE INDEX idx_flow_ticket ON req_flow_log (ticket_id, created_at);
CREATE INDEX idx_flow_return ON req_flow_log (ticket_id, return_point);


-- 字段级变更历史。只追加，answers「内容被谁改成什么样」（03 第 4.2.1）
CREATE TABLE req_change_log (
  id            TEXT PRIMARY KEY,
  ticket_id     TEXT NOT NULL,
  field_name    TEXT NOT NULL,                             -- 字段名，如 acceptance_criteria
  field_label   TEXT NOT NULL,                             -- 中文名，如「验收标准」，界面直接渲染
  old_value     TEXT,                                      -- 长文本存全文，不截断
  new_value     TEXT,
  operator_id   TEXT,
  change_source TEXT NOT NULL DEFAULT 'FORM',              -- FORM / FLOW / SYSTEM / MIGRATE
  reason        TEXT,                                      -- 锁定后的变更必填
  created_at    TEXT NOT NULL
);
CREATE INDEX idx_change_ticket ON req_change_log (ticket_id, created_at);


-- AI 分析结果快照。自研模块产出，不关联旧系统
CREATE TABLE req_analysis (
  id              TEXT PRIMARY KEY,
  ticket_id       TEXT NOT NULL,
  sim_score       REAL,                                    -- 最高相似度 0~1
  verdict         TEXT,                                    -- NEW / PARTIAL / DUPLICATE
  similar_list    TEXT,                                    -- JSON 数组：相似需求清单
  suggestion      TEXT,                                    -- 三问结论 + 建议
  roi_json        TEXT,                                    -- ROI 估算结构化结果
  trace_json      TEXT,                                    -- 推理轨迹，供复盘与调阈值
  analysis_mode   TEXT NOT NULL DEFAULT 'RULE',            -- RULE / LLM / RULE_FALLBACK
  engine_version  TEXT,
  prompt_version  TEXT,
  override_flag   INTEGER NOT NULL DEFAULT 0,              -- 1 = 人覆盖了 AI 结论
  override_reason TEXT,
  created_at      TEXT NOT NULL
);
CREATE INDEX idx_analysis_ticket ON req_analysis (ticket_id, created_at);


-- 附件。按节点归档：stage = 上传时所处状态，attach_type = 交付物类型（07 第一节）
-- 库里只存相对路径，绝对路径放配置里，换机器/转库只搬目录不改数据
CREATE TABLE req_attachment (
  id             TEXT PRIMARY KEY,
  ticket_id      TEXT NOT NULL,
  stage          TEXT NOT NULL,                            -- 上传时所处状态码，详情页按它分组
  attach_type    TEXT NOT NULL,                            -- 交付物类型：DESIGN_DOC / SELF_TEST / RELEASE_ORDER / ROLLBACK_PLAN / SCREENSHOT / OTHER
  original_name  TEXT NOT NULL,                            -- 原始文件名，下载时还原
  relative_path  TEXT NOT NULL,                            -- 相对路径，如 2026/11/{ticket_id}/{uuid}_x.docx
  size           INTEGER,                                  -- 字节数
  mime_type      TEXT,
  sha256         TEXT,                                     -- 文件指纹：重复上传检测 + 完整性校验
  extracted_text TEXT,                                     -- 正文提取结果，作为 AI 分析输入（07 第六节）
  uploader_id    TEXT,
  is_confirmed   INTEGER NOT NULL DEFAULT 0,               -- 1 = 确认版；同节点同类型只允许一个
  deleted        INTEGER NOT NULL DEFAULT 0,               -- 软删除，附件是审计证据不做物理删
  created_at     TEXT NOT NULL
);
CREATE INDEX idx_att_ticket ON req_attachment (ticket_id, stage);
CREATE INDEX idx_att_sha ON req_attachment (sha256);


-- 附件必填规则。单据类型 + 状态 → 必传哪些交付物，改规则不改代码（07 第二节）
-- 点流转按钮时校验，缺附件直接拦下；不做「事后提醒」
-- status 语义定死为 to_status：流转「到达」该状态时校验必填；
-- 附件的 stage 记的是上传时的 from_status，两者不是一回事（09 第四节 #3 裁决）
CREATE TABLE req_attach_rule (
  id          TEXT PRIMARY KEY,
  ticket_type TEXT NOT NULL DEFAULT '*',                   -- REQ / BUG / INC / *
  status      TEXT NOT NULL DEFAULT '*',                   -- 目标状态码（to_status），* = 所有状态
  attach_type TEXT NOT NULL,                               -- 必传的交付物类型
  min_count   INTEGER NOT NULL DEFAULT 1,                  -- 至少传几份
  required    INTEGER NOT NULL DEFAULT 1,                  -- 1 = 不满足不许流转
  enabled     INTEGER NOT NULL DEFAULT 1,
  remark      TEXT,
  created_at  TEXT NOT NULL,
  updated_at  TEXT NOT NULL
);
CREATE UNIQUE INDEX uk_attach_rule ON req_attach_rule (ticket_type, status, attach_type);


-- 测试用例。由验收标准逐条翻译而来，结构化落表才能算覆盖率（06 第三节）
CREATE TABLE req_test_case (
  id              TEXT PRIMARY KEY,
  ticket_id       TEXT NOT NULL,
  case_no         TEXT NOT NULL,                           -- TC-REQ2026000123-03
  title           TEXT NOT NULL,
  precond         TEXT,                                    -- 前置条件
  steps           TEXT,                                    -- JSON 数组：[{seq,action,data}]
  expected        TEXT,                                    -- 预期结果
  priority        TEXT NOT NULL DEFAULT 'P1',              -- P0 / P1 / P2
  source_criteria TEXT,                                    -- 对应的验收标准原文
  criteria_index  INTEGER,                                 -- 对应第几条验收标准，算覆盖率的分母依据
  focus_flag      INTEGER NOT NULL DEFAULT 0,              -- 1 = 重点关注
  focus_reason    TEXT,                                    -- 为什么重点关注；只标红不说原因是无效的
  origin          TEXT NOT NULL DEFAULT 'AI',              -- AI / HUMAN
  exec_status     TEXT NOT NULL DEFAULT 'NOT_RUN',         -- NOT_RUN / PASS / FAIL / SKIP
  bug_id          TEXT,                                    -- 不通过时关联的 BUG 单
  round_no        INTEGER NOT NULL DEFAULT 1,              -- 生成批次：设计确认后第 1 轮，UAT 前刷新为第 2 轮
  created_by      TEXT,
  created_at      TEXT NOT NULL,
  updated_at      TEXT NOT NULL
);
CREATE UNIQUE INDEX uk_case_no ON req_test_case (case_no);
CREATE INDEX idx_case_ticket ON req_test_case (ticket_id, round_no);


-- 用例执行记录。多次回归时每轮一条，不覆盖历史（06 第十节）
CREATE TABLE req_case_result (
  id            TEXT PRIMARY KEY,
  case_id       TEXT NOT NULL,
  ticket_id     TEXT NOT NULL,                             -- 冗余，便于一次查出某单的全部执行记录
  round_no      INTEGER NOT NULL DEFAULT 1,                -- 第几轮 UAT
  exec_status   TEXT NOT NULL,                             -- PASS / FAIL / SKIP
  actual_result TEXT,
  executor_id   TEXT,
  executed_at   TEXT,
  bug_id        TEXT,
  remark        TEXT,
  created_at    TEXT NOT NULL
);
CREATE INDEX idx_caseres_case ON req_case_result (case_id, round_no);
CREATE INDEX idx_caseres_ticket ON req_case_result (ticket_id);


-- 沟通记录
CREATE TABLE req_comment (
  id          TEXT PRIMARY KEY,
  ticket_id   TEXT NOT NULL,
  author_id   TEXT NOT NULL,
  content     TEXT NOT NULL,
  is_internal INTEGER NOT NULL DEFAULT 0,                  -- 1 = 仅内部可见，对提出人不显示
  created_at  TEXT NOT NULL
);
CREATE INDEX idx_comment_ticket ON req_comment (ticket_id, created_at);


-- 评审记录。对应 SUSPENDED_REVIEW（第 3 次退回后线下开会，线上只登记结论）
CREATE TABLE req_review (
  id            TEXT PRIMARY KEY,
  ticket_id     TEXT NOT NULL,
  review_type   TEXT NOT NULL DEFAULT 'ESCALATION',        -- ESCALATION / DESIGN / OTHER
  conclusion    TEXT NOT NULL,                             -- CONTINUE / CLOSE / REASSIGN
  resume_status TEXT,                                      -- 选 CONTINUE 时必填，回到哪个状态
  participants  TEXT,                                      -- 参与人，逗号分隔
  minutes       TEXT,                                      -- 评审纪要
  reviewer_id   TEXT,
  reviewed_at   TEXT,
  created_at    TEXT NOT NULL
);
CREATE INDEX idx_review_ticket ON req_review (ticket_id);


-- 通用外部关联。分支 / MR / 流水线 / 部署 / 文档，加类型不改表
CREATE TABLE req_link (
  id          TEXT PRIMARY KEY,
  ticket_id   TEXT NOT NULL,
  link_type   TEXT NOT NULL,                               -- BRANCH/MR/COMMIT/CI_BUILD/DEPLOY/DOC/OTHER
  external_id TEXT,                                        -- 分支名、MR iid、build number
  url         TEXT NOT NULL,
  title       TEXT,
  status      TEXT,                                        -- 外部状态快照 opened/merged/success/failed
  synced_at   TEXT,
  link_source TEXT NOT NULL DEFAULT 'MANUAL',              -- MANUAL / WEBHOOK / POLLING
  created_at  TEXT NOT NULL
);
CREATE INDEX idx_link_ticket ON req_link (ticket_id, link_type);


-- 部署记录。一期手工登记，二期自动回写
CREATE TABLE req_deploy (
  id            TEXT PRIMARY KEY,
  ticket_id     TEXT NOT NULL,
  env           TEXT NOT NULL,                             -- DEV / UAT / PROD
  version       TEXT,
  image_tag     TEXT,                                      -- 容器化部署的回滚点
  deploy_type   TEXT NOT NULL DEFAULT 'MANUAL',            -- JENKINS_JOB/GITLAB_PIPELINE/SSH_SCRIPT/MANUAL
  deploy_status TEXT NOT NULL DEFAULT 'SUCCESS',           -- SUCCESS / FAILED / RUNNING
  log_url       TEXT,
  rollback_point TEXT,
  operator_id   TEXT,
  started_at    TEXT,
  finished_at   TEXT,
  created_at    TEXT NOT NULL
);
CREATE INDEX idx_deploy_ticket ON req_deploy (ticket_id, env);


-- 上线单（审批 + 执行结果）
CREATE TABLE req_release (
  id            TEXT PRIMARY KEY,
  ticket_id     TEXT NOT NULL,
  env           TEXT NOT NULL DEFAULT 'PROD',
  deployer_id   TEXT,
  rollback_plan TEXT,                                      -- 回滚方案，审批必看
  window_start  TEXT,
  window_end    TEXT,
  approver_id   TEXT,
  approved_at   TEXT,
  result        TEXT,                                      -- SUCCESS / FAILED / ROLLBACK
  created_at    TEXT NOT NULL
);
CREATE INDEX idx_release_ticket ON req_release (ticket_id);


-- 环境占用锁。UAT 只有 1 套，必须防两个需求同时部署互相覆盖
CREATE TABLE req_env_lock (
  id          TEXT PRIMARY KEY,
  env         TEXT NOT NULL,
  ticket_id   TEXT NOT NULL,
  locked_by   TEXT,
  locked_at   TEXT NOT NULL,
  expire_at   TEXT,
  released_at TEXT
);
CREATE INDEX idx_envlock_env ON req_env_lock (env, released_at);


-- 故障复盘。INC 类型必填
CREATE TABLE req_incident_review (
  id            TEXT PRIMARY KEY,
  ticket_id     TEXT NOT NULL,
  root_cause    TEXT,
  impact        TEXT,
  action_items  TEXT,                                      -- JSON 数组：整改项 + 责任人 + 期限
  reviewed_at   TEXT,
  created_at    TEXT NOT NULL
);
CREATE INDEX idx_incident_ticket ON req_incident_review (ticket_id);


-- =====================================================================
-- 四、主数据与流程文档（v1.6 新增 5 张）
-- =====================================================================

-- 项目主数据。需求的最上层归属；所有项目、所有系统共用同一套 27 态流程，一期不做流程模板
CREATE TABLE req_project (
  id            TEXT PRIMARY KEY,
  project_code  TEXT NOT NULL,                             -- 全局唯一，如 ZS-PILOT。一经使用不再改（附件目录第一级就是它）
  project_name  TEXT NOT NULL,
  project_type  TEXT,                                      -- PILOT 中试 / MASS 量产 / OTHER，走字典
  status        TEXT NOT NULL DEFAULT 'PLANNING',          -- PLANNING / ACTIVE / SUSPENDED / CLOSED
  owner_id      TEXT,                                      -- 项目负责人
  dept_id       TEXT,                                      -- 归口部门
  start_date    TEXT,                                      -- YYYY-MM-DD
  end_date      TEXT,                                      -- YYYY-MM-DD
  remark        TEXT,
  sort_no       INTEGER NOT NULL DEFAULT 0,
  created_at    TEXT NOT NULL,
  updated_at    TEXT NOT NULL,
  deleted       INTEGER NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX uk_project_code ON req_project (project_code);
CREATE INDEX idx_project_status ON req_project (status, sort_no);

-- 系统主数据。挂在项目下，只属一个项目（不做中间表）；system_code 项目内唯一，不是全局唯一
CREATE TABLE req_system (
  id           TEXT PRIMARY KEY,
  project_id   TEXT NOT NULL,
  system_code  TEXT NOT NULL,                              -- 项目内唯一，如 MES / EAP
  system_name  TEXT NOT NULL,
  system_type  TEXT,                                       -- PURCHASED 外购 / INHOUSE 自研 / RUNNING 在运
  owner_id     TEXT,                                       -- 系统负责人
  status       TEXT NOT NULL DEFAULT 'BUILDING',           -- BUILDING / LIVE / OFFLINE
  remark       TEXT,
  sort_no      INTEGER NOT NULL DEFAULT 0,
  created_at   TEXT NOT NULL,
  updated_at   TEXT NOT NULL,
  deleted      INTEGER NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX uk_system_project_code ON req_system (project_id, system_code);
CREATE INDEX idx_system_project ON req_system (project_id, status);

-- AI 生成的需求文档。12 节结构化存 doc_json，正文存 content_md 供预览与导出。
-- 节标题固定，验收标准（第 10 节）与功能清单（第 5 节）要能被抽出成结构化数据
CREATE TABLE req_doc (
  id            TEXT PRIMARY KEY,
  ticket_id     TEXT NOT NULL,
  version       INTEGER NOT NULL DEFAULT 1,                -- 同单每次重生成 +1，最新版为当前版
  content_md    TEXT,                                      -- Markdown 正文
  doc_json      TEXT,                                      -- 12 节结构化 JSON
  ai_model      TEXT,                                      -- 生成所用模型标识；降级生成时为 NULL
  ai_degraded   INTEGER NOT NULL DEFAULT 0,                -- 1 = 模板空壳，前端要打黄标
  confirmed_at  TEXT,
  confirmed_by  TEXT,
  created_at    TEXT NOT NULL,
  updated_at    TEXT NOT NULL
);
CREATE INDEX idx_doc_ticket ON req_doc (ticket_id, version);

-- AI 澄清问答。按 dimension 去重，实现「已答过的不重问」；轮次上限 3
CREATE TABLE req_ai_clarify (
  id           TEXT PRIMARY KEY,
  ticket_id    TEXT NOT NULL,
  round_no     INTEGER NOT NULL,                           -- 第几轮，从 1 开始
  dimension    TEXT,                                       -- USER 谁用 / PROBLEM 解决什么 / ASIS 现在怎么干 / TOBE 期望什么样 / SUCCESS 怎么算成功
  question     TEXT NOT NULL,
  answer       TEXT,                                       -- 未答为 NULL
  answered_at  TEXT,
  created_at   TEXT NOT NULL
);
CREATE INDEX idx_clarify_ticket ON req_ai_clarify (ticket_id, round_no);

-- 重点功能点检清单。初始为空、必须可维护；清单为空时不卡流程，flow_log 标 check_items_empty=1
-- 主键统一叫 id（02 文档里写作 check_item_id，指同一列）
CREATE TABLE req_check_item (
  id            TEXT PRIMARY KEY,
  ticket_id     TEXT NOT NULL,
  system_id     TEXT,                                      -- 冗余系统，供「按系统看重点功能」统计
  item_name     TEXT NOT NULL,                             -- 功能点名称，如「批次过站后 WIP 自动扣减」
  item_desc     TEXT,                                      -- 怎么验，一句话
  expect_result TEXT,                                      -- 期望结果，判定通过与否的依据
  is_key        INTEGER NOT NULL DEFAULT 1,                -- 1 = 重点功能，必须点检
  sort_no       INTEGER NOT NULL DEFAULT 0,
  source        TEXT NOT NULL DEFAULT 'MANUAL',            -- MANUAL 人工维护 / AI_SUGGEST 从需求文档第 5 节带出
  check_result  TEXT,                                      -- PASS / FAIL / NA，点检时填
  check_remark  TEXT,                                      -- 不通过说明
  checked_by    TEXT,
  checked_at    TEXT,
  created_at    TEXT NOT NULL,
  updated_at    TEXT NOT NULL
);
CREATE INDEX idx_check_ticket ON req_check_item (ticket_id, sort_no);
CREATE INDEX idx_check_system ON req_check_item (system_id, is_key);


-- =====================================================================
-- 五、配置与通知（11 张）
-- =====================================================================

-- 工作日历。SLA 里的「2 个工作日」必须有它才能算，否则国庆期间全量误报超期
-- 日期用字符串存 YYYY-MM-DD：ISO8601 字典序等于时间序，两库比较/排序行为一致，
-- 也避开 Oracle DATE 隐含时分秒导致「等于」判断失败的老坑
CREATE TABLE sys_work_calendar (
  id          TEXT PRIMARY KEY,
  cal_date    TEXT NOT NULL,                               -- YYYY-MM-DD
  year_no     INTEGER NOT NULL,                            -- 年份冗余，便于按年批量维护
  day_type    TEXT NOT NULL,                               -- WORKDAY 工作日 / WEEKEND 周末 / HOLIDAY 节假日 / MAKEUP 调休上班
  day_name    TEXT,                                        -- 如「国庆节」「春节调休」
  work_hours  REAL NOT NULL DEFAULT 8,                     -- 当日可工作小时数，非工作日填 0
  start_time  TEXT,                                        -- HH:mm，支持非 8 小时制的班次
  end_time    TEXT,
  remark      TEXT,
  created_at  TEXT NOT NULL,
  updated_at  TEXT NOT NULL
);
CREATE UNIQUE INDEX uk_cal_date ON sys_work_calendar (cal_date);
CREATE INDEX idx_cal_year ON sys_work_calendar (year_no, day_type);

-- SLA 配置。数值必须可配，不许写死在代码里
CREATE TABLE req_sla_config (
  id            TEXT PRIMARY KEY,
  ticket_type   TEXT NOT NULL DEFAULT '*',
  priority      TEXT NOT NULL DEFAULT '*',                 -- * 表示不限优先级
  status        TEXT NOT NULL,
  limit_hours   REAL NOT NULL,                             -- 时限（小时，按工作日历折算）
  warn_hours    REAL,                                      -- 提前预警
  overdue_action TEXT,                                     -- NOTIFY / ESCALATE / CLOSE
  enabled       INTEGER NOT NULL DEFAULT 1,
  remark        TEXT,
  created_at    TEXT NOT NULL,
  updated_at    TEXT NOT NULL
);
CREATE UNIQUE INDEX uk_sla_config ON req_sla_config (ticket_type, priority, status);


-- 通知记录
CREATE TABLE req_notify (
  id            TEXT PRIMARY KEY,
  ticket_id     TEXT,
  receiver_id   TEXT,                                      -- 站内信接收人；群通知为空
  channel       TEXT NOT NULL,                             -- SITE / DINGTALK / MAIL
  template_code TEXT,
  title         TEXT,
  content       TEXT,
  sent_at       TEXT,
  read_at       TEXT,
  send_result   TEXT,                                      -- SUCCESS / FAILED + 错误摘要
  retry_count   INTEGER NOT NULL DEFAULT 0,
  created_at    TEXT NOT NULL
);
CREATE INDEX idx_notify_receiver ON req_notify (receiver_id, read_at);
CREATE INDEX idx_notify_ticket ON req_notify (ticket_id, created_at);


-- 通知通道配置。钉钉 Webhook + 加签密钥存这里，不写进 yml
CREATE TABLE sys_notify_channel (
  id          TEXT PRIMARY KEY,
  channel_code TEXT NOT NULL,                              -- SITE / DINGTALK / MAIL
  enabled     INTEGER NOT NULL DEFAULT 0,
  webhook_url TEXT,                                        -- 钉钉机器人地址
  secret      TEXT,                                        -- 钉钉加签密钥（加密存储）
  msg_type    TEXT DEFAULT 'markdown',
  at_mobiles  TEXT,                                        -- 需要 @ 的手机号，逗号分隔
  retry_max   INTEGER NOT NULL DEFAULT 2,
  timeout_ms  INTEGER NOT NULL DEFAULT 5000,
  remark      TEXT,
  created_at  TEXT NOT NULL,
  updated_at  TEXT NOT NULL
);
CREATE UNIQUE INDEX uk_channel_code ON sys_notify_channel (channel_code);


-- 通知模板。文案外置，运营可改不发版
CREATE TABLE sys_notify_template (
  id            TEXT PRIMARY KEY,
  template_code TEXT NOT NULL,
  channel_code  TEXT NOT NULL DEFAULT 'SITE',
  title_tpl     TEXT,
  content_tpl   TEXT,                                      -- 用 ${ticketNo} ${title} ${status} 占位
  enabled       INTEGER NOT NULL DEFAULT 1,
  remark        TEXT,
  created_at    TEXT NOT NULL,
  updated_at    TEXT NOT NULL,
  is_deleted    INTEGER NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX uk_tpl_code ON sys_notify_template (template_code, channel_code);


-- 部署配置。不同系统部署方式不同，没有这张表一键部署只能硬编码
CREATE TABLE sys_deploy_config (
  id             TEXT PRIMARY KEY,
  system_code    TEXT NOT NULL,
  env            TEXT NOT NULL,
  deploy_type    TEXT NOT NULL,                            -- JENKINS_JOB/GITLAB_PIPELINE/SSH_SCRIPT/MANUAL
  target_ref     TEXT,                                     -- Job 名 / Pipeline ID / 脚本路径
  param_template TEXT,                                     -- JSON，如 {"branch":"${branch}","ticketNo":"${ticketNo}"}
  enabled        INTEGER NOT NULL DEFAULT 0,
  remark         TEXT,
  created_at     TEXT NOT NULL,
  updated_at     TEXT NOT NULL
);
CREATE UNIQUE INDEX uk_deploy_cfg ON sys_deploy_config (system_code, env);


-- 单据编号发号器。REQ-2026-000123 在这里生成
-- 为什么不用数据库序列：SQLite 没有序列，Oracle 有 SEQUENCE，语法与语义都不同，
-- 违反「不用数据库特有语法」的纪律。改用「一类型一年段一行计数器」，
-- 发号流程固定为：同一事务内先 UPDATE current_no = current_no + 1，再 SELECT 取值，
-- 两个库都是标准 SQL，换库只建表不换代码
CREATE TABLE sys_no_sequence (
  id          TEXT PRIMARY KEY,
  seq_code    TEXT NOT NULL,                               -- TICKET_REQ / TICKET_BUG / TICKET_INC
  prefix      TEXT NOT NULL,                               -- REQ / BUG / INC
  period_type TEXT NOT NULL DEFAULT 'YEAR',                -- YEAR 按年切段 / MONTH 按月 / NONE 不分段
  period_key  TEXT NOT NULL DEFAULT '*',                   -- 2026 或 202611；NONE 时填 *（不写空串）
  current_no  INTEGER NOT NULL DEFAULT 0,                  -- 本段已发出的最大序号
  padding     INTEGER NOT NULL DEFAULT 6,                  -- 补零位数，6 → 000123
  no_pattern  TEXT NOT NULL DEFAULT '${prefix}-${period}-${seq}',  -- NONE 时不带 period 段
  enabled     INTEGER NOT NULL DEFAULT 1,
  remark      TEXT,
  created_at  TEXT NOT NULL,
  updated_at  TEXT NOT NULL
);
CREATE UNIQUE INDEX uk_no_seq ON sys_no_sequence (seq_code, period_key);


-- HR 岗位 → 系统角色映射。HR 不能直接给业务权限
CREATE TABLE sys_hr_job_role_map (
  id          TEXT PRIMARY KEY,
  hr_job_code TEXT NOT NULL,                               -- HR 岗位编码，如 DEV_MIT
  dept_code   TEXT,                                        -- 可选，按部门限定
  role_code   TEXT NOT NULL,
  auto_grant  INTEGER NOT NULL DEFAULT 0,                  -- 0 = 仅建议需人工确认（默认，见 03 7.5.1）
  enabled     INTEGER NOT NULL DEFAULT 1,
  remark      TEXT,
  created_at  TEXT NOT NULL,
  updated_at  TEXT NOT NULL
);
CREATE UNIQUE INDEX uk_hr_map ON sys_hr_job_role_map (hr_job_code, dept_code, role_code);


-- AI 提示词。外置 + 记版本，改提示词不发版
CREATE TABLE sys_ai_prompt (
  id          TEXT PRIMARY KEY,
  prompt_code TEXT NOT NULL,
  prompt_name TEXT NOT NULL,
  content     TEXT NOT NULL,
  version_no  INTEGER NOT NULL DEFAULT 1,
  enabled     INTEGER NOT NULL DEFAULT 1,
  remark      TEXT,
  created_at  TEXT NOT NULL,
  updated_at  TEXT NOT NULL
);
CREATE UNIQUE INDEX uk_prompt_code ON sys_ai_prompt (prompt_code, version_no);


-- 同义词表。CIM 里「看板 / 大屏 / dashboard」「报工 / 上工」不归并，相似度永远上不去（03 4.3）
-- word 与 std_word 归一后参与分词比对；domain 用来避免跨系统误归并
CREATE TABLE req_synonym (
  id          TEXT PRIMARY KEY,
  word        TEXT NOT NULL,                               -- 原始词
  std_word    TEXT NOT NULL,                               -- 归一后的标准词
  domain      TEXT NOT NULL DEFAULT '*',                   -- 限定业务域/系统，* = 全局
  enabled     INTEGER NOT NULL DEFAULT 1,
  remark      TEXT,
  created_at  TEXT NOT NULL,
  updated_at  TEXT NOT NULL
);
CREATE UNIQUE INDEX uk_synonym_word ON req_synonym (word, domain);


-- 操作日志（审计）
CREATE TABLE sys_oper_log (
  id          TEXT PRIMARY KEY,
  user_id     TEXT,
  module      TEXT,                                        -- ticket / system / analysis
  action      TEXT,
  target_id   TEXT,                                        -- 单据 ID 或目标对象 ID
  request_uri TEXT,
  http_method TEXT,
  params      TEXT,                                        -- 入参 JSON（脱敏后）
  ip          TEXT,
  cost_ms     INTEGER,
  result      TEXT,                                        -- SUCCESS / FAIL
  error_msg   TEXT,
  created_at  TEXT NOT NULL
);
CREATE INDEX idx_oper_user ON sys_oper_log (user_id, created_at);
CREATE INDEX idx_oper_target ON sys_oper_log (target_id);


-- =====================================================================
-- 附：设计约定说明
--
-- 1. 为什么不建物理外键
--    ① 换 Oracle 时外键创建顺序敏感，容易卡在初始化脚本上
--    ② 与逻辑删除冲突：主子表都逻辑删时外键仍要求行存在
--    ③ 批量迁移/清理时外键顺序难排
--    代价是完整性问题要应用层兜住，所以配套两条措施：
--      - 所有删除走状态字段，不做物理删除
--      - 定期跑一遍孤儿数据对账（ticket_id 找不到主单的日志行）
--
-- 2. 为什么 req_ticket 冗余存了 6 个时间戳（submitted_at 等）
--    报表要算「等待时间 / 处理时间」，如果每次都从 req_flow_log 反推，
--    要按状态区间做窗口函数，SQLite 和 Oracle 写法还不一样。
--    冗余存这几个高频节点，报表直接相减，换库零改动。
--    风险是可能与 flow_log 不一致，所以只由状态机引擎写入，不允许业务代码改。
--
-- 3. 字段长度在 SQLite 侧不体现（TEXT 无长度概念），
--    Oracle 侧统一按 VARCHAR2(n CHAR) 建，两边字段名与语义严格一一对应，
--    一致性由 gen_check.py 校验（比对表名集合与列名集合）。
-- =====================================================================
