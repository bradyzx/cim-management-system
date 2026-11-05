-- =====================================================================
-- 需求全生命周期管理系统 · 表结构（Oracle 12c+）
-- 版本：v1.7    与 schema-sqlite.sql 字段一一对应
--
-- v1.7 变更（与 SQLite 侧同步，v0.7 并行化，27 态改 23 态）：
--   1. req_ticket 补 6 列：mit_passed / platform_passed / superior_passed /
--      dsgn_it_passed / dsgn_platform_passed / dsgn_user_passed
--   2. 三方确认三态合并为 CONFIRMING、设计评审三态合并为 DSGN_REVIEWING，
--      退回点合并为 ⑱ RT_CONFS 与 ⑲ RT_DSGN_REVIEW
--
-- v1.6 变更（与 SQLite 侧同步，对齐 02 v0.6 的 27 态与 17 个退回点）：
--   1. 新建 5 表：req_project / req_system / req_doc / req_ai_clarify / req_check_item
--   2. req_ticket 补 12 列（项目归属 2 / 流程标记 6 / 上线后验证 4）
--   3. sys_dept 补 dept_type，三方确认定位确认人靠它
--   4. req_ticket.released_at 改名 release_at（req_env_lock.released_at 语义不同，不改）
--   5. 满 7 天暂停与挂起冻结均不加列，用 INC 区间相减与 flow_log 推导，见 10 号提案第六章
--
-- v1.5 变更（闭合 09 清单第四节的表结构项，与 SQLite 侧同步）：
--   1. req_flow_log 补 return_point（退回点码）：同一退回点第 3 次升级按它计次
--   2. 新建 req_acceptance_criteria（验收标准子表）：用例覆盖率分母的依据
--   3. req_attach_rule.status 语义定死为 to_status（流转到达该状态时校验）
--   4/5. SLA 工作日历折算、动作权限矩阵均落代码，无表结构变更
--
-- v1.4 变更：req_ticket 补 9 列（上级确认 3 列 / 评审通过 1 列 / 上线评价 5 列），
--   支撑新增的 LEADER_CONFIRM、REQ_REVIEW、EVALUATING 三个状态，详见 02 v0.4。
--
-- 换库纪律（03 第三节）：
--   1. 主键 VARCHAR2(64)，应用层生成 32 位 UUID，不用 SEQUENCE/TRIGGER/IDENTITY
--   2. 时间统一 TIMESTAMP(6)，由应用层赋值，不用 SYSDATE 默认值、不用触发器
--   3. 布尔用 NUMBER(1) 存 0/1，不建布尔列
--   4. Oracle 里 '' 等于 NULL：应用层写库前把空串归一成 null，
--      判空用 isBlank 而不是 isEmpty，否则 IS NOT NULL 统计会出错
--   5. 字符串长度一律写 CHAR 语义（VARCHAR2(n CHAR)）。
--      不写 CHAR 时按 NLS_LENGTH_SEMANTICS 走，默认是 BYTE，
--      中文占 3 字节，VARCHAR2(50) 只能存 16 个汉字 —— 这是最常见的翻车点
--   6. 标识符小写下划线，不加双引号（加了就大小写敏感，行为与 SQLite 不一致）
--   7. 不建物理外键（理由见 schema-sqlite.sql 文末）
--
-- 表总量：40 张
-- =====================================================================


-- =====================================================================
-- 一、系统基础（10 张）
-- =====================================================================

CREATE TABLE sys_user (
  id                  VARCHAR2(64 CHAR)  NOT NULL,
  username            VARCHAR2(64 CHAR)  NOT NULL,
  password_hash       VARCHAR2(100 CHAR),
  real_name           VARCHAR2(64 CHAR)  NOT NULL,
  nickname            VARCHAR2(64 CHAR),
  email               VARCHAR2(128 CHAR),
  mobile              VARCHAR2(32 CHAR),
  avatar              VARCHAR2(255 CHAR),
  auth_type           VARCHAR2(16 CHAR)  DEFAULT 'LOCAL' NOT NULL,
  external_id         VARCHAR2(128 CHAR),
  employment_type     VARCHAR2(16 CHAR)  DEFAULT 'EMPLOYEE' NOT NULL,
  hr_employment_code  VARCHAR2(32 CHAR),
  primary_dept_id     VARCHAR2(64 CHAR),
  status              VARCHAR2(16 CHAR)  DEFAULT 'ACTIVE' NOT NULL,
  must_change_password NUMBER(1)         DEFAULT 0 NOT NULL,  -- 1 = 首次登录或密码被重置，必须先改密
  remark              VARCHAR2(500 CHAR),
  sync_source         VARCHAR2(16 CHAR)  DEFAULT 'LOCAL' NOT NULL,
  sync_at             TIMESTAMP(6),
  last_login_at       TIMESTAMP(6),
  created_at          TIMESTAMP(6)       NOT NULL,
  updated_at          TIMESTAMP(6)       NOT NULL,
  CONSTRAINT pk_sys_user PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_user_username ON sys_user (username);
CREATE INDEX idx_user_dept ON sys_user (primary_dept_id);

COMMENT ON TABLE sys_user IS '用户。不物理删除，离职只置 status=DISABLED';
COMMENT ON COLUMN sys_user.auth_type IS 'LOCAL 本地密码 / AD 域账号';
COMMENT ON COLUMN sys_user.employment_type IS 'EMPLOYEE 正式 / INTERN 实习 / CONTRACTOR 外包';
COMMENT ON COLUMN sys_user.primary_dept_id IS '主部门，仅用于展示，禁止用于权限计算';
COMMENT ON COLUMN sys_user.status IS 'ACTIVE 启用 / DISABLED 停用 / LOCKED 锁定';
COMMENT ON COLUMN sys_user.sync_source IS 'LOCAL 手工维护 / HR 同步 / AD 同步';


CREATE TABLE sys_dept (
  id              VARCHAR2(64 CHAR)  NOT NULL,
  dept_code       VARCHAR2(64 CHAR)  NOT NULL,
  dept_name       VARCHAR2(200 CHAR) NOT NULL,
  parent_id       VARCHAR2(64 CHAR),
  ancestors       VARCHAR2(500 CHAR) DEFAULT '/' NOT NULL,
  dept_level      NUMBER(3)          DEFAULT 1 NOT NULL,
  leader_user_id  VARCHAR2(64 CHAR),
  dept_type       VARCHAR2(16 CHAR)  DEFAULT 'BUSINESS' NOT NULL,  -- v1.6：MIT / PLATFORM / BUSINESS / IT
  sort            NUMBER(10)         DEFAULT 0 NOT NULL,
  status          VARCHAR2(16 CHAR)  DEFAULT 'ACTIVE' NOT NULL,
  remark          VARCHAR2(500 CHAR),
  external_id     VARCHAR2(128 CHAR),
  sync_source     VARCHAR2(16 CHAR)  DEFAULT 'LOCAL' NOT NULL,
  sync_at         TIMESTAMP(6),
  created_at      TIMESTAMP(6)       NOT NULL,
  updated_at      TIMESTAMP(6)       NOT NULL,
  CONSTRAINT pk_sys_dept PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_dept_code ON sys_dept (dept_code);
CREATE INDEX idx_dept_parent ON sys_dept (parent_id);
CREATE INDEX idx_dept_ancestors ON sys_dept (ancestors);

COMMENT ON TABLE sys_dept IS '部门。多层，ancestors 存路径串，子部门用 LIKE 前缀查询';
COMMENT ON COLUMN sys_dept.ancestors IS '路径，如 /1/5/23/ 末尾带斜杠；由本系统重算，不接收外部传入';
COMMENT ON COLUMN sys_dept.dept_level IS '层级深度，1 为根；建议不超过 6 层';


CREATE TABLE sys_user_dept (
  id              VARCHAR2(64 CHAR) NOT NULL,
  user_id         VARCHAR2(64 CHAR) NOT NULL,
  dept_id         VARCHAR2(64 CHAR) NOT NULL,
  is_primary      NUMBER(1)         DEFAULT 0 NOT NULL,
  post_type       VARCHAR2(16 CHAR) DEFAULT 'PRIMARY' NOT NULL,
  hr_job_code     VARCHAR2(64 CHAR),
  start_date      DATE,
  end_date        DATE,
  status          VARCHAR2(16 CHAR) DEFAULT 'ACTIVE' NOT NULL,
  created_at      TIMESTAMP(6)      NOT NULL,
  updated_at      TIMESTAMP(6)      NOT NULL,
  CONSTRAINT pk_sys_user_dept PRIMARY KEY (id)
);
CREATE INDEX idx_ud_user ON sys_user_dept (user_id);
CREATE INDEX idx_ud_dept ON sys_user_dept (dept_id);
CREATE UNIQUE INDEX uk_ud_unique ON sys_user_dept (user_id, dept_id, start_date);

COMMENT ON TABLE sys_user_dept IS '人与部门关系。一人可多部门，是权限判断的唯一数据源';
COMMENT ON COLUMN sys_user_dept.is_primary IS '1 = 主部门，每人有且仅有一条为 1';
COMMENT ON COLUMN sys_user_dept.post_type IS 'PRIMARY 本职 / SECONDARY 兼岗';
COMMENT ON COLUMN sys_user_dept.end_date IS '离职或调岗时填结束日期，不删记录（保住历史审计）';


CREATE TABLE sys_role (
  id          VARCHAR2(64 CHAR)  NOT NULL,
  role_code   VARCHAR2(64 CHAR)  NOT NULL,
  role_name   VARCHAR2(100 CHAR) NOT NULL,
  is_system   NUMBER(1)          DEFAULT 0 NOT NULL,
  sort        NUMBER(10)         DEFAULT 0 NOT NULL,
  status      VARCHAR2(16 CHAR)  DEFAULT 'ACTIVE' NOT NULL,
  remark      VARCHAR2(500 CHAR),
  created_at  TIMESTAMP(6)       NOT NULL,
  updated_at  TIMESTAMP(6)       NOT NULL,
  is_deleted  NUMBER(1)          DEFAULT 0 NOT NULL,
  CONSTRAINT pk_sys_role PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_role_code ON sys_role (role_code);

COMMENT ON COLUMN sys_role.role_code IS 'REPORTER/MIT_DEV/MIT_LEADER/DEPT_MANAGER/ADMIN';
COMMENT ON COLUMN sys_role.is_system IS '1 = 内置角色，不允许删除';


CREATE TABLE sys_user_role (
  id          VARCHAR2(64 CHAR) NOT NULL,
  user_id     VARCHAR2(64 CHAR) NOT NULL,
  role_id     VARCHAR2(64 CHAR) NOT NULL,
  dept_id     VARCHAR2(64 CHAR),
  created_at  TIMESTAMP(6)      NOT NULL,
  CONSTRAINT pk_sys_user_role PRIMARY KEY (id)
);
CREATE INDEX idx_ur_user ON sys_user_role (user_id);
CREATE UNIQUE INDEX uk_ur_unique ON sys_user_role (user_id, role_id, dept_id);

COMMENT ON TABLE sys_user_role IS '用户角色。角色按部门生效，支持兼岗（A 部门 Leader / B 部门开发）';
COMMENT ON COLUMN sys_user_role.dept_id IS 'NULL = 全局角色（仅 ADMIN 这类）；非空 = 该角色仅在此部门生效';


CREATE TABLE sys_menu (
  id          VARCHAR2(64 CHAR)  NOT NULL,
  parent_id   VARCHAR2(64 CHAR),
  menu_name   VARCHAR2(100 CHAR) NOT NULL,
  menu_type   VARCHAR2(16 CHAR)  NOT NULL,
  path        VARCHAR2(255 CHAR),
  component   VARCHAR2(255 CHAR),
  redirect    VARCHAR2(255 CHAR),
  auth_code   VARCHAR2(100 CHAR),
  icon        VARCHAR2(100 CHAR),
  sort        NUMBER(10)         DEFAULT 0 NOT NULL,
  visible     NUMBER(1)          DEFAULT 1 NOT NULL,
  keep_alive  NUMBER(1)          DEFAULT 1 NOT NULL,
  link_url    VARCHAR2(500 CHAR),
  status      VARCHAR2(16 CHAR)  DEFAULT 'ACTIVE' NOT NULL,
  created_at  TIMESTAMP(6)       NOT NULL,
  updated_at  TIMESTAMP(6)       NOT NULL,
  is_deleted  NUMBER(1)          DEFAULT 0 NOT NULL,
  CONSTRAINT pk_sys_menu PRIMARY KEY (id)
);
CREATE INDEX idx_menu_parent ON sys_menu (parent_id);
CREATE INDEX idx_menu_auth ON sys_menu (auth_code);

COMMENT ON COLUMN sys_menu.menu_type IS 'DIR 目录 / MENU 菜单 / BUTTON 按钮 / EMBED 内嵌 / LINK 外链';
COMMENT ON COLUMN sys_menu.auth_code IS '按钮权限码，如 ticket:assign';


CREATE TABLE sys_role_menu (
  id          VARCHAR2(64 CHAR) NOT NULL,
  role_id     VARCHAR2(64 CHAR) NOT NULL,
  menu_id     VARCHAR2(64 CHAR) NOT NULL,
  created_at  TIMESTAMP(6)      NOT NULL,
  CONSTRAINT pk_sys_role_menu PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_rm_unique ON sys_role_menu (role_id, menu_id);


CREATE TABLE sys_dict_type (
  id          VARCHAR2(64 CHAR)  NOT NULL,
  dict_type   VARCHAR2(100 CHAR) NOT NULL,
  dict_name   VARCHAR2(100 CHAR) NOT NULL,
  remark      VARCHAR2(500 CHAR),
  status      VARCHAR2(16 CHAR)  DEFAULT 'ACTIVE' NOT NULL,
  created_at  TIMESTAMP(6)       NOT NULL,
  updated_at  TIMESTAMP(6)       NOT NULL,
  is_deleted  NUMBER(1)          DEFAULT 0 NOT NULL,
  CONSTRAINT pk_sys_dict_type PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_dict_type ON sys_dict_type (dict_type);


CREATE TABLE sys_dict_data (
  id          VARCHAR2(64 CHAR)  NOT NULL,
  dict_type   VARCHAR2(100 CHAR) NOT NULL,
  dict_label  VARCHAR2(100 CHAR) NOT NULL,
  dict_value  VARCHAR2(100 CHAR) NOT NULL,
  css_class   VARCHAR2(64 CHAR),
  sort        NUMBER(10)         DEFAULT 0 NOT NULL,
  status      VARCHAR2(16 CHAR)  DEFAULT 'ACTIVE' NOT NULL,
  remark      VARCHAR2(500 CHAR),
  created_at  TIMESTAMP(6)       NOT NULL,
  updated_at  TIMESTAMP(6)       NOT NULL,
  is_deleted  NUMBER(1)          DEFAULT 0 NOT NULL,
  CONSTRAINT pk_sys_dict_data PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_dict_data ON sys_dict_data (dict_type, dict_value);


CREATE TABLE sys_schema_version (
  id              VARCHAR2(64 CHAR)   NOT NULL,
  version         VARCHAR2(32 CHAR)   NOT NULL,
  script_name     VARCHAR2(255 CHAR)  NOT NULL,
  script_checksum VARCHAR2(64 CHAR),
  db_dialect      VARCHAR2(16 CHAR)   DEFAULT 'SQLITE' NOT NULL,
  applied_at      TIMESTAMP(6)        NOT NULL,
  applied_by      VARCHAR2(64 CHAR),
  cost_ms         NUMBER(10),
  success         NUMBER(1)           DEFAULT 1 NOT NULL,
  error_msg       VARCHAR2(2000 CHAR),
  remark          VARCHAR2(500 CHAR),
  created_at      TIMESTAMP(6)        NOT NULL,
  CONSTRAINT pk_sys_schema_version PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_schema_ver ON sys_schema_version (version, db_dialect);

COMMENT ON TABLE sys_schema_version IS '建表脚本版本记录，转库与升级的抓手';
COMMENT ON COLUMN sys_schema_version.script_checksum IS '脚本 SHA-256 前 16 位，能发现「脚本改过但没重跑」';
COMMENT ON COLUMN sys_schema_version.success IS '1 = 成功，0 = 失败；失败记录同样要留';
COMMENT ON COLUMN sys_schema_version.error_msg IS '失败原因';


-- =====================================================================
-- 二、权限（3 张）
-- =====================================================================

CREATE TABLE sys_data_scope_rule (
  id           VARCHAR2(64 CHAR) NOT NULL,
  role_code    VARCHAR2(64 CHAR) NOT NULL,
  ticket_type  VARCHAR2(16 CHAR) DEFAULT '*' NOT NULL,
  scope_type   VARCHAR2(32 CHAR) NOT NULL,
  extra_json   VARCHAR2(1000 CHAR),
  readable     NUMBER(1)         DEFAULT 1 NOT NULL,
  writable     NUMBER(1)         DEFAULT 1 NOT NULL,
  enabled      NUMBER(1)         DEFAULT 1 NOT NULL,
  remark       VARCHAR2(500 CHAR),
  created_at   TIMESTAMP(6)      NOT NULL,
  updated_at   TIMESTAMP(6)      NOT NULL,
  CONSTRAINT pk_sys_data_scope_rule PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_scope_rule ON sys_data_scope_rule (role_code, ticket_type);

COMMENT ON TABLE sys_data_scope_rule IS '数据权限规则。改权限改这张表，不发版';
COMMENT ON COLUMN sys_data_scope_rule.ticket_type IS 'REQ / BUG / INC / *（* 表示所有类型）';
COMMENT ON COLUMN sys_data_scope_rule.scope_type IS 'SELF/ASSIGNED/GROUP/DEPT/DEPT_AND_SUB/ALL/CUSTOM/NONE';
COMMENT ON COLUMN sys_data_scope_rule.writable IS '0 = 只读，如部门负责人只能看不能操作流转';


CREATE TABLE sys_user_group (
  id          VARCHAR2(64 CHAR)  NOT NULL,
  group_code  VARCHAR2(64 CHAR)  NOT NULL,
  group_name  VARCHAR2(100 CHAR) NOT NULL,
  remark      VARCHAR2(500 CHAR),
  status      VARCHAR2(16 CHAR)  DEFAULT 'ACTIVE' NOT NULL,
  created_at  TIMESTAMP(6)       NOT NULL,
  updated_at  TIMESTAMP(6)       NOT NULL,
  CONSTRAINT pk_sys_user_group PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_group_code ON sys_user_group (group_code);

COMMENT ON TABLE sys_user_group IS '自定义用户组。预留，用于跨部门临时授权（CUSTOM 范围指向这里）';


CREATE TABLE sys_user_group_member (
  id          VARCHAR2(64 CHAR) NOT NULL,
  group_id    VARCHAR2(64 CHAR) NOT NULL,
  user_id     VARCHAR2(64 CHAR) NOT NULL,
  created_at  TIMESTAMP(6)      NOT NULL,
  CONSTRAINT pk_sys_user_group_member PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_gm_unique ON sys_user_group_member (group_id, user_id);


-- =====================================================================
-- 三、业务单据（16 张）
-- =====================================================================

CREATE TABLE req_ticket (
  id                    VARCHAR2(64 CHAR)  NOT NULL,
  ticket_no             VARCHAR2(32 CHAR)  NOT NULL,
  ticket_type           VARCHAR2(8 CHAR)   NOT NULL,
  title                 VARCHAR2(200 CHAR) NOT NULL,
  description           CLOB,
  content_asis          CLOB,
  content_tobe          CLOB,
  status                VARCHAR2(32 CHAR)  NOT NULL,
  priority              VARCHAR2(16 CHAR),
  reporter_id           VARCHAR2(64 CHAR)  NOT NULL,
  creator_id            VARCHAR2(64 CHAR)  NOT NULL,
  owner_id              VARCHAR2(64 CHAR),
  dept_id               VARCHAR2(64 CHAR),
  assignee_id           VARCHAR2(64 CHAR),
  system_code           VARCHAR2(64 CHAR),
  module_code           VARCHAR2(64 CHAR),
  acceptance_criteria   CLOB,
  acceptance_locked     NUMBER(1)          DEFAULT 0 NOT NULL,
  acceptance_locked_at  TIMESTAMP(6),
  acceptance_locked_by  VARCHAR2(64 CHAR),
  roi_desc              CLOB,
  roi_value             NUMBER(10,2),
  propose_date          VARCHAR2(10 CHAR),
  expect_due_date       VARCHAR2(10 CHAR),
  estimated_hours       NUMBER(10,2),
  actual_hours          NUMBER(10,2),
  parent_id             VARCHAR2(64 CHAR),
  ticket_source         VARCHAR2(16 CHAR)  DEFAULT 'WEB' NOT NULL,
  dup_override          NUMBER(1)          DEFAULT 0 NOT NULL,
  override_reason       VARCHAR2(1000 CHAR),
  return_count          NUMBER(5)          DEFAULT 0 NOT NULL,
  suspended_from        VARCHAR2(32 CHAR),
  suspend_reason        VARCHAR2(500 CHAR),
  expect_resume_at      TIMESTAMP(6),
  close_reason          VARCHAR2(32 CHAR),
  closed_at             TIMESTAMP(6),
  submitted_at          TIMESTAMP(6),
  assigned_at           TIMESTAMP(6),
  design_at             TIMESTAMP(6),
  dev_at                TIMESTAMP(6),
  uat_at                TIMESTAMP(6),
  release_at            TIMESTAMP(6),                      -- 上线时间：满 7 天起算点（原 released_at，v1.6 统一命名）
  sla_due_at            TIMESTAMP(6),
  sla_overdue_hours     NUMBER(10,2)       DEFAULT 0 NOT NULL,
  branch_name_suggest   VARCHAR2(200 CHAR),
  superior_id           VARCHAR2(64 CHAR),                 -- 上级确认人快照（Leader/主任）；本人是 Leader 时为空
  superior_confirmed_at TIMESTAMP(6),                      -- 上级确认时间（LEADER_CONFIRM → REQ_REVIEW）
  superior_skipped      NUMBER(1)          DEFAULT 0 NOT NULL, -- 1 = 提出人本人即 Leader，按规则跳过确认
  review_passed_at      TIMESTAMP(6),                      -- 需求评审通过时间（REQ_REVIEW → PENDING_ASSIGN）
  eval_result           VARCHAR2(16 CHAR),                 -- 上线评价结论：PASS 符合 / FAIL 不符合
  satisfaction_score    NUMBER(2),                         -- 满意度 1~5，仅 eval_result=PASS 时填
  eval_comment          CLOB,                              -- FAIL 时必填的不符合原因；PASS 时为可选评语
  evaluated_at          TIMESTAMP(6),                      -- 评价提交时间
  evaluated_by          VARCHAR2(64 CHAR),                 -- 评价人（提出人或需求 Owner）
  -- ---- v1.6 新增：项目归属 / 流程标记 / 上线后验证 ----
  project_id            VARCHAR2(64 CHAR)  NOT NULL,       -- 所属项目，提单时快照
  system_id             VARCHAR2(64 CHAR)  NOT NULL,       -- 所属系统（主关联），下拉按当前项目过滤
  mit_skipped           NUMBER(1)          DEFAULT 0 NOT NULL,
  platform_skipped      NUMBER(1)          DEFAULT 0 NOT NULL,
  -- v0.7 并行化：三方确认与设计评审不分先后，各方通过后置 1；全 1 由服务层原子升级状态
  mit_passed            NUMBER(1)          DEFAULT 0 NOT NULL,   -- 1 = MIT 已确认（CONFIRMING 阶段）
  platform_passed       NUMBER(1)          DEFAULT 0 NOT NULL,   -- 1 = 平台已确认（CONFIRMING 阶段）
  superior_passed       NUMBER(1)          DEFAULT 0 NOT NULL,   -- 1 = 上级已确认（CONFIRMING 阶段）
  dsgn_it_passed        NUMBER(1)          DEFAULT 0 NOT NULL,   -- 1 = IT 评审已通过（DSGN_REVIEWING 阶段）
  dsgn_platform_passed  NUMBER(1)          DEFAULT 0 NOT NULL,   -- 1 = 平台评审已通过（DSGN_REVIEWING 阶段）
  dsgn_user_passed      NUMBER(1)          DEFAULT 0 NOT NULL,   -- 1 = 用户评审已通过（DSGN_REVIEWING 阶段）
  doc_confirmed_at      TIMESTAMP(6),
  run_stable_days       NUMBER(3)          DEFAULT 7 NOT NULL,   -- 观察期天数：REQ 7 / BUG 3
  ai_degraded           NUMBER(1)          DEFAULT 0 NOT NULL,
  ai_clarify_round      NUMBER(3)          DEFAULT 0 NOT NULL,   -- 已进行轮次，上限 3
  monitor_note          CLOB,                              -- 运行监控结论，提交时校验 ≥20 字
  related_inc_id        VARCHAR2(64 CHAR),                 -- 未关闭则满 7 天计时暂停
  uat_waiver            NUMBER(1)          DEFAULT 0 NOT NULL,   -- 1 = 覆盖率 <100% 由 Leader 豁免
  uat_waiver_reason     VARCHAR2(1000 CHAR),
  created_at            TIMESTAMP(6)       NOT NULL,
  updated_at            TIMESTAMP(6)       NOT NULL,
  updated_by            VARCHAR2(64 CHAR),
  CONSTRAINT pk_req_ticket PRIMARY KEY (id)
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

COMMENT ON TABLE req_ticket IS '单据主表，REQ/BUG/INC 共用，靠 ticket_type 区分';
COMMENT ON COLUMN req_ticket.ticket_type IS 'REQ 需求 / BUG 缺陷 / INC 故障';
COMMENT ON COLUMN req_ticket.status IS '27 态，见 02-流程与退回机制 第四、五节';
COMMENT ON COLUMN req_ticket.reporter_id IS '提出人（统计口径用这个）';
COMMENT ON COLUMN req_ticket.creator_id IS '录入人，可为代录的助理；与提出人相同则为本人录入';
COMMENT ON COLUMN req_ticket.dept_id IS '提交时提出人主部门快照，不随调岗变化';
COMMENT ON COLUMN req_ticket.acceptance_locked IS '1 = 设计确认后锁定，之后修改走变更记录';
COMMENT ON COLUMN req_ticket.dup_override IS '1 = AI 判重复但用户坚持新建；报表单独统计';
COMMENT ON COLUMN req_ticket.close_reason IS 'DONE 完成 / REJECT 驳回 / DUP 重复 / CANCEL 取消';
COMMENT ON COLUMN req_ticket.sla_overdue_hours IS '累计超期小时，退回不归零';


CREATE TABLE req_flow_log (
  id                VARCHAR2(64 CHAR)  NOT NULL,
  ticket_id         VARCHAR2(64 CHAR)  NOT NULL,
  from_status       VARCHAR2(32 CHAR),
  to_status         VARCHAR2(32 CHAR)  NOT NULL,
  action            VARCHAR2(64 CHAR)  NOT NULL,
  operator_id       VARCHAR2(64 CHAR),
  operator_dept_id  VARCHAR2(64 CHAR),
  reason_code       VARCHAR2(64 CHAR),
  remark            VARCHAR2(1000 CHAR),
  return_point      VARCHAR2(32 CHAR),
  return_seq        NUMBER(5),
  created_at        TIMESTAMP(6)       NOT NULL,
  CONSTRAINT pk_req_flow_log PRIMARY KEY (id)
);
CREATE INDEX idx_flow_ticket ON req_flow_log (ticket_id, created_at);
CREATE INDEX idx_flow_return ON req_flow_log (ticket_id, return_point);

COMMENT ON TABLE req_flow_log IS '流转时间线。只追加，不更新不删除';
COMMENT ON COLUMN req_flow_log.operator_dept_id IS '操作时所在部门快照，兼岗场景留痕用';
COMMENT ON COLUMN req_flow_log.return_point IS '退回点码（如 RT_DESIGN），同一退回点第 3 次升级按它计次';
COMMENT ON COLUMN req_flow_log.return_seq IS '第几次退回（全局序号，展示用）';


CREATE TABLE req_change_log (
  id            VARCHAR2(64 CHAR)   NOT NULL,
  ticket_id     VARCHAR2(64 CHAR)   NOT NULL,
  field_name    VARCHAR2(64 CHAR)   NOT NULL,
  field_label   VARCHAR2(100 CHAR)  NOT NULL,
  old_value     CLOB,
  new_value     CLOB,
  operator_id   VARCHAR2(64 CHAR),
  change_source VARCHAR2(16 CHAR)   DEFAULT 'FORM' NOT NULL,
  reason        VARCHAR2(500 CHAR),
  created_at    TIMESTAMP(6)        NOT NULL,
  CONSTRAINT pk_req_change_log PRIMARY KEY (id)
);
CREATE INDEX idx_change_ticket ON req_change_log (ticket_id, created_at);

COMMENT ON TABLE req_change_log IS '字段级变更历史。只追加，回答「内容被谁改成什么样」';
COMMENT ON COLUMN req_change_log.field_label IS '字段中文名，界面直接渲染，不靠前端映射';
COMMENT ON COLUMN req_change_log.change_source IS 'FORM 表单改 / FLOW 流程动作顺带改 / SYSTEM 系统计算 / MIGRATE 迁移；统计修改次数时排除后两类';


CREATE TABLE req_analysis (
  id              VARCHAR2(64 CHAR)   NOT NULL,
  ticket_id       VARCHAR2(64 CHAR)   NOT NULL,
  sim_score       NUMBER(5,4),
  verdict         VARCHAR2(16 CHAR),
  similar_list    CLOB,
  suggestion      CLOB,
  roi_json        CLOB,
  trace_json      CLOB,
  analysis_mode   VARCHAR2(16 CHAR)   DEFAULT 'RULE' NOT NULL,
  engine_version  VARCHAR2(32 CHAR),
  prompt_version  VARCHAR2(32 CHAR),
  override_flag   NUMBER(1)           DEFAULT 0 NOT NULL,
  override_reason VARCHAR2(1000 CHAR),
  created_at      TIMESTAMP(6)        NOT NULL,
  CONSTRAINT pk_req_analysis PRIMARY KEY (id)
);
CREATE INDEX idx_analysis_ticket ON req_analysis (ticket_id, created_at);

COMMENT ON COLUMN req_analysis.sim_score IS '最高相似度 0~1';
COMMENT ON COLUMN req_analysis.verdict IS 'NEW 新需求 / PARTIAL 部分相似 / DUPLICATE 重复';
COMMENT ON COLUMN req_analysis.analysis_mode IS 'RULE 规则版 / LLM 大模型 / RULE_FALLBACK 模型失败降级';
COMMENT ON TABLE req_analysis.override_flag IS '1 = 人覆盖了 AI 结论，原因是优化引擎的数据来源';


-- 验收标准子表。主表 acceptance_criteria 是原文，本表是提交时拆分的结构化结果；
-- req_test_case.criteria_index 指向 seq，覆盖率分母 = 本表条数（09 第四节 #2）
CREATE TABLE req_acceptance_criteria (
  id          VARCHAR2(64 CHAR)   NOT NULL,
  ticket_id   VARCHAR2(64 CHAR)   NOT NULL,
  seq         NUMBER(5)           NOT NULL,
  content     VARCHAR2(2000 CHAR) NOT NULL,
  locked      NUMBER(1)           DEFAULT 0 NOT NULL,
  created_at  TIMESTAMP(6)        NOT NULL,
  updated_at  TIMESTAMP(6)        NOT NULL,
  CONSTRAINT pk_req_acceptance_criteria PRIMARY KEY (id)
);
CREATE INDEX idx_accrit_ticket ON req_acceptance_criteria (ticket_id, seq);

COMMENT ON TABLE req_acceptance_criteria IS '验收标准子表：逐条结构化，用例覆盖率与 UAT 放行校验的依据';
COMMENT ON COLUMN req_acceptance_criteria.seq IS '第几条，从 1 开始';
COMMENT ON COLUMN req_acceptance_criteria.locked IS '1 = 已锁定（随主单 acceptance_locked）';


CREATE TABLE req_attachment (
  id             VARCHAR2(64 CHAR)  NOT NULL,
  ticket_id      VARCHAR2(64 CHAR)  NOT NULL,
  stage          VARCHAR2(32 CHAR)  NOT NULL,
  attach_type    VARCHAR2(32 CHAR)  NOT NULL,
  original_name  VARCHAR2(255 CHAR) NOT NULL,
  relative_path  VARCHAR2(500 CHAR) NOT NULL,
  size           NUMBER(15),
  mime_type      VARCHAR2(128 CHAR),
  sha256         VARCHAR2(64 CHAR),
  extracted_text CLOB,
  uploader_id    VARCHAR2(64 CHAR),
  is_confirmed   NUMBER(1)          DEFAULT 0 NOT NULL,
  deleted        NUMBER(1)          DEFAULT 0 NOT NULL,
  created_at     TIMESTAMP(6)       NOT NULL,
  CONSTRAINT pk_req_attachment PRIMARY KEY (id)
);
CREATE INDEX idx_att_ticket ON req_attachment (ticket_id, stage);
CREATE INDEX idx_att_sha ON req_attachment (sha256);

COMMENT ON COLUMN req_attachment.stage IS '上传时所处状态码，详情页按它分组展示';
COMMENT ON COLUMN req_attachment.attach_type IS '交付物类型：DESIGN_DOC/SELF_TEST/RELEASE_ORDER/ROLLBACK_PLAN/SCREENSHOT/OTHER';
COMMENT ON COLUMN req_attachment.relative_path IS '相对路径，绝对路径放配置，换机器或转库不改数据';
COMMENT ON COLUMN req_attachment.sha256 IS '文件指纹：重复上传检测 + 完整性校验';
COMMENT ON COLUMN req_attachment.extracted_text IS '正文提取结果，作为 AI 分析的输入';
COMMENT ON COLUMN req_attachment.deleted IS '1 = 软删除，附件是审计证据不做物理删';


CREATE TABLE req_attach_rule (
  id          VARCHAR2(64 CHAR) NOT NULL,
  ticket_type VARCHAR2(16 CHAR) DEFAULT '*' NOT NULL,
  status      VARCHAR2(32 CHAR) DEFAULT '*' NOT NULL,
  attach_type VARCHAR2(32 CHAR) NOT NULL,
  min_count   NUMBER(5)         DEFAULT 1 NOT NULL,
  required    NUMBER(1)         DEFAULT 1 NOT NULL,
  enabled     NUMBER(1)         DEFAULT 1 NOT NULL,
  remark      VARCHAR2(500 CHAR),
  created_at  TIMESTAMP(6)      NOT NULL,
  updated_at  TIMESTAMP(6)      NOT NULL,
  CONSTRAINT pk_req_attach_rule PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_attach_rule ON req_attach_rule (ticket_type, status, attach_type);

COMMENT ON TABLE req_attach_rule IS '附件必填规则：单据类型 + 状态 → 必传交付物，改规则不改代码';
COMMENT ON COLUMN req_attach_rule.status IS '目标状态码（to_status）：流转到达该状态时校验必填，附件的 stage 记 from_status，* 表示所有状态';
COMMENT ON COLUMN req_attach_rule.required IS '1 = 不满足不许流转（点按钮时校验，不做事后提醒）';


CREATE TABLE req_test_case (
  id              VARCHAR2(64 CHAR)  NOT NULL,
  ticket_id       VARCHAR2(64 CHAR)  NOT NULL,
  case_no         VARCHAR2(64 CHAR)  NOT NULL,
  title           VARCHAR2(500 CHAR) NOT NULL,
  precond         VARCHAR2(2000 CHAR),
  steps           CLOB,
  expected        VARCHAR2(2000 CHAR),
  priority        VARCHAR2(8 CHAR)   DEFAULT 'P1' NOT NULL,
  source_criteria VARCHAR2(2000 CHAR),
  criteria_index  NUMBER(5),
  focus_flag      NUMBER(1)          DEFAULT 0 NOT NULL,
  focus_reason    VARCHAR2(1000 CHAR),
  origin          VARCHAR2(16 CHAR)  DEFAULT 'AI' NOT NULL,
  exec_status     VARCHAR2(16 CHAR)  DEFAULT 'NOT_RUN' NOT NULL,
  bug_id          VARCHAR2(64 CHAR),
  round_no        NUMBER(5)          DEFAULT 1 NOT NULL,
  created_by      VARCHAR2(64 CHAR),
  created_at      TIMESTAMP(6)       NOT NULL,
  updated_at      TIMESTAMP(6)       NOT NULL,
  CONSTRAINT pk_req_test_case PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_case_no ON req_test_case (case_no);
CREATE INDEX idx_case_ticket ON req_test_case (ticket_id, round_no);

COMMENT ON TABLE req_test_case IS 'AI 生成的测试用例，结构化落表才能算「验收标准 → 用例」覆盖率';
COMMENT ON COLUMN req_test_case.steps IS 'JSON 数组：[{seq,action,data}]';
COMMENT ON COLUMN req_test_case.criteria_index IS '对应第几条验收标准，覆盖率的分母依据';
COMMENT ON COLUMN req_test_case.focus_reason IS '为什么重点关注；只标红不说原因是无效的';
COMMENT ON COLUMN req_test_case.round_no IS '生成批次：设计确认后第 1 轮，UAT 前刷新为第 2 轮';


CREATE TABLE req_case_result (
  id            VARCHAR2(64 CHAR)  NOT NULL,
  case_id       VARCHAR2(64 CHAR)  NOT NULL,
  ticket_id     VARCHAR2(64 CHAR)  NOT NULL,
  round_no      NUMBER(5)          DEFAULT 1 NOT NULL,
  exec_status   VARCHAR2(16 CHAR)  NOT NULL,
  actual_result VARCHAR2(2000 CHAR),
  executor_id   VARCHAR2(64 CHAR),
  executed_at   TIMESTAMP(6),
  bug_id        VARCHAR2(64 CHAR),
  remark        VARCHAR2(1000 CHAR),
  created_at    TIMESTAMP(6)       NOT NULL,
  CONSTRAINT pk_req_case_result PRIMARY KEY (id)
);
CREATE INDEX idx_caseres_case ON req_case_result (case_id, round_no);
CREATE INDEX idx_caseres_ticket ON req_case_result (ticket_id);

COMMENT ON TABLE req_case_result IS '用例执行记录，多次回归每轮一条，不覆盖历史';
COMMENT ON COLUMN req_case_result.exec_status IS 'PASS / FAIL / SKIP';


CREATE TABLE req_comment (
  id          VARCHAR2(64 CHAR) NOT NULL,
  ticket_id   VARCHAR2(64 CHAR) NOT NULL,
  author_id   VARCHAR2(64 CHAR) NOT NULL,
  content     CLOB              NOT NULL,
  is_internal NUMBER(1)         DEFAULT 0 NOT NULL,
  created_at  TIMESTAMP(6)      NOT NULL,
  CONSTRAINT pk_req_comment PRIMARY KEY (id)
);
CREATE INDEX idx_comment_ticket ON req_comment (ticket_id, created_at);

COMMENT ON COLUMN req_comment.is_internal IS '1 = 仅内部可见，对提出人不显示';


CREATE TABLE req_review (
  id            VARCHAR2(64 CHAR)   NOT NULL,
  ticket_id     VARCHAR2(64 CHAR)   NOT NULL,
  review_type   VARCHAR2(32 CHAR)   DEFAULT 'ESCALATION' NOT NULL,
  conclusion    VARCHAR2(32 CHAR)   NOT NULL,
  resume_status VARCHAR2(32 CHAR),
  participants  VARCHAR2(500 CHAR),
  minutes       CLOB,
  reviewer_id   VARCHAR2(64 CHAR),
  reviewed_at   TIMESTAMP(6),
  created_at    TIMESTAMP(6)        NOT NULL,
  CONSTRAINT pk_req_review PRIMARY KEY (id)
);
CREATE INDEX idx_review_ticket ON req_review (ticket_id);

COMMENT ON TABLE req_review IS '评审记录。第 3 次退回后线下开会，线上只登记结论';
COMMENT ON COLUMN req_review.conclusion IS 'CONTINUE 继续 / CLOSE 关闭 / REASSIGN 重派';
COMMENT ON COLUMN req_review.resume_status IS '选 CONTINUE 时必填，指定从哪一步重来，不许直接回原状态';


CREATE TABLE req_link (
  id          VARCHAR2(64 CHAR)  NOT NULL,
  ticket_id   VARCHAR2(64 CHAR)  NOT NULL,
  link_type   VARCHAR2(32 CHAR)  NOT NULL,
  external_id VARCHAR2(255 CHAR),
  url         VARCHAR2(1000 CHAR) NOT NULL,
  title       VARCHAR2(255 CHAR),
  status      VARCHAR2(32 CHAR),
  synced_at   TIMESTAMP(6),
  link_source VARCHAR2(16 CHAR)  DEFAULT 'MANUAL' NOT NULL,
  created_at  TIMESTAMP(6)       NOT NULL,
  CONSTRAINT pk_req_link PRIMARY KEY (id)
);
CREATE INDEX idx_link_ticket ON req_link (ticket_id, link_type);

COMMENT ON TABLE req_link IS '通用外部关联。加类型不改表结构，link_type 走字典';
COMMENT ON COLUMN req_link.link_type IS 'BRANCH/MR/COMMIT/CI_BUILD/DEPLOY/DOC/OTHER';
COMMENT ON COLUMN req_link.link_source IS 'MANUAL 手工填 / WEBHOOK 回调写入 / POLLING 轮询采集';


CREATE TABLE req_deploy (
  id             VARCHAR2(64 CHAR)  NOT NULL,
  ticket_id      VARCHAR2(64 CHAR)  NOT NULL,
  env            VARCHAR2(16 CHAR)  NOT NULL,
  version        VARCHAR2(64 CHAR),
  image_tag      VARCHAR2(128 CHAR),
  deploy_type    VARCHAR2(32 CHAR)  DEFAULT 'MANUAL' NOT NULL,
  deploy_status  VARCHAR2(16 CHAR)  DEFAULT 'SUCCESS' NOT NULL,
  log_url        VARCHAR2(1000 CHAR),
  rollback_point VARCHAR2(255 CHAR),
  operator_id    VARCHAR2(64 CHAR),
  started_at     TIMESTAMP(6),
  finished_at    TIMESTAMP(6),
  created_at     TIMESTAMP(6)       NOT NULL,
  CONSTRAINT pk_req_deploy PRIMARY KEY (id)
);
CREATE INDEX idx_deploy_ticket ON req_deploy (ticket_id, env);

COMMENT ON COLUMN req_deploy.image_tag IS '容器化部署的回滚点';
COMMENT ON COLUMN req_deploy.deploy_status IS 'SUCCESS / FAILED / RUNNING';


CREATE TABLE req_release (
  id            VARCHAR2(64 CHAR)  NOT NULL,
  ticket_id     VARCHAR2(64 CHAR)  NOT NULL,
  env           VARCHAR2(16 CHAR)  DEFAULT 'PROD' NOT NULL,
  deployer_id   VARCHAR2(64 CHAR),
  rollback_plan CLOB,
  window_start  TIMESTAMP(6),
  window_end    TIMESTAMP(6),
  approver_id   VARCHAR2(64 CHAR),
  approved_at   TIMESTAMP(6),
  result        VARCHAR2(16 CHAR),
  created_at    TIMESTAMP(6)       NOT NULL,
  CONSTRAINT pk_req_release PRIMARY KEY (id)
);
CREATE INDEX idx_release_ticket ON req_release (ticket_id);

COMMENT ON COLUMN req_release.result IS 'SUCCESS / FAILED / ROLLBACK';


CREATE TABLE req_env_lock (
  id          VARCHAR2(64 CHAR) NOT NULL,
  env         VARCHAR2(16 CHAR) NOT NULL,
  ticket_id   VARCHAR2(64 CHAR) NOT NULL,
  locked_by   VARCHAR2(64 CHAR),
  locked_at   TIMESTAMP(6)      NOT NULL,
  expire_at   TIMESTAMP(6),
  released_at TIMESTAMP(6),
  CONSTRAINT pk_req_env_lock PRIMARY KEY (id)
);
CREATE INDEX idx_envlock_env ON req_env_lock (env, released_at);

COMMENT ON TABLE req_env_lock IS '环境占用锁。UAT 只有 1 套，防两个需求同时部署互相覆盖';


CREATE TABLE req_incident_review (
  id           VARCHAR2(64 CHAR) NOT NULL,
  ticket_id    VARCHAR2(64 CHAR) NOT NULL,
  root_cause   CLOB,
  impact       CLOB,
  action_items CLOB,
  reviewed_at  TIMESTAMP(6),
  created_at   TIMESTAMP(6)      NOT NULL,
  CONSTRAINT pk_req_incident_review PRIMARY KEY (id)
);
CREATE INDEX idx_incident_ticket ON req_incident_review (ticket_id);

COMMENT ON TABLE req_incident_review IS '故障复盘。INC 类型必填';


-- =====================================================================
-- 四、主数据与流程文档（v1.6 新增 5 张）
-- =====================================================================

-- 项目主数据。需求的最上层归属；所有项目共用同一套 27 态流程，一期不做流程模板
CREATE TABLE req_project (
  id            VARCHAR2(64 CHAR)   NOT NULL,
  project_code  VARCHAR2(64 CHAR)   NOT NULL,
  project_name  VARCHAR2(200 CHAR)  NOT NULL,
  project_type  VARCHAR2(32 CHAR),
  status        VARCHAR2(16 CHAR)   DEFAULT 'PLANNING' NOT NULL,
  owner_id      VARCHAR2(64 CHAR),
  dept_id       VARCHAR2(64 CHAR),
  start_date    VARCHAR2(10 CHAR),
  end_date      VARCHAR2(10 CHAR),
  remark        VARCHAR2(1000 CHAR),
  sort_no       NUMBER(10)          DEFAULT 0 NOT NULL,
  created_at    TIMESTAMP(6)        NOT NULL,
  updated_at    TIMESTAMP(6)        NOT NULL,
  deleted       NUMBER(1)           DEFAULT 0 NOT NULL,
  CONSTRAINT pk_req_project PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_project_code ON req_project (project_code);
CREATE INDEX idx_project_status ON req_project (status, sort_no);

COMMENT ON COLUMN req_project.project_code IS '全局唯一；一经使用不再改（附件目录第一级）';
COMMENT ON COLUMN req_project.status IS 'PLANNING / ACTIVE / SUSPENDED / CLOSED；已关闭项目不允许再提新需求';

-- 系统主数据。挂在项目下，只属一个项目；system_code 项目内唯一
CREATE TABLE req_system (
  id           VARCHAR2(64 CHAR)   NOT NULL,
  project_id   VARCHAR2(64 CHAR)   NOT NULL,
  system_code  VARCHAR2(64 CHAR)   NOT NULL,
  system_name  VARCHAR2(200 CHAR)  NOT NULL,
  system_type  VARCHAR2(32 CHAR),
  owner_id     VARCHAR2(64 CHAR),
  status       VARCHAR2(16 CHAR)   DEFAULT 'BUILDING' NOT NULL,
  remark       VARCHAR2(1000 CHAR),
  sort_no      NUMBER(10)          DEFAULT 0 NOT NULL,
  created_at   TIMESTAMP(6)        NOT NULL,
  updated_at   TIMESTAMP(6)        NOT NULL,
  deleted      NUMBER(1)           DEFAULT 0 NOT NULL,
  CONSTRAINT pk_req_system PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_system_project_code ON req_system (project_id, system_code);
CREATE INDEX idx_system_project ON req_system (project_id, status);

COMMENT ON COLUMN req_system.system_type IS 'PURCHASED 外购 / INHOUSE 自研 / RUNNING 在运';
COMMENT ON COLUMN req_system.status IS 'BUILDING / LIVE / OFFLINE';

-- AI 生成的需求文档。12 节结构化存 doc_json，节标题固定
CREATE TABLE req_doc (
  id            VARCHAR2(64 CHAR)   NOT NULL,
  ticket_id     VARCHAR2(64 CHAR)   NOT NULL,
  version       NUMBER(5)           DEFAULT 1 NOT NULL,
  content_md    CLOB,
  doc_json      CLOB,
  ai_model      VARCHAR2(128 CHAR),
  ai_degraded   NUMBER(1)           DEFAULT 0 NOT NULL,
  confirmed_at  TIMESTAMP(6),
  confirmed_by  VARCHAR2(64 CHAR),
  created_at    TIMESTAMP(6)        NOT NULL,
  updated_at    TIMESTAMP(6)        NOT NULL,
  CONSTRAINT pk_req_doc PRIMARY KEY (id)
);
CREATE INDEX idx_doc_ticket ON req_doc (ticket_id, version);

COMMENT ON COLUMN req_doc.doc_json IS '12 节结构化 JSON，第 5 节功能清单用于生成点检项草稿';
COMMENT ON COLUMN req_doc.ai_degraded IS '1 = AI 超时降级为模板空壳，前端打黄标';

-- AI 澄清问答。按 dimension 去重，实现「已答过的不重问」
CREATE TABLE req_ai_clarify (
  id           VARCHAR2(64 CHAR)   NOT NULL,
  ticket_id    VARCHAR2(64 CHAR)   NOT NULL,
  round_no     NUMBER(3)           NOT NULL,
  dimension    VARCHAR2(32 CHAR),
  question     VARCHAR2(2000 CHAR) NOT NULL,
  answer       VARCHAR2(4000 CHAR),
  answered_at  TIMESTAMP(6),
  created_at   TIMESTAMP(6)        NOT NULL,
  CONSTRAINT pk_req_ai_clarify PRIMARY KEY (id)
);
CREATE INDEX idx_clarify_ticket ON req_ai_clarify (ticket_id, round_no);

COMMENT ON COLUMN req_ai_clarify.dimension IS 'USER / PROBLEM / ASIS / TOBE / SUCCESS';

-- 重点功能点检清单。初始为空、必须可维护；为空时不卡流程
CREATE TABLE req_check_item (
  id            VARCHAR2(64 CHAR)   NOT NULL,
  ticket_id     VARCHAR2(64 CHAR)   NOT NULL,
  system_id     VARCHAR2(64 CHAR),
  item_name     VARCHAR2(500 CHAR)  NOT NULL,
  item_desc     VARCHAR2(2000 CHAR),
  expect_result VARCHAR2(2000 CHAR),
  is_key        NUMBER(1)           DEFAULT 1 NOT NULL,
  sort_no       NUMBER(10)          DEFAULT 0 NOT NULL,
  source        VARCHAR2(16 CHAR)   DEFAULT 'MANUAL' NOT NULL,
  check_result  VARCHAR2(16 CHAR),
  check_remark  VARCHAR2(2000 CHAR),
  checked_by    VARCHAR2(64 CHAR),
  checked_at    TIMESTAMP(6),
  created_at    TIMESTAMP(6)        NOT NULL,
  updated_at    TIMESTAMP(6)        NOT NULL,
  CONSTRAINT pk_req_check_item PRIMARY KEY (id)
);
CREATE INDEX idx_check_ticket ON req_check_item (ticket_id, sort_no);
CREATE INDEX idx_check_system ON req_check_item (system_id, is_key);

COMMENT ON COLUMN req_check_item.source IS 'MANUAL 人工维护 / AI_SUGGEST 从需求文档第 5 节带出';
COMMENT ON COLUMN req_check_item.check_result IS 'PASS / FAIL / NA，点检时填';


-- =====================================================================
-- 五、配置与通知（11 张）
-- =====================================================================

CREATE TABLE sys_work_calendar (
  id          VARCHAR2(64 CHAR)  NOT NULL,
  cal_date    VARCHAR2(10 CHAR)  NOT NULL,
  year_no     NUMBER(5)          NOT NULL,
  day_type    VARCHAR2(16 CHAR)  NOT NULL,
  day_name    VARCHAR2(64 CHAR),
  work_hours  NUMBER(5,2)        DEFAULT 8 NOT NULL,
  start_time  VARCHAR2(8 CHAR),
  end_time    VARCHAR2(8 CHAR),
  remark      VARCHAR2(500 CHAR),
  created_at  TIMESTAMP(6)       NOT NULL,
  updated_at  TIMESTAMP(6)       NOT NULL,
  CONSTRAINT pk_sys_work_calendar PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_cal_date ON sys_work_calendar (cal_date);
CREATE INDEX idx_cal_year ON sys_work_calendar (year_no, day_type);

COMMENT ON TABLE sys_work_calendar IS '工作日历，SLA 按工作日折算的依据；缺失会导致节假日全量误报超期';
COMMENT ON COLUMN sys_work_calendar.cal_date IS 'YYYY-MM-DD 字符串，字典序即时间序，避开 Oracle DATE 含时分秒的坑';
COMMENT ON COLUMN sys_work_calendar.day_type IS 'WORKDAY 工作日 / WEEKEND 周末 / HOLIDAY 节假日 / MAKEUP 调休上班';
COMMENT ON COLUMN sys_work_calendar.work_hours IS '当日可工作小时数，非工作日填 0';


CREATE TABLE req_sla_config (
  id             VARCHAR2(64 CHAR) NOT NULL,
  ticket_type    VARCHAR2(16 CHAR) DEFAULT '*' NOT NULL,
  priority       VARCHAR2(16 CHAR) DEFAULT '*' NOT NULL,
  status         VARCHAR2(32 CHAR) NOT NULL,
  limit_hours    NUMBER(10,2)      NOT NULL,
  warn_hours     NUMBER(10,2),
  overdue_action VARCHAR2(32 CHAR),
  enabled        NUMBER(1)         DEFAULT 1 NOT NULL,
  remark         VARCHAR2(500 CHAR),
  created_at     TIMESTAMP(6)      NOT NULL,
  updated_at     TIMESTAMP(6)      NOT NULL,
  CONSTRAINT pk_req_sla_config PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_sla_config ON req_sla_config (ticket_type, priority, status);

COMMENT ON TABLE req_sla_config IS 'SLA 配置。数值可配，不写死在代码里';
COMMENT ON COLUMN req_sla_config.limit_hours IS '时限（小时），按工作日历折算';
COMMENT ON COLUMN req_sla_config.overdue_action IS 'NOTIFY 提醒 / ESCALATE 升级 / CLOSE 自动关闭';


CREATE TABLE req_notify (
  id            VARCHAR2(64 CHAR)   NOT NULL,
  ticket_id     VARCHAR2(64 CHAR),
  receiver_id   VARCHAR2(64 CHAR),
  channel       VARCHAR2(16 CHAR)   NOT NULL,
  template_code VARCHAR2(64 CHAR),
  title         VARCHAR2(255 CHAR),
  content       CLOB,
  sent_at       TIMESTAMP(6),
  read_at       TIMESTAMP(6),
  send_result   VARCHAR2(500 CHAR),
  retry_count   NUMBER(5)           DEFAULT 0 NOT NULL,
  created_at    TIMESTAMP(6)        NOT NULL,
  CONSTRAINT pk_req_notify PRIMARY KEY (id)
);
CREATE INDEX idx_notify_receiver ON req_notify (receiver_id, read_at);
CREATE INDEX idx_notify_ticket ON req_notify (ticket_id, created_at);

COMMENT ON COLUMN req_notify.channel IS 'SITE 站内信 / DINGTALK 钉钉群机器人 / MAIL 邮件（一期不做）';


CREATE TABLE sys_notify_channel (
  id           VARCHAR2(64 CHAR)   NOT NULL,
  channel_code VARCHAR2(32 CHAR)   NOT NULL,
  enabled      NUMBER(1)           DEFAULT 0 NOT NULL,
  webhook_url  VARCHAR2(1000 CHAR),
  secret       VARCHAR2(255 CHAR),
  msg_type     VARCHAR2(32 CHAR)   DEFAULT 'markdown',
  at_mobiles   VARCHAR2(500 CHAR),
  retry_max    NUMBER(3)           DEFAULT 2 NOT NULL,
  timeout_ms   NUMBER(10)          DEFAULT 5000 NOT NULL,
  remark       VARCHAR2(500 CHAR),
  created_at   TIMESTAMP(6)        NOT NULL,
  updated_at   TIMESTAMP(6)        NOT NULL,
  CONSTRAINT pk_sys_notify_channel PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_channel_code ON sys_notify_channel (channel_code);

COMMENT ON COLUMN sys_notify_channel.secret IS '钉钉加签密钥，加密存储；不写进 yml';


CREATE TABLE sys_notify_template (
  id            VARCHAR2(64 CHAR)   NOT NULL,
  template_code VARCHAR2(64 CHAR)   NOT NULL,
  channel_code  VARCHAR2(32 CHAR)   DEFAULT 'SITE' NOT NULL,
  title_tpl     VARCHAR2(255 CHAR),
  content_tpl   CLOB,
  enabled       NUMBER(1)           DEFAULT 1 NOT NULL,
  remark        VARCHAR2(500 CHAR),
  created_at    TIMESTAMP(6)        NOT NULL,
  updated_at    TIMESTAMP(6)        NOT NULL,
  is_deleted    NUMBER(1)           DEFAULT 0 NOT NULL,
  CONSTRAINT pk_sys_notify_template PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_tpl_code ON sys_notify_template (template_code, channel_code);

COMMENT ON COLUMN sys_notify_template.content_tpl IS '用 ${ticketNo} ${title} ${status} 占位；运营可改文案不发版';


CREATE TABLE sys_deploy_config (
  id             VARCHAR2(64 CHAR)   NOT NULL,
  system_code    VARCHAR2(64 CHAR)   NOT NULL,
  env            VARCHAR2(16 CHAR)   NOT NULL,
  deploy_type    VARCHAR2(32 CHAR)   NOT NULL,
  target_ref     VARCHAR2(255 CHAR),
  param_template CLOB,
  enabled        NUMBER(1)           DEFAULT 0 NOT NULL,
  remark         VARCHAR2(500 CHAR),
  created_at     TIMESTAMP(6)        NOT NULL,
  updated_at     TIMESTAMP(6)        NOT NULL,
  CONSTRAINT pk_sys_deploy_config PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_deploy_cfg ON sys_deploy_config (system_code, env);

COMMENT ON TABLE sys_deploy_config IS '部署配置。没有这张表，一键部署只能对单个系统硬编码';
COMMENT ON COLUMN sys_deploy_config.deploy_type IS 'JENKINS_JOB/GITLAB_PIPELINE/SSH_SCRIPT/MANUAL';


CREATE TABLE sys_no_sequence (
  id          VARCHAR2(64 CHAR)   NOT NULL,
  seq_code    VARCHAR2(32 CHAR)   NOT NULL,
  prefix      VARCHAR2(16 CHAR)   NOT NULL,
  period_type VARCHAR2(16 CHAR)   DEFAULT 'YEAR' NOT NULL,
  period_key  VARCHAR2(16 CHAR)   DEFAULT '*' NOT NULL,
  current_no  NUMBER(10)          DEFAULT 0 NOT NULL,
  padding     NUMBER(5)           DEFAULT 6 NOT NULL,
  no_pattern  VARCHAR2(128 CHAR)  DEFAULT '${prefix}-${period}-${seq}' NOT NULL,
  enabled     NUMBER(1)           DEFAULT 1 NOT NULL,
  remark      VARCHAR2(500 CHAR),
  created_at  TIMESTAMP(6)        NOT NULL,
  updated_at  TIMESTAMP(6)        NOT NULL,
  CONSTRAINT pk_sys_no_sequence PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_no_seq ON sys_no_sequence (seq_code, period_key);

COMMENT ON TABLE sys_no_sequence IS '单据编号发号器，替代数据库序列（SQLite 无序列，Oracle 有，语义不同）';
COMMENT ON COLUMN sys_no_sequence.period_type IS 'YEAR 按年切段 / MONTH 按月 / NONE 不分段';
COMMENT ON COLUMN sys_no_sequence.period_key IS '2026 或 202611；NONE 时填 *，不写空串（Oracle 里空串即 NULL）';
COMMENT ON COLUMN sys_no_sequence.current_no IS '发号固定为：同一事务内先 UPDATE +1 再 SELECT，两库标准 SQL';


CREATE TABLE sys_hr_job_role_map (
  id          VARCHAR2(64 CHAR)  NOT NULL,
  hr_job_code VARCHAR2(64 CHAR)  NOT NULL,
  dept_code   VARCHAR2(64 CHAR),
  role_code   VARCHAR2(64 CHAR)  NOT NULL,
  auto_grant  NUMBER(1)          DEFAULT 0 NOT NULL,
  enabled     NUMBER(1)          DEFAULT 1 NOT NULL,
  remark      VARCHAR2(500 CHAR),
  created_at  TIMESTAMP(6)       NOT NULL,
  updated_at  TIMESTAMP(6)       NOT NULL,
  CONSTRAINT pk_sys_hr_job_role_map PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_hr_map ON sys_hr_job_role_map (hr_job_code, dept_code, role_code);

COMMENT ON TABLE sys_hr_job_role_map IS 'HR 岗位到系统角色的映射。HR 不能直接给业务权限';
COMMENT ON COLUMN sys_hr_job_role_map.auto_grant IS '0 = 仅建议需人工确认（默认）/ 1 = 同步时自动授予';


CREATE TABLE sys_ai_prompt (
  id          VARCHAR2(64 CHAR)   NOT NULL,
  prompt_code VARCHAR2(64 CHAR)   NOT NULL,
  prompt_name VARCHAR2(100 CHAR)  NOT NULL,
  content     CLOB                NOT NULL,
  version_no  NUMBER(5)           DEFAULT 1 NOT NULL,
  enabled     NUMBER(1)           DEFAULT 1 NOT NULL,
  remark      VARCHAR2(500 CHAR),
  created_at  TIMESTAMP(6)        NOT NULL,
  updated_at  TIMESTAMP(6)        NOT NULL,
  CONSTRAINT pk_sys_ai_prompt PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_prompt_code ON sys_ai_prompt (prompt_code, version_no);


CREATE TABLE req_synonym (
  id          VARCHAR2(64 CHAR)   NOT NULL,
  word        VARCHAR2(128 CHAR)  NOT NULL,
  std_word    VARCHAR2(128 CHAR)  NOT NULL,
  domain      VARCHAR2(32 CHAR)   DEFAULT '*' NOT NULL,
  enabled     NUMBER(1)           DEFAULT 1 NOT NULL,
  remark      VARCHAR2(500 CHAR),
  created_at  TIMESTAMP(6)        NOT NULL,
  updated_at  TIMESTAMP(6)        NOT NULL,
  CONSTRAINT pk_req_synonym PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_synonym_word ON req_synonym (word, domain);

COMMENT ON TABLE req_synonym IS '同义词表：看板/大屏/dashboard 归并，否则 CIM 场景相似度永远上不去';
COMMENT ON COLUMN req_synonym.std_word IS '归一后的标准词，参与分词比对';
COMMENT ON COLUMN req_synonym.domain IS '限定业务域/系统，* = 全局，避免跨系统误归并';


CREATE TABLE sys_oper_log (
  id           VARCHAR2(64 CHAR)    NOT NULL,
  user_id      VARCHAR2(64 CHAR),
  module       VARCHAR2(64 CHAR),
  action       VARCHAR2(64 CHAR),
  target_id    VARCHAR2(64 CHAR),
  request_uri  VARCHAR2(500 CHAR),
  http_method  VARCHAR2(16 CHAR),
  params       CLOB,
  ip           VARCHAR2(64 CHAR),
  cost_ms      NUMBER(10),
  result       VARCHAR2(16 CHAR),
  error_msg    VARCHAR2(1000 CHAR),
  created_at   TIMESTAMP(6)         NOT NULL,
  CONSTRAINT pk_sys_oper_log PRIMARY KEY (id)
);
CREATE INDEX idx_oper_user ON sys_oper_log (user_id, created_at);
CREATE INDEX idx_oper_target ON sys_oper_log (target_id);

COMMENT ON COLUMN sys_oper_log.params IS '入参 JSON，脱敏后存储';
COMMENT ON COLUMN sys_oper_log.result IS 'SUCCESS / FAIL';
