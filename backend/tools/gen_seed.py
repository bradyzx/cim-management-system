# -*- coding: utf-8 -*-
"""
种子数据生成器。

用途：
    生成两份内容完全相同的初始化脚本：
        db/seed-sqlite.sql
        db/seed-oracle.sql
    覆盖：部门树、8 个角色与 8 个演示账号、菜单树与按钮权限、字典、
          数据权限矩阵、SLA 配置、工作日历、单据发号器、通知模板与通道、
          部署配置、附件必填规则、HR 岗位映射、AI 提示词、同义词、脚本版本。

为什么用脚本生成而不是手写两份 SQL：
    种子数据里有大量外键式的 ID 引用（用户→部门、角色菜单、权限规则引用角色码），
    手写两份几乎必然出现「SQLite 改了 Oracle 没改」和「ID 对不上」。
    两份 SQL 只是字面量写法不同（时间戳），其余完全一致，适合用一份配置生成。

用法：
    python gen_seed.py

约定：
    1. 主键用 32 位定长数字串（00000000000000000000000000000001），
       既是合法字符串主键，又保证两次生成结果完全一致，便于人工核对与 diff。
    2. 演示账号密码统一 Cim#Init2026（bcrypt，cost=10），上线前必须全部改掉。
    3. 时间戳固定写死，不用 NOW()，保证脚本可重复执行、结果可 diff。
"""

import hashlib
from pathlib import Path

DB目录 = Path(__file__).resolve().parent.parent / "src" / "main" / "resources" / "db"

建库时间 = "2026-11-02 09:00:00"

# 5 个演示账号的 bcrypt 哈希，明文均为 Cim#Init2026（已逐个校验往返一致）
口令哈希 = [
    "$2b$10$JjcRR6.GkfekHtWkCs2S6.jGsPF.F5MF.aHPkoWiS9CotASbn60Ny",
    "$2b$10$CQc/vh423gNmYRthsxxpm.rKlmF.XGqWbJVUyurN3VhAECfk0cjP6",
    "$2b$10$W51KD2wBmmEEEz/zldmN.eOPnruTzEdrjhWhQDbcn..kGH1sfk4gm",
    "$2b$10$r26yMcDCpe6pArjRC38Wvu16Ywrbdauq7zwQCoifciC5JpQ5e6ujO",
    "$2b$10$qZ.wBdsXjSjf6aMdrqonCeM2Z8meaRTWAempTQX4V3YXRfDc3n4Ba",
]

# 表结构定义：列名顺序即 INSERT 的列顺序，必须与 DDL 完全一致
表定义 = {
    "sys_dept": ["id", "dept_code", "dept_name", "parent_id", "ancestors", "dept_level",
                 "leader_user_id", "dept_type", "sort", "status", "remark", "external_id",
                 "sync_source", "sync_at", "created_at", "updated_at"],
    "sys_user": ["id", "username", "password_hash", "real_name", "nickname", "email", "mobile",
                 "avatar", "auth_type", "external_id", "employment_type", "hr_employment_code",
                 "primary_dept_id", "status", "must_change_password", "remark", "sync_source",
                 "sync_at", "last_login_at", "created_at", "updated_at"],
    "sys_user_dept": ["id", "user_id", "dept_id", "is_primary", "post_type", "hr_job_code",
                      "start_date", "end_date", "status", "created_at", "updated_at"],
    "sys_role": ["id", "role_code", "role_name", "is_system", "sort", "status", "remark",
                 "created_at", "updated_at", "is_deleted"],
    "sys_user_role": ["id", "user_id", "role_id", "dept_id", "created_at"],
    "sys_menu": ["id", "parent_id", "menu_name", "menu_type", "path", "component", "redirect",
                 "auth_code", "icon", "sort", "visible", "keep_alive", "link_url", "status",
                 "created_at", "updated_at", "is_deleted"],
    "sys_role_menu": ["id", "role_id", "menu_id", "created_at"],
    "sys_dict_type": ["id", "dict_type", "dict_name", "remark", "status", "created_at",
                      "updated_at", "is_deleted"],
    "sys_dict_data": ["id", "dict_type", "dict_label", "dict_value", "css_class", "sort",
                      "status", "remark", "created_at", "updated_at", "is_deleted"],
    "sys_data_scope_rule": ["id", "role_code", "ticket_type", "scope_type", "extra_json",
                            "readable", "writable", "enabled", "remark", "created_at",
                            "updated_at"],
    "req_sla_config": ["id", "ticket_type", "priority", "status", "limit_hours", "warn_hours",
                       "overdue_action", "enabled", "remark", "created_at", "updated_at"],
    "sys_work_calendar": ["id", "cal_date", "year_no", "day_type", "day_name", "work_hours",
                          "start_time", "end_time", "remark", "created_at", "updated_at"],
    "sys_no_sequence": ["id", "seq_code", "prefix", "period_type", "period_key", "current_no",
                        "padding", "no_pattern", "enabled", "remark", "created_at",
                        "updated_at"],
    "sys_notify_channel": ["id", "channel_code", "enabled", "webhook_url", "secret", "msg_type",
                           "at_mobiles", "retry_max", "timeout_ms", "remark", "created_at",
                           "updated_at"],
    "sys_notify_template": ["id", "template_code", "channel_code", "title_tpl", "content_tpl",
                            "enabled", "remark", "created_at", "updated_at", "is_deleted"],
    "sys_deploy_config": ["id", "system_code", "env", "deploy_type", "target_ref",
                          "param_template", "enabled", "remark", "created_at", "updated_at"],
    "req_attach_rule": ["id", "ticket_type", "status", "attach_type", "min_count", "required",
                        "enabled", "remark", "created_at", "updated_at"],
    "req_ticket": ["id", "ticket_no", "ticket_type", "title", "description", "content_asis",
                   "content_tobe", "status", "priority", "reporter_id", "creator_id", "owner_id",
                   "dept_id", "assignee_id", "system_code", "module_code", "acceptance_criteria",
                   "acceptance_locked", "acceptance_locked_at", "acceptance_locked_by",
                   "roi_desc", "roi_value", "propose_date", "expect_due_date", "estimated_hours",
                   "actual_hours", "parent_id", "ticket_source", "dup_override", "override_reason",
                   "return_count", "suspended_from", "suspend_reason", "expect_resume_at",
                   "close_reason", "closed_at", "submitted_at", "assigned_at", "design_at",
                   "dev_at", "uat_at", "release_at", "sla_due_at", "sla_overdue_hours",
                   "branch_name_suggest", "superior_id", "superior_confirmed_at",
                   "superior_skipped", "review_passed_at", "eval_result", "satisfaction_score",
                   "eval_comment", "evaluated_at", "evaluated_by",
                   # v1.6 新增：项目归属 / 流程标记 / 上线后验证
                   "project_id", "system_id", "mit_skipped", "platform_skipped",
                   # v0.7 并行化新增：三方确认与设计评审的各方通过标志
                   "mit_passed", "platform_passed", "superior_passed",
                   "dsgn_it_passed", "dsgn_platform_passed", "dsgn_user_passed",
                   "doc_confirmed_at", "run_stable_days", "ai_degraded", "ai_clarify_round",
                   "monitor_note", "related_inc_id", "uat_waiver", "uat_waiver_reason",
                   "created_at", "updated_at", "updated_by"],
    "req_acceptance_criteria": ["id", "ticket_id", "seq", "content", "locked", "created_at",
                                "updated_at"],
    # v1.6 新增 5 表
    "req_project": ["id", "project_code", "project_name", "project_type", "status", "owner_id",
                    "dept_id", "start_date", "end_date", "remark", "sort_no", "created_at",
                    "updated_at", "deleted"],
    "req_system": ["id", "project_id", "system_code", "system_name", "system_type", "owner_id",
                   "status", "remark", "sort_no", "created_at", "updated_at", "deleted"],
    "req_doc": ["id", "ticket_id", "version", "content_md", "doc_json", "ai_model", "ai_degraded",
                "confirmed_at", "confirmed_by", "created_at", "updated_at"],
    "req_ai_clarify": ["id", "ticket_id", "round_no", "dimension", "question", "answer",
                       "answered_at", "created_at"],
    "req_check_item": ["id", "ticket_id", "system_id", "item_name", "item_desc", "expect_result",
                       "is_key", "sort_no", "source", "check_result", "check_remark",
                       "checked_by", "checked_at", "created_at", "updated_at"],
    "req_flow_log": ["id", "ticket_id", "from_status", "to_status", "action", "operator_id",
                     "operator_dept_id", "reason_code", "remark", "return_point", "return_seq",
                     "created_at"],
    "sys_hr_job_role_map": ["id", "hr_job_code", "dept_code", "role_code", "auto_grant",
                            "enabled", "remark", "created_at", "updated_at"],
    "sys_ai_prompt": ["id", "prompt_code", "prompt_name", "content", "version_no", "enabled",
                      "remark", "created_at", "updated_at"],
    "req_synonym": ["id", "word", "std_word", "domain", "enabled", "remark", "created_at",
                    "updated_at"],
    "sys_schema_version": ["id", "version", "script_name", "script_checksum", "db_dialect",
                           "applied_at", "applied_by", "cost_ms", "success", "error_msg",
                           "remark", "created_at"],
}

# 时间类列：Oracle 侧要包 TO_TIMESTAMP，SQLite 侧直接存字符串
时间列 = {"created_at", "updated_at", "applied_at", "start_date", "end_date", "sync_at",
          "last_login_at"}

# 各表的数据，键为表名，值为「每行一个 dict」的列表
数据 = {表名: [] for 表名 in 表定义}

# 主键自增，保证每次生成结果一致
序号 = [0]


def 新ID():
    """返回 32 位定长数字串主键。"""
    序号[0] += 1
    return "%032d" % 序号[0]


def 加行(表名, **字段):
    """按表定义补全缺省列后追加一行，缺的列自动填 NULL。"""
    行 = {}
    for 列 in 表定义[表名]:
        行[列] = 字段.get(列)
    数据[表名].append(行)
    return 行


# =====================================================================
# 一、部门树（多层，ancestors 存路径串）
# =====================================================================

部门 = {}


def 建部门(键, 编码, 名称, 上级键, 层级, 排序, 类型="BUSINESS"):
    上级 = 部门.get(上级键) if 上级键 else None
    if 上级 is None:
        祖先 = "/"
        上级ID = None
    else:
        祖先 = 上级["ancestors"] + 上级["id"] + "/"
        上级ID = 上级["id"]

    部门[键] = 加行(
        "sys_dept",
        id=新ID(),
        dept_code=编码,
        dept_name=名称,
        parent_id=上级ID,
        ancestors=祖先,
        dept_level=层级,
        dept_type=类型,
        sort=排序,
        status="ACTIVE",
        sync_source="LOCAL",
        created_at=建库时间,
        updated_at=建库时间,
    )
    return 部门[键]


# dept_type 是三方确认定位确认人的唯一依据（03 4.2.4）：
#   MIT       → MIT 确认人取该部门负责人
#   PLATFORM  → 平台确认人 / 平台评审人取该部门负责人
#   IT        → IT 评审、IT 点检的人在这条线上
#   BUSINESS  → 业务部门，本身不做确认，只作为提出人归属
建部门("root", "CIM-ROOT", "上海芯源创新中心", None, 1, 1, "BUSINESS")
建部门("it", "IT", "信息技术部", "root", 2, 1, "IT")
建部门("mit", "MIT", "MIT 开发组", "it", 3, 1, "MIT")
建部门("platform", "PLATFORM", "中试平台", "root", 2, 2, "PLATFORM")
建部门("mfg", "MFG", "制造部", "root", 2, 3, "BUSINESS")
建部门("qa", "QA", "质量部", "root", 2, 4, "BUSINESS")


# =====================================================================
# 二、角色（8 个业务角色：5 个原有 + 平台负责人 / 评审领导 / IT 运维）
# =====================================================================

角色 = {}


def 建角色(键, 编码, 名称, 排序, 备注):
    角色[键] = 加行(
        "sys_role",
        id=新ID(),
        role_code=编码,
        role_name=名称,
        is_system=1,
        sort=排序,
        status="ACTIVE",
        remark=备注,
        created_at=建库时间,
        updated_at=建库时间,
        is_deleted=0,
    )
    return 角色[键]


建角色("admin", "ADMIN", "系统管理员", 1, "组织/人员/字典/权限维护，数据范围全部")
建角色("leader", "MIT_LEADER", "MIT Leader", 2, "MIT 确认、接需求池、派单、驳回、上线审批")
建角色("dev", "MIT_DEV", "MIT 开发人员", 3, "接收/拒接、设计、开发、自测、修 BUG")
建角色("reporter", "REPORTER", "提出人", 4, "提需求、确认文档、用户评审、UAT、验收、评价")
建角色("platform", "PLATFORM_LEADER", "平台负责人", 5,
       "平台确认（需求阶段）+ 平台评审（设计阶段），与 MIT Leader 平级、互不替代")
建角色("reviewer", "REVIEW_LEADER", "评审领导", 6,
       "需求评审；比 MIT / 平台高一级，不自己评自己")
建角色("itops", "IT_OPS", "IT 运维", 7,
       "IT 评审（设计阶段）+ IT 点检（上线后）；可与开发兼任，但动作独立授权")
建角色("deptmgr", "DEPT_MANAGER", "部门负责人", 8, "看本部门需求，只读；但可以是上级确认人")


# =====================================================================
# 三、用户与账号（每个角色一个，密码统一 Cim#Init2026）
# =====================================================================

用户 = {}


def 建用户(键, 账号, 姓名, 部门键, 角色键, 邮箱, 手机, 哈希, 备注):
    部门行 = 部门[部门键]
    用户[键] = 加行(
        "sys_user",
        id=新ID(),
        username=账号,
        password_hash=哈希,
        real_name=姓名,
        nickname=姓名,
        email=邮箱,
        mobile=手机,
        auth_type="LOCAL",
        employment_type="EMPLOYEE",
        primary_dept_id=部门行["id"],
        status="ACTIVE",
        # 演示账号不置强制改密，否则每次演示都被拦在改密页；
        # 正式初始化时应由管理员把这一列批量置 1（08 第 2 节已写「上线前必须改掉」）
        must_change_password=0,
        remark=备注,
        sync_source="LOCAL",
        created_at=建库时间,
        updated_at=建库时间,
    )

    # 一人一部门，主部门唯一
    加行(
        "sys_user_dept",
        id=新ID(),
        user_id=用户[键]["id"],
        dept_id=部门行["id"],
        is_primary=1,
        post_type="PRIMARY",
        status="ACTIVE",
        created_at=建库时间,
        updated_at=建库时间,
    )

    # 角色按部门生效；ADMIN 是全局角色，dept_id 留 NULL
    角色部门 = None if 角色键 == "admin" else 部门行["id"]
    加行(
        "sys_user_role",
        id=新ID(),
        user_id=用户[键]["id"],
        role_id=角色[角色键]["id"],
        dept_id=角色部门,
        created_at=建库时间,
    )


建用户("admin", "admin", "系统管理员", "it", "admin",
       "admin@cim.local", "13800000001", 口令哈希[0], "初始化管理员账号")
建用户("zhangsan", "zhangsan", "张三", "mfg", "reporter",
       "zhangsan@cim.local", "13800000002", 口令哈希[1], "演示：提出人（制造部）")
建用户("lisi", "lisi", "李四", "mit", "dev",
       "lisi@cim.local", "13800000003", 口令哈希[2], "演示：MIT 开发人员")
建用户("wangwu", "wangwu", "王五", "mit", "leader",
       "wangwu@cim.local", "13800000004", 口令哈希[3], "演示：MIT Leader")
建用户("zhaoliu", "zhaoliu", "赵六", "qa", "deptmgr",
       "zhaoliu@cim.local", "13800000005", 口令哈希[4], "演示：质量部负责人（只读）")
# 8 个角色各一个演示账号。后三个的角色是 v0.5 新增（平台负责人 / 评审领导 / IT 运维），
# 没有这三个人，CONF_PLATFORM、REQ_REVIEW、DSGN_REVIEW_IT 三步在演示里根本没人能点。
# 口令哈希复用前三个：同一明文 + 不同 salt 的 bcrypt 花括号可以不同，复用不影响校验。
建用户("sunqi", "sunqi", "孙七", "platform", "platform",
       "sunqi@cim.local", "13800000006", 口令哈希[0], "演示：平台负责人（平台确认 / 平台评审）")
建用户("zhouba", "zhouba", "周八", "root", "reviewer",
       "zhouba@cim.local", "13800000007", 口令哈希[1], "演示：评审领导（需求评审，比 MIT / 平台高一级）")
建用户("wujiu", "wujiu", "吴九", "it", "itops",
       "wujiu@cim.local", "13800000008", 口令哈希[2], "演示：IT 运维（IT 评审 / IT 点检）")

# 部门负责人回填：三方确认与上级确认都靠这里取人
部门["mit"]["leader_user_id"] = 用户["wangwu"]["id"]        # MIT 确认人
部门["platform"]["leader_user_id"] = 用户["sunqi"]["id"]    # 平台确认人 / 平台评审人
部门["it"]["leader_user_id"] = 用户["wujiu"]["id"]          # IT 评审 / IT 点检线
部门["qa"]["leader_user_id"] = 用户["zhaoliu"]["id"]
部门["root"]["leader_user_id"] = 用户["admin"]["id"]
# 制造部不设负责人：验证「上级确认人沿 parent 向上找到 root 负责人」这条兜底路径，
# 也保留 08 第五节「已知限制 1」的场景


# =====================================================================
# 四、菜单树（目录 / 菜单 / 按钮三级）
# =====================================================================

菜单 = {}


def 建菜单(键, 名称, 类型, 上级键, 路径, 组件, 权限码, 图标, 排序, 可见=1):
    上级ID = 菜单[上级键]["id"] if 上级键 else None
    菜单[键] = 加行(
        "sys_menu",
        id=新ID(),
        parent_id=上级ID,
        menu_name=名称,
        menu_type=类型,
        path=路径,
        component=组件,
        auth_code=权限码,
        icon=图标,
        sort=排序,
        visible=可见,
        keep_alive=1,
        status="ACTIVE",
        created_at=建库时间,
        updated_at=建库时间,
        is_deleted=0,
    )
    return 菜单[键]


# --- 需求管理 ---
建菜单("req_dir", "需求管理", "DIR", None, "/req", None, None, "lucide:clipboard-list", 1)
建菜单("req_todo", "我的待办", "MENU", "req_dir", "/req/todo", "ticket/todo/index", None,
       "lucide:list-todo", 1)
建菜单("req_pool", "需求池", "MENU", "req_dir", "/req/pool", "ticket/pool/index", None,
       "lucide:inbox", 2)
建菜单("req_list", "需求列表", "MENU", "req_dir", "/req/list", "ticket/list/index", None,
       "lucide:table-2", 3)
建菜单("req_bug", "缺陷与故障", "MENU", "req_dir", "/req/bug", "ticket/bug/index", None,
       "lucide:bug", 4)
建菜单("req_detail", "需求单详情", "MENU", "req_dir", "/req/detail/:id",
       "ticket/detail/index", None, "lucide:file-text", 5, 可见=0)

# --- 统计报表 ---
建菜单("rpt_dir", "统计报表", "DIR", None, "/report", None, None, "lucide:bar-chart-3", 2)
建菜单("rpt_dash", "看板与统计", "MENU", "rpt_dir", "/report/dashboard",
       "report/dashboard/index", None, "lucide:layout-dashboard", 1)

# --- 系统管理 ---
建菜单("sys_dir", "系统管理", "DIR", None, "/system", None, None, "lucide:settings", 3)
建菜单("sys_user", "用户管理", "MENU", "sys_dir", "/system/user", "system/user/index", None,
       "lucide:users", 1)
建菜单("sys_role", "角色管理", "MENU", "sys_dir", "/system/role", "system/role/index", None,
       "lucide:shield", 2)
建菜单("sys_menu", "菜单管理", "MENU", "sys_dir", "/system/menu", "system/menu/index", None,
       "lucide:menu", 3)
建菜单("sys_dept", "部门管理", "MENU", "sys_dir", "/system/dept", "system/dept/index", None,
       "lucide:network", 4)
建菜单("sys_dict", "字典管理", "MENU", "sys_dir", "/system/dict", "system/dict/index", None,
       "lucide:book-a", 5)
建菜单("sys_scope", "数据权限", "MENU", "sys_dir", "/system/data-scope",
       "system/data-scope/index", None, "lucide:lock", 6)

# --- 基础数据（项目与系统两级主数据，v1.6 新增）---
建菜单("basic_dir", "基础数据", "DIR", None, "/basic", None, None, "lucide:database", 4)
建菜单("basic_project", "项目管理", "MENU", "basic_dir", "/basic/project",
       "basic/project/index", None, "lucide:folder-kanban", 1)
建菜单("basic_system", "系统管理", "MENU", "basic_dir", "/basic/system",
       "basic/system/index", None, "lucide:server", 2)

# --- 技术档案（静态查阅页，内容与 03-技术方案与约束.md 同步维护，不挂任何接口）---
建菜单("tech_arch", "技术架构", "MENU", None, "/tech-arch", "tech-arch/index", None,
       "lucide:layers", 5)

# --- 按钮权限 ---
建菜单("btn_create", "提单", "BUTTON", "req_list", None, None, "ticket:create", None, 1)
建菜单("btn_assign", "派单", "BUTTON", "req_pool", None, None, "ticket:assign", None, 1)
建菜单("btn_reject", "驳回/退回", "BUTTON", "req_pool", None, None, "ticket:reject", None, 2)
建菜单("btn_accept", "接收需求", "BUTTON", "req_todo", None, None, "ticket:accept", None, 1)
建菜单("btn_refuse", "拒接", "BUTTON", "req_todo", None, None, "ticket:refuse", None, 2)
建菜单("btn_design", "提交设计", "BUTTON", "req_todo", None, None, "ticket:design", None, 3)
建菜单("btn_design_ok", "确认设计", "BUTTON", "req_todo", None, None, "ticket:design-confirm",
       None, 4)
建菜单("btn_selftest", "提交自测", "BUTTON", "req_todo", None, None, "ticket:self-test", None, 5)
建菜单("btn_uat", "UAT 通过", "BUTTON", "req_todo", None, None, "ticket:uat-pass", None, 6)
# 新增三环节的按钮权限
建菜单("btn_leader_confirm", "上级确认", "BUTTON", "req_todo", None, None,
       "ticket:leader-confirm", None, 7)
建菜单("btn_req_review", "需求评审", "BUTTON", "req_todo", None, None,
       "ticket:req-review", None, 8)
建菜单("btn_evaluate", "提交评价", "BUTTON", "req_list", None, None, "ticket:evaluate", None, 5)
建菜单("btn_release", "上线审批", "BUTTON", "req_pool", None, None, "ticket:release-approve",
       None, 3)
建菜单("btn_close", "关闭单据", "BUTTON", "req_list", None, None, "ticket:close", None, 2)
建菜单("btn_suspend", "挂起/恢复", "BUTTON", "req_list", None, None, "ticket:suspend", None, 3)
建菜单("btn_export", "导出", "BUTTON", "req_list", None, None, "ticket:export", None, 4)
建菜单("btn_attach", "上传附件", "BUTTON", "req_detail", None, None, "attach:upload", None, 1)
建菜单("btn_attach_del", "删除附件", "BUTTON", "req_detail", None, None, "attach:delete", None, 2)
建菜单("btn_bug", "提 BUG/故障", "BUTTON", "req_bug", None, None, "ticket:bug-create", None, 1)
建菜单("btn_user", "用户维护", "BUTTON", "sys_user", None, None, "system:user:edit", None, 1)
建菜单("btn_role", "角色维护", "BUTTON", "sys_role", None, None, "system:role:edit", None, 1)
建菜单("btn_menumgr", "菜单维护", "BUTTON", "sys_menu", None, None, "system:menu:edit", None, 1)
建菜单("btn_dept", "部门维护", "BUTTON", "sys_dept", None, None, "system:dept:edit", None, 1)
建菜单("btn_dict", "字典维护", "BUTTON", "sys_dict", None, None, "system:dict:edit", None, 1)
建菜单("btn_scope", "权限规则维护", "BUTTON", "sys_scope", None, None, "system:scope:edit",
       None, 1)

# 新增 11 个（02 第九节，v0.5 的 8 个流程动作 + 3 个上线后动作）
建菜单("btn_clarify", "提交澄清答复", "BUTTON", "req_todo", None, None, "ticket:clarify", None, 9)
建菜单("btn_doc_confirm", "确认需求文档", "BUTTON", "req_todo", None, None,
       "ticket:doc-confirm", None, 10)
建菜单("btn_doc_regen", "退回重生成", "BUTTON", "req_todo", None, None,
       "ticket:doc-regenerate", None, 11)
建菜单("btn_conf_mit", "MIT 确认", "BUTTON", "req_todo", None, None, "ticket:conf-mit", None, 12)
建菜单("btn_conf_platform", "平台确认", "BUTTON", "req_todo", None, None,
       "ticket:conf-platform", None, 13)
建菜单("btn_dsgn_it", "IT 评审", "BUTTON", "req_todo", None, None, "ticket:dsgn-review-it",
       None, 14)
建菜单("btn_dsgn_platform", "平台评审", "BUTTON", "req_todo", None, None,
       "ticket:dsgn-review-platform", None, 15)
建菜单("btn_dsgn_user", "用户评审", "BUTTON", "req_todo", None, None,
       "ticket:dsgn-review-user", None, 16)
建菜单("btn_prod_accept", "上线验收确认", "BUTTON", "req_todo", None, None,
       "ticket:prod-accept", None, 17)
建菜单("btn_monitor", "提交监控结论", "BUTTON", "req_todo", None, None,
       "ticket:monitor-submit", None, 18)
建菜单("btn_check_item", "点检项维护", "BUTTON", "req_todo", None, None, "check-item:edit",
       None, 19)

# 新增 8 个（项目/系统主数据 CRUD 6 个 + 两个上线后回退动作）
建菜单("btn_project_add", "项目新增", "BUTTON", "basic_project", None, None, "project:add",
       None, 1)
建菜单("btn_project_edit", "项目编辑", "BUTTON", "basic_project", None, None, "project:edit",
       None, 2)
建菜单("btn_project_del", "项目删除", "BUTTON", "basic_project", None, None, "project:delete",
       None, 3)
建菜单("btn_system_add", "系统新增", "BUTTON", "basic_system", None, None, "system:add", None, 1)
建菜单("btn_system_edit", "系统编辑", "BUTTON", "basic_system", None, None, "system:edit",
       None, 2)
建菜单("btn_system_del", "系统删除", "BUTTON", "basic_system", None, None, "system:delete",
       None, 3)
建菜单("btn_prod_reject", "验收不通过", "BUTTON", "req_todo", None, None, "ticket:prod-reject",
       None, 20)
建菜单("btn_monitor_return", "监控异常退回", "BUTTON", "req_todo", None, None,
       "ticket:monitor-return", None, 21)
# UAT 退回（⑤）不能复用 ticket:reject：那个码只授给 Leader / ADMIN，
# 而 UAT 退回是提出人才能做的动作，复用会导致提出人看不见按钮。
建菜单("btn_uat_fail", "UAT 退回", "BUTTON", "req_todo", None, None, "ticket:uat-fail",
       None, 22)


# =====================================================================
# 五、角色与菜单授权
# =====================================================================

# 每个角色能看到的菜单键（不含按钮），按钮单独列，便于核对「能点哪些」
角色菜单 = {
    "admin": ["req_dir", "req_todo", "req_pool", "req_list", "req_bug", "req_detail",
              "rpt_dir", "rpt_dash",
              "sys_dir", "sys_user", "sys_role", "sys_menu", "sys_dept", "sys_dict",
              "sys_scope",
              "basic_dir", "basic_project", "basic_system",
              "tech_arch"],
    "leader": ["req_dir", "req_todo", "req_pool", "req_list", "req_bug", "req_detail",
               "rpt_dir", "rpt_dash",
               "tech_arch"],
    "dev": ["req_dir", "req_todo", "req_list", "req_bug", "req_detail",
            "tech_arch"],
    "reporter": ["req_dir", "req_todo", "req_list", "req_detail",
                 "tech_arch"],
    "deptmgr": ["req_dir", "req_list", "req_detail", "rpt_dir", "rpt_dash",
                "tech_arch"],
    # 三个新增角色：只需要「待我处理」和「单据详情」，不需要系统管理与报表全量
    "platform": ["req_dir", "req_todo", "req_list", "req_detail", "rpt_dir", "rpt_dash",
                 "tech_arch"],
    "reviewer": ["req_dir", "req_list", "req_detail", "rpt_dir", "rpt_dash",
                 "tech_arch"],
    "itops": ["req_dir", "req_todo", "req_list", "req_detail",
              "tech_arch"],
}

角色按钮 = {
    "admin": ["btn_create", "btn_assign", "btn_reject", "btn_accept", "btn_refuse", "btn_design",
              "btn_design_ok", "btn_selftest", "btn_uat", "btn_leader_confirm", "btn_req_review",
              "btn_evaluate", "btn_release", "btn_close",
              "btn_suspend", "btn_export", "btn_attach", "btn_attach_del", "btn_bug",
              "btn_user", "btn_role", "btn_menumgr", "btn_dept", "btn_dict", "btn_scope",
              "btn_clarify", "btn_doc_confirm", "btn_doc_regen", "btn_conf_mit",
              "btn_conf_platform", "btn_dsgn_it", "btn_dsgn_platform", "btn_dsgn_user",
              "btn_prod_accept", "btn_monitor", "btn_check_item",
              "btn_project_add", "btn_project_edit", "btn_project_del",
              "btn_system_add", "btn_system_edit", "btn_system_del",
              "btn_prod_reject", "btn_monitor_return", "btn_uat_fail"],
    "leader": ["btn_create", "btn_assign", "btn_reject", "btn_conf_mit", "btn_leader_confirm",
               "btn_req_review", "btn_release", "btn_close",
               "btn_suspend", "btn_export", "btn_attach"],
    "dev": ["btn_accept", "btn_refuse", "btn_design", "btn_selftest", "btn_monitor",
            "btn_monitor_return", "btn_attach", "btn_bug"],
    "reporter": ["btn_create", "btn_clarify", "btn_doc_confirm", "btn_doc_regen",
                 "btn_design_ok", "btn_dsgn_user", "btn_uat", "btn_uat_fail",
                 "btn_prod_accept", "btn_prod_reject", "btn_evaluate",
                 # btn_close 必须授给提出人：评审驳回后由他决定「关闭」还是「重提」（02 6.2 ⑧）
                 "btn_close", "btn_attach", "btn_bug"],
    # 部门负责人只读，但「上级确认」按任务归属判定，不受数据范围 writable=0 限制
    "deptmgr": ["btn_export", "btn_leader_confirm"],
    "platform": ["btn_conf_platform", "btn_dsgn_platform", "btn_export", "btn_attach"],
    "reviewer": ["btn_req_review", "btn_export", "btn_attach"],
    "itops": ["btn_dsgn_it", "btn_check_item", "btn_export", "btn_attach"],
}

for 角色键, 菜单键列表 in 角色菜单.items():
    for 菜单键 in 菜单键列表 + 角色按钮[角色键]:
        加行(
            "sys_role_menu",
            id=新ID(),
            role_id=角色[角色键]["id"],
            menu_id=菜单[菜单键]["id"],
            created_at=建库时间,
        )


# =====================================================================
# 六、字典（类型 + 数据）
# =====================================================================

字典类型 = [
    ("ticket_type", "单据类型", "需求 / 缺陷 / 故障"),
    ("ticket_status", "单据状态", "23 态状态机（v0.7 并行化），与 02 第四节一一对应；9 个旧码置 DISABLED 保留"),
    ("ticket_priority", "优先级", "P0 最高"),
    ("return_reason", "退回原因", "退回时必须选，不允许只写「不行」"),
    ("return_point", "退回点", "19 个退回点（v0.7 并行化合并后），同一退回点第 3 次升级，按它聚合计次"),
    ("attach_type", "附件类型", "100 个业务槽位：33 已用 + 67 预留，另有 OTHER 兜底"),
    ("cim_system", "所属系统（已作废）", "v1.6 起系统迁入 req_system 实体表，本字典仅用于翻译历史单据"),
    ("employment_type", "用工形式", "实习生的动作限制由 employment_type 判定"),
    ("env", "环境", "部署环境"),
    ("close_reason", "关闭原因", "单据关闭时必须填"),
    ("link_type", "外部关联类型", "分支 / MR / 流水线 / 部署"),
    ("exec_status", "用例执行状态", "UAT 逐条打勾用"),
    ("case_origin", "用例来源", "AI 生成的用例要能与人写的区分"),
    ("change_source", "变更来源", "统计被改次数时必须能排除 FLOW 与 SYSTEM"),
    ("post_type", "兼任性质", "本职 / 兼岗"),
    # v1.6 新增：项目与系统主数据的枚举
    ("project_type", "项目类型", "中试 / 量产 / 其他"),
    ("project_status", "项目状态", "已关闭项目不允许再提新需求"),
    ("system_type", "系统类型", "外购 / 自研 / 在运"),
    ("system_status", "系统状态", "建设中 / 已上线 / 已下线"),
]

for 编码, 名称, 备注 in 字典类型:
    加行(
        "sys_dict_type",
        id=新ID(),
        dict_type=编码,
        dict_name=名称,
        remark=备注,
        status="ACTIVE",
        created_at=建库时间,
        updated_at=建库时间,
        is_deleted=0,
    )

字典数据 = {
    "ticket_type": [
        ("REQ", "需求", "processing"), ("BUG", "缺陷", "error"), ("INC", "故障", "warning"),
    ],
    "ticket_status": [
        # 27 态启用，顺序与 02 第四节 4.1 完全一致（前端配色也按这个分段）
        ("DRAFT", "草稿", "default"),
        ("SUBMITTED", "待分析", "processing"),
        ("AI_ANALYZING", "AI 分析中", "processing"),
        ("AI_CLARIFYING", "待补充澄清", "warning"),
        ("REQ_DOC_DRAFTED", "需求文档待确认", "warning"),
        ("CONFIRMING", "三方确认中", "warning"),
        ("REQ_REVIEW", "需求评审", "warning"),
        ("PENDING_ASSIGN", "待分配", "warning"),
        ("ASSIGNED", "待接收", "warning"),
        ("DESIGNING", "设计中", "processing"),
        ("DSGN_REVIEWING", "设计评审中", "warning"),
        ("DEVELOPING", "开发中", "processing"),
        ("SELF_TEST", "开发自测", "processing"),
        ("DEPLOYING", "部署中", "processing"),
        ("UAT", "UAT 测试", "warning"),
        ("RELEASE_PENDING", "待上线", "warning"),
        ("RELEASED", "已上线·待验收", "success"),
        ("MONITORING", "运行监控", "processing"),
        ("IT_INSPECTION", "IT 点检", "warning"),
        ("EVALUATING", "待评价", "processing"),
        ("CLOSED", "已关闭", "default"),
        ("SUSPENDED", "挂起", "default"),
        ("SUSPENDED_REVIEW", "挂起·待评审", "error"),
        # 三个旧码不删：历史单据的状态要能翻译成中文，前端下拉按 status='ACTIVE' 过滤所以不显示
        ("ANALYZED", "分析完成（旧）", "success", "DISABLED"),
        ("LEADER_CONFIRM", "上级确认（旧）", "warning", "DISABLED"),
        ("DESIGN_REVIEW", "待确认设计（旧）", "warning", "DISABLED"),
        # v0.7 并行化退役的 6 个码：三方确认三态、设计评审三态合并为 CONFIRMING / DSGN_REVIEWING，
        # 同样只翻译历史数据，新代码不产生
        ("CONF_MIT", "MIT 确认（旧）", "warning", "DISABLED"),
        ("CONF_PLATFORM", "平台确认（旧）", "warning", "DISABLED"),
        ("CONF_SUPERIOR", "上级确认（旧）", "warning", "DISABLED"),
        ("DSGN_REVIEW_IT", "设计·IT 评审（旧）", "warning", "DISABLED"),
        ("DSGN_REVIEW_PLATFORM", "设计·平台评审（旧）", "warning", "DISABLED"),
        ("DSGN_REVIEW_USER", "设计·用户评审（旧）", "warning", "DISABLED"),
    ],
    "ticket_priority": [
        ("P0", "P0 紧急", "error"), ("P1", "P1 高", "warning"),
        ("P2", "P2 中", "processing"), ("P3", "P3 低", "default"),
    ],
    "return_reason": [
        ("UNCLEAR", "需求描述不清", None),
        ("MISUNDERSTAND", "理解有偏差", None),
        ("NOT_FIT", "方案不符合业务", None),
        ("MISSING", "功能缺失", None),
        ("PERF", "性能不达标", None),
        ("UI", "界面不符预期", None),
        ("ENV", "环境问题", None),
        ("OTHER", "其他", None),
    ],
    "return_point": [
        # 编号与 02 第六节 6.2 一一对应；④ 是设计评审退回的统计总称，不单独产生数据。
        # v0.7 并行化：⑦⑩⑪ 合并为 ⑱（三方确认驳回），⑫⑬⑭ 合并为 ⑲（设计评审退回），
        # 六个旧码置 DISABLED 只翻译历史记录
        ("RT_DOC_REGEN", "① 需求文档退回重生成", None),
        ("RT_ASSIGN_REJECT", "② 待分配退回 / 不予立项", None),
        ("RT_ASSIGN_REFUSE", "③ 待接收拒接", None),
        ("RT_DESIGN_TOTAL", "④ 设计评审退回（统计总称）", None),
        ("RT_UAT_FAIL", "⑤ UAT 退回", None),
        ("RT_RELEASE_REJECT", "⑥ 上线审批驳回", None),
        ("RT_CONF_SUPERIOR", "⑦ 上级确认驳回", None, "DISABLED"),
        ("RT_REQ_REVIEW", "⑧ 需求评审驳回", None),
        ("RT_EVAL_FAIL", "⑨ 上线评价不符合", None),
        ("RT_CONF_MIT", "⑩ MIT 确认驳回", None, "DISABLED"),
        ("RT_CONF_PLATFORM", "⑪ 平台确认驳回", None, "DISABLED"),
        ("RT_DSGN_IT", "⑫ 设计·IT 评审退回", None, "DISABLED"),
        ("RT_DSGN_PLATFORM", "⑬ 设计·平台评审退回", None, "DISABLED"),
        ("RT_DSGN_USER", "⑭ 设计·用户评审退回", None, "DISABLED"),
        ("RT_PROD_ACCEPT", "⑮ 上线验收不通过", None),
        ("RT_MONITOR", "⑯ 运行监控异常退回", None),
        ("RT_IT_CHECK", "⑰ IT 点检不通过", None),
        ("RT_CONFS", "⑱ 三方确认驳回", None),
        ("RT_DSGN_REVIEW", "⑲ 设计评审退回", None),
    ],
    # 一物一码：33 个已用 + 67 个预留 + OTHER 兜底。预留行 status=DISABLED，前端下拉不显示；
    # 启用方式是把 dict_value 改成语义码、改中文名、status 置 ACTIVE，行 id 不变（07 2.4）。
    "attach_type": (
        [
            ("DRAFT_DOC", "需求草案", None),
            ("REQ_FORM", "需求单", None),
            ("AI_PRECHECK", "预审结论", None),
            ("CLARIFY_LOG", "澄清记录", None),
            ("REQ_DOC", "需求文档", None),
            ("REQ_DOC_FINAL", "需求文档定版", None),
            ("CONF_MIT_OPINION", "MIT 确认意见", None),
            ("CONF_PLATFORM_OPINION", "平台确认意见", None),
            ("CONF_SUPERIOR_REPLY", "上级批复记录", None),
            ("REVIEW_DOC", "评审纪要", None),
            ("POOL_LOG", "需求池记录", None),
            ("ASSIGN_ORDER", "派工单（接收）", None),
            ("FDS", "功能设计文档 FDS", None),
            ("DSGN_IT_OPINION", "IT 评审意见", None),
            ("DSGN_PLATFORM_OPINION", "平台评审意见", None),
            ("DSGN_USER_CONFIRM", "设计确认记录", None),
            ("COMMIT_LOG", "代码提交记录", None),
            ("TEST_REPORT", "用例 + 自测报告", None),
            ("DEPLOY_LOG", "部署记录", None),
            ("UAT_REPORT", "UAT 测试/验收报告", None),
            ("RELEASE_DOC", "上线申请单", None),
            ("ROLLBACK_PLAN", "回滚方案", None),
            ("RELEASE_LOG", "上线记录", None),
            ("PROD_ACCEPT", "生产验收确认", None),
            ("MONITOR_LOG", "运行监控记录", None),
            ("IT_CHECK", "IT 点检报告", None),
            ("OBSERVE_PROOF", "观察期结束凭证", None),
            ("EVAL_FORM", "需求评价表", None),
            ("CLOSURE_REPORT", "结项验收报告", None),
            ("SUSPEND_DOC", "挂起说明单", None),
            ("SUSPEND_REVIEW_DOC", "挂起恢复评审单", None),
            ("SCREENSHOT", "截图 / 日志", None),
            ("INCIDENT_REVIEW", "故障复盘报告", None),
        ]
        + [("RESERVED_%02d" % 序号, "预留", None, "DISABLED") for 序号 in range(1, 68)]
        + [("OTHER", "其他", None)]
    ),
    "cim_system": [
        # v1.6 起系统迁入 req_system 实体表，本字典整体置 DISABLED，只用来翻译历史单据的
        # system_code 快照。不物理删除，与「旧状态码禁用不删」同一纪律。
        ("MES", "制造执行系统", None, "DISABLED"),
        ("EAP", "设备自动化", None, "DISABLED"),
        ("FDC", "故障侦测与分类", None, "DISABLED"),
        ("RMS", "配方管理", None, "DISABLED"),
        ("SPC", "统计过程控制", None, "DISABLED"),
        ("PMS", "预防性维护系统", None, "DISABLED"),
        ("QMS", "质量管理", None, "DISABLED"),
        ("AMS", "报警管理", None, "DISABLED"),
        ("YMS", "良率管理", None, "DISABLED"),
        ("RCM", "远程控制管理", None, "DISABLED"),
        ("APC", "先进过程控制", None, "DISABLED"),
        ("WMS", "仓储管理", None, "DISABLED"),
        ("RPT", "报表平台", None, "DISABLED"),
        ("MDM", "主数据管理", None, "DISABLED"),
        ("ESB", "企业服务总线", None, "DISABLED"),
        ("BI", "商业智能", None, "DISABLED"),
        ("PORTAL", "统一门户", None, "DISABLED"),
    ],
    "employment_type": [
        ("EMPLOYEE", "正式员工", None), ("INTERN", "实习生", "warning"),
        ("CONTRACTOR", "外包", None),
    ],
    "env": [("DEV", "开发", None), ("UAT", "测试", None), ("PROD", "生产", "error")],
    "close_reason": [
        ("DONE", "已完成", "success"), ("REJECT", "驳回", "default"),
        ("DUP", "重复需求", "default"), ("CANCEL", "取消", "default"),
    ],
    "link_type": [
        ("BRANCH", "分支", None), ("MR", "合并请求", None), ("COMMIT", "提交", None),
        ("CI_BUILD", "流水线", None), ("DEPLOY", "部署", None),
        ("DOC", "文档", None), ("OTHER", "其他", None),
    ],
    "exec_status": [
        ("NOT_RUN", "未执行", "default"), ("PASS", "通过", "success"),
        ("FAIL", "不通过", "error"), ("SKIP", "跳过", "default"),
    ],
    "case_origin": [("AI", "AI 生成", "processing"), ("HUMAN", "人工编写", None)],
    "change_source": [
        ("FORM", "表单修改", None), ("FLOW", "流程动作", None),
        ("SYSTEM", "系统计算", None), ("MIGRATE", "数据迁移", None),
    ],
    "post_type": [("PRIMARY", "本职", None), ("SECONDARY", "兼岗", "warning")],
    "project_type": [
        ("PILOT", "中试", None), ("MASS", "量产", None), ("OTHER", "其他", None),
    ],
    "project_status": [
        ("PLANNING", "筹备", "processing"), ("ACTIVE", "进行中", "success"),
        ("SUSPENDED", "暂停", "warning"), ("CLOSED", "已关闭", "default"),
    ],
    "system_type": [
        ("PURCHASED", "外购", None), ("INHOUSE", "自研", None), ("RUNNING", "在运", None),
    ],
    "system_status": [
        ("BUILDING", "建设中", "processing"), ("LIVE", "已上线", "success"),
        ("OFFLINE", "已下线", "default"),
    ],
}

# 元组允许 3 项（值、标签、样式）或 4 项（再加状态）。4 项的用途有两类：
#   DISABLED  —— 旧码 / 已作废 / 预留槽位，前端下拉按 ACTIVE 过滤，不显示但仍可翻译历史数据
for 类型, 列表 in 字典数据.items():
    for 序号值, 项 in enumerate(列表, start=1):
        值, 标签, 样式 = 项[0], 项[1], 项[2]
        状态 = 项[3] if len(项) > 3 else "ACTIVE"
        加行(
            "sys_dict_data",
            id=新ID(),
            dict_type=类型,
            dict_label=标签,
            dict_value=值,
            css_class=样式,
            sort=序号值,
            status=状态,
            created_at=建库时间,
            updated_at=建库时间,
            is_deleted=0,
        )


# =====================================================================
# 七、数据权限矩阵（03 第 5.2.1 节，改这张表不发版）
# =====================================================================

权限规则 = [
    ("REPORTER", "SELF", 1, 1,
     "自己提出的 + 自己作为需求 Owner 的（owner_id 参与 SELF 判定，见 03 4.2.3）"),
    ("MIT_DEV", "ASSIGNED", 1, 1, "派给我的 + 本组待认领"),
    ("MIT_LEADER", "GROUP", 1, 1,
     "我作为 Leader 的部门及下级 + 全部待分配单（只按提出部门过滤会导致跨部门提的需求派不出去）"),
    ("DEPT_MANAGER", "DEPT", 1, 0, "本部门提的单，只读，writable=0"),
    ("ADMIN", "ALL", 1, 1, "全部，无附加条件"),
    # 三个 v0.5 新增角色：确认/评审/点检的对象天生跨部门（平台确认看全公司需求），
    # 按部门过滤会让待办永远是空的，所以给 ALL；具体能点哪个动作仍由动作矩阵约束，
    # 数据范围只管「看得见」，不管「能不能点」（03 4.2.4 权限怎么判）。
    ("PLATFORM_LEADER", "ALL", 1, 1, "平台确认 / 平台评审跨部门，只读范围给 ALL；动作权限另判"),
    ("REVIEW_LEADER", "ALL", 1, 1, "需求评审要看全部待评审单；动作权限另判"),
    ("IT_OPS", "ALL", 1, 1, "IT 评审与 IT 点检跨系统；动作权限另判"),
]

for 角色码, 范围码, 可读, 可写, 备注 in 权限规则:
    加行(
        "sys_data_scope_rule",
        id=新ID(),
        role_code=角色码,
        ticket_type="*",
        scope_type=范围码,
        readable=可读,
        writable=可写,
        enabled=1,
        remark=备注,
        created_at=建库时间,
        updated_at=建库时间,
    )


# =====================================================================
# 八、SLA 配置（工时按工作日折算，1 个工作日 = 8 小时）
# =====================================================================

# REQ 25 条 = 02 第七节逐状态列的时限（DRAFT 草稿、CLOSED 终态不计；满 7 天是定时器不是 SLA）
# 口径：limit_hours 是「工作日折算后的总工时」，1 个工作日 = 8 小时；
# 实际到期时间必须按 sys_work_calendar 逐日累减，不能直接加小时（否则跨周末必错）。
SLA配置 = [
    ("REQ", "*", "SUBMITTED", 0.2, 0.1, "NOTIFY", "10 分钟未进分析队列，告警 ADMIN"),
    ("REQ", "*", "AI_ANALYZING", 0.1, 0.05, "NOTIFY", "5 分钟未完成视为 AI 失败，降级模板空壳"),
    ("REQ", "*", "AI_CLARIFYING", 16, 8, "NOTIFY", "2 个工作日未答复；满 3 轮自动带未澄清项出文档"),
    ("REQ", "*", "REQ_DOC_DRAFTED", 16, 8, "NOTIFY", "2 个工作日未确认，第 5 天自动关闭（可重开）"),
    # v0.7 并行化：三方确认合并计时，任一方未确认都算这 1 个工作日；跳过不计时
    ("REQ", "*", "CONFIRMING", 8, 4, "NOTIFY", "1 个工作日内三方中任一方未确认，第 2 天升级其主管；跳过不计时"),
    ("REQ", "*", "REQ_REVIEW", 16, 8, "NOTIFY", "2 个工作日未评审，超 3 天通知 ADMIN 协调"),
    ("REQ", "*", "PENDING_ASSIGN", 8, 4, "ESCALATE", "1 个工作日未派单，2 天升级主管"),
    ("REQ", "*", "ASSIGNED", 4, 2, "NOTIFY", "4 小时未接收，1 天自动回池"),
    ("REQ", "*", "DESIGNING", 24, 8, "NOTIFY", "3 个工作日未提交设计"),
    # v0.7 并行化：设计评审三步并行，取原三步中最宽的用户评审时限（2 个工作日）
    ("REQ", "*", "DSGN_REVIEWING", 16, 8, "NOTIFY", "2 个工作日内三方中任一方未评审，自动催办"),
    ("REQ", "*", "DEVELOPING", 0, 0, "ESCALATE", "按派单预估工时，超 20% 预警、50% 升级"),
    ("REQ", "*", "SELF_TEST", 8, 4, "NOTIFY", "1 个工作日未提交自测"),
    ("REQ", "*", "DEPLOYING", 1, 0.5, "NOTIFY", "构建耗时 + 10 分钟，二期启用"),
    ("REQ", "*", "UAT", 40, 8, "NOTIFY", "5 个工作日；到期前 1 天提醒，超期通知 Leader"),
    ("REQ", "*", "RELEASE_PENDING", 24, 8, "NOTIFY", "按上线窗口提醒 Leader"),
    ("REQ", "*", "RELEASED", 24, 8, "NOTIFY", "3 个工作日未验收提醒提出人，超 5 天升级其上级"),
    ("REQ", "*", "MONITORING", 16, 8, "NOTIFY", "2 个工作日未填监控结论，超 3 天同时提醒 MIT Leader"),
    ("REQ", "*", "IT_INSPECTION", 16, 8, "NOTIFY", "2 个工作日未点检；清单为空不卡流程，只标 check_items_empty"),
    ("REQ", "*", "EVALUATING", 24, 8, "NOTIFY", "3 个工作日未评价，超 5 天兜底关闭并标「未评价」，不补默认分"),
    ("REQ", "*", "SUSPENDED", 0, 0, "NOTIFY", "暂停计时，只统计滞留时长；超 5 天提醒双方主管"),
    ("REQ", "*", "SUSPENDED_REVIEW", 0, 0, "ESCALATE", "暂停计时，超 5 天提醒双方主管"),
    ("BUG", "*", "PENDING_ASSIGN", 4, 2, "NOTIFY", "缺陷 4 小时内派单"),
    ("BUG", "*", "DEVELOPING", 24, 8, "ESCALATE", "缺陷 24 小时修复"),
    ("BUG", "*", "UAT", 24, 8, "NOTIFY", "回归验证 24 小时"),
    ("INC", "*", "PENDING_ASSIGN", 1, 0.5, "ESCALATE", "故障 1 小时内响应"),
    ("INC", "*", "DEVELOPING", 8, 4, "ESCALATE", "故障 8 小时内恢复"),
]

for 类型, 优先级, 状态, 时限, 预警, 动作, 备注 in SLA配置:
    加行(
        "req_sla_config",
        id=新ID(),
        ticket_type=类型,
        priority=优先级,
        status=状态,
        limit_hours=时限,
        warn_hours=预警,
        overdue_action=动作,
        enabled=1,
        remark=备注,
        created_at=建库时间,
        updated_at=建库时间,
    )


# =====================================================================
# 九、工作日历（只登记例外，其余按自然周推断）
# =====================================================================

# 说明：表里只存法定节假日与调休（HOLIDAY / MAKEUP），
# 未登记的日期按「周一至周五为工作日、周六日为休息日」推断。
# 这样每年只需维护约 30 条例外，而不是 365 条全量。
# 2027 年放假安排以国务院通知为准，此处只放元旦（固定 1 月 1 日），
# 其余等通知发布后由管理员导入，不预先编造日期。
日历 = [
    ("2027-01-01", 2027, "HOLIDAY", "元旦", 0, "固定假日；2027 年放假安排发布后补齐春节及后续"),
]

for 日期, 年份, 类型, 名称, 工时, 备注 in 日历:
    加行(
        "sys_work_calendar",
        id=新ID(),
        cal_date=日期,
        year_no=年份,
        day_type=类型,
        day_name=名称,
        work_hours=工时,
        start_time="09:00",
        end_time="18:00",
        remark=备注,
        created_at=建库时间,
        updated_at=建库时间,
    )


# =====================================================================
# 十、单据编号发号器
# =====================================================================

发号器 = [
    # REQ 已发 6 张演示单，current_no 直接拨到 6，下一张从 000007 起
    ("TICKET_REQ", "REQ", "2026", 6),
    ("TICKET_BUG", "BUG", "2026", 0),
    ("TICKET_INC", "INC", "2026", 0),
]

for 编码, 前缀, 期间, 已发号 in 发号器:
    加行(
        "sys_no_sequence",
        id=新ID(),
        seq_code=编码,
        prefix=前缀,
        period_type="YEAR",
        period_key=期间,
        current_no=已发号,
        padding=6,
        no_pattern="${prefix}-${period}-${seq}",
        enabled=1,
        remark="跨年自动新建下一年的行",
        created_at=建库时间,
        updated_at=建库时间,
    )


# =====================================================================
# 十一、通知通道与模板
# =====================================================================

通道 = [
    ("SITE", 1, None, "站内信，一期默认开启"),
    ("DINGTALK", 0, None, "钉钉群机器人，填 webhook + 加签密钥后启用"),
    ("MAIL", 0, None, "一期不做，只留枚举位"),
]

for 编码, 开关, 地址, 备注 in 通道:
    加行(
        "sys_notify_channel",
        id=新ID(),
        channel_code=编码,
        enabled=开关,
        webhook_url=地址,
        msg_type="markdown",
        retry_max=2,
        timeout_ms=5000,
        remark=备注,
        created_at=建库时间,
        updated_at=建库时间,
    )

模板 = [
    ("TICKET_ASSIGNED", "收到新需求", "你有新需求待接收：${ticketNo} ${title}"),
    ("TICKET_RETURNED", "需求被退回", "需求 ${ticketNo} 被退回，原因：${reason}"),
    ("TICKET_UAT_READY", "需求可验收", "需求 ${ticketNo} 已部署测试环境，请验收"),
    ("TICKET_RELEASED", "需求已上线", "需求 ${ticketNo} 已上线，观察期 ${runStableDays} 天"),
    ("SLA_OVERDUE", "需求已超期", "需求 ${ticketNo} 在当前状态已超期 ${overdueHours} 小时"),
    # v0.5 新增 7 个，对应 02 第九节
    ("AI_CLARIFY", "AI 需要补充信息", "需求 ${ticketNo} 有 ${questionCount} 个问题待你回答"),
    ("DOC_CONFIRM_TODO", "需求文档待确认", "需求 ${ticketNo} 的需求文档已生成，请确认或退回重生成"),
    ("CONF_TODO", "待您确认", "需求 ${ticketNo} 已到「${status}」，请在 ${slaHours} 小时内处理"),
    ("DSGN_REVIEW_TODO", "设计评审待办", "需求 ${ticketNo} 的设计稿待您评审（${status}）"),
    ("PROD_ACCEPT_TODO", "上线待验收", "需求 ${ticketNo} 已上线，请在生产环境验收"),
    ("MONITOR_TODO", "运行监控待填", "需求 ${ticketNo} 的监控结论待填写（不少于 20 字）"),
    ("STABLE_DUE", "观察期满转评价", "需求 ${ticketNo} 观察期已满 ${runStableDays} 天，请评价"),
]

for 编码, 标题, 内容 in 模板:
    加行(
        "sys_notify_template",
        id=新ID(),
        template_code=编码,
        channel_code="SITE",
        title_tpl=标题,
        content_tpl=内容,
        enabled=1,
        remark="占位符：${ticketNo} ${title} ${status} ${reason} ${overdueHours}",
        created_at=建库时间,
        updated_at=建库时间,
        is_deleted=0,
    )


# =====================================================================
# 十二、部署配置（一期只留示例，enabled=0）
# =====================================================================

部署示例 = [
    ("MES", "UAT", "MANUAL", None, '{"branch":"${branch}","ticketNo":"${ticketNo}"}'),
    ("MES", "PROD", "MANUAL", None, '{"branch":"${branch}","ticketNo":"${ticketNo}"}'),
    ("EAP", "UAT", "MANUAL", None, '{"branch":"${branch}","ticketNo":"${ticketNo}"}'),
]

for 系统, 环境, 类型, 目标, 参数 in 部署示例:
    加行(
        "sys_deploy_config",
        id=新ID(),
        system_code=系统,
        env=环境,
        deploy_type=类型,
        target_ref=目标,
        param_template=参数,
        enabled=0,
        remark="一期手工部署 + 手工登记，二期接 Jenkins 后改 target_ref",
        created_at=建库时间,
        updated_at=建库时间,
    )


# =====================================================================
# 十三、附件必填规则（07 第二节）
# =====================================================================

# 键是「流转到达的状态」to_status，不是「现在停在哪」from_status。
# 例：FDS 要在提交设计（离开 DESIGNING）时校验，所以规则挂在 DSGN_REVIEWING 上；
# 挂在 DESIGNING 上等于「刚进设计就要有设计文档」，永远拦死。这条 09 第四节第 3 条定过，
# DDL v1.5 把语义写进了 req_attach_rule.status 的注释。
# 元组：单据类型 / 到达状态 / 附件类型 / 最少份数 / 是否必填 / 备注
附件规则 = [
    ("REQ", "CONFIRMING", "REQ_DOC", 1, 1, "N06 确认需求文档：没文档不许进三方确认"),
    ("REQ", "PENDING_ASSIGN", "REVIEW_DOC", 1, 1, "N10 需求评审：评审纪要是立项依据"),
    ("REQ", "DSGN_REVIEWING", "FDS", 1, 1, "N13 提交设计：没有 FDS 不许进设计评审"),
    ("REQ", "UAT", "TEST_REPORT", 1, 1, "N18 自测通过：没有用例与自测报告，「自测通过」没有依据"),
    ("REQ", "RELEASED", "RELEASE_DOC", 1, 1, "N21 上线审批：上线申请单与回滚方案缺一不批"),
    ("REQ", "RELEASED", "ROLLBACK_PLAN", 1, 1, "同上，两份都要传"),
    ("REQ", "IT_INSPECTION", "IT_CHECK", 1, 1, "N25 点检通过：要按重点功能清单出结论（同状态动作，to_status 取自身）"),
    ("REQ", "MONITORING", "MONITOR_LOG", 1, 0, "N24 可选：结论已结构化进 monitor_note，附件只做补充"),
    ("BUG", "*", "SCREENSHOT", 1, 1, "缺陷必须有截图或日志，否则查不动"),
    ("INC", "CLOSED", "INCIDENT_REVIEW", 1, 1, "故障关闭前必须有复盘报告"),
]

for 类型, 状态, 附件类型, 数量, 必填, 备注 in 附件规则:
    加行(
        "req_attach_rule",
        id=新ID(),
        ticket_type=类型,
        status=状态,
        attach_type=附件类型,
        min_count=数量,
        required=必填,
        enabled=1,
        remark=备注,
        created_at=建库时间,
        updated_at=建库时间,
    )


# =====================================================================
# 十三之二、项目与系统主数据（v1.6 新增，08 第 4.6 节）
# =====================================================================

项目 = {}
系统 = {}

项目["pilot"] = 加行(
    "req_project",
    id=新ID(),
    project_code="ZS-PILOT",
    project_name="中试平台建设",
    project_type="PILOT",
    status="ACTIVE",
    owner_id=用户["admin"]["id"],
    dept_id=部门["root"]["id"],
    start_date="2026-01-01",
    remark="主演示项目，六个系统全挂这里",
    sort_no=1,
    created_at=建库时间,
    updated_at=建库时间,
    deleted=0,
)
项目["mass"] = 加行(
    "req_project",
    id=新ID(),
    project_code="ZS-MASS",
    project_name="量产线扩产",
    project_type="MASS",
    status="PLANNING",
    owner_id=用户["wangwu"]["id"],
    dept_id=部门["root"]["id"],
    start_date="2027-01-01",
    remark="空项目：用于验证「项目下没有系统时提单页系统下拉为空、提交置灰」",
    sort_no=2,
    created_at=建库时间,
    updated_at=建库时间,
    deleted=0,
)

# 八个系统全挂 ZS-PILOT。system_code 是项目内唯一，不是全局唯一。
# 08 第 4.6 节列了 6 个，这里补 PMS / WMS —— 演示单 3 和 5 的需求就挂在这两个系统上，
# 不补的话 system_id 无从取值（NOT NULL），单据也失去真实归属。
系统清单 = [
    ("MES", "制造执行系统", "PURCHASED", "LIVE"),
    ("EAP", "设备自动化平台", "PURCHASED", "LIVE"),
    ("FDC", "故障侦测与分类", "PURCHASED", "BUILDING"),
    ("SPC", "统计过程控制", "INHOUSE", "LIVE"),
    ("QMS", "质量管理系统", "PURCHASED", "LIVE"),
    ("RMS", "配方管理系统", "PURCHASED", "LIVE"),
    ("PMS", "预防性维护系统", "PURCHASED", "LIVE"),
    ("WMS", "仓储管理系统", "PURCHASED", "BUILDING"),
]

for 序, (码, 名, 类型, 状态) in enumerate(系统清单, start=1):
    系统[码] = 加行(
        "req_system",
        id=新ID(),
        project_id=项目["pilot"]["id"],
        system_code=码,
        system_name=名,
        system_type=类型,
        owner_id=用户["admin"]["id"],
        status=状态,
        remark=None,
        sort_no=序,
        created_at=建库时间,
        updated_at=建库时间,
        deleted=0,
    )


# =====================================================================
# 十四、演示单据（6 张，覆盖 6 个关键状态，09 清单第六节）
# =====================================================================
# 全部由 zhangsan 提出，部门快照固定制造部，Owner 为本人；
# 上级确认人按 03 4.2.4 的判定顺序：制造部未设负责人 → 向上找到 root 负责人 = admin。
# 时间戳全部写死，保证两次生成结果可 diff。
# 6 张单覆盖：ANALYZED / PENDING_ASSIGN / ASSIGNED / DESIGN_REVIEW / UAT / SUSPENDED_REVIEW，
# 其中第 6 张演示「同一退回点（设计退回）第 3 次 → 升级挂起待评审」的场景。

提出人 = 用户["zhangsan"]["id"]
管理员 = 用户["admin"]["id"]
开发 = 用户["lisi"]["id"]
组长 = 用户["wangwu"]["id"]
平台负责人 = 用户["sunqi"]["id"]
评审领导 = 用户["zhouba"]["id"]
IT运维 = 用户["wujiu"]["id"]
制造部 = 部门["mfg"]["id"]


def 建演示单(单号, 标题, 状态, 系统码, **额外字段):
    """建一张演示单：提出人 / 录入人 / Owner 固定，缺省列由 加行 补 NULL。
    创建时间跟随 submitted_at，保证时间线自洽。
    v1.6 起 project_id / system_id 是 NOT NULL，必须在这里补，不能让 加行 填 NULL。"""
    提交时间 = 额外字段.get("submitted_at", "2026-10-20 09:30:00")
    # 缺省值允许被调用方覆盖：传了就用传的，没传用默认
    优先级 = 额外字段.pop("priority", "P2")
    退回次数 = 额外字段.pop("return_count", 0)
    # 以下列在 DDL 里 NOT NULL，种子生成器会给缺省列补 NULL，必须显式给值
    验收锁定 = 额外字段.pop("acceptance_locked", 0)
    return 加行(
        "req_ticket",
        id=新ID(),
        ticket_no=单号,
        ticket_type="REQ",
        title=标题,
        status=状态,
        priority=优先级,
        reporter_id=提出人,
        creator_id=提出人,
        owner_id=提出人,
        dept_id=制造部,
        project_id=项目["pilot"]["id"],
        system_id=系统[系统码]["id"],
        system_code=系统码,          # 冗余快照，不参与业务判断
        ticket_source="WEB",
        dup_override=0,
        acceptance_locked=验收锁定,
        return_count=退回次数,
        superior_id=管理员,
        superior_skipped=0,
        mit_skipped=额外字段.pop("mit_skipped", 0),
        platform_skipped=额外字段.pop("platform_skipped", 0),
        # v0.7 并行化：各方通过标志，演示单按所处环节传入
        mit_passed=额外字段.pop("mit_passed", 0),
        platform_passed=额外字段.pop("platform_passed", 0),
        superior_passed=额外字段.pop("superior_passed", 0),
        dsgn_it_passed=额外字段.pop("dsgn_it_passed", 0),
        dsgn_platform_passed=额外字段.pop("dsgn_platform_passed", 0),
        dsgn_user_passed=额外字段.pop("dsgn_user_passed", 0),
        run_stable_days=额外字段.pop("run_stable_days", 7),
        ai_degraded=额外字段.pop("ai_degraded", 0),
        ai_clarify_round=额外字段.pop("ai_clarify_round", 0),
        uat_waiver=额外字段.pop("uat_waiver", 0),
        sla_overdue_hours=0,
        created_at=提交时间,
        updated_at="2026-11-02 10:00:00",
        **额外字段,
    )


def 建流转(单, 起态, 止态, 动作, 操作人, 时间, **额外字段):
    """追加一条流转记录；系统动作（AI 分析、升级）操作人传 None。"""
    return 加行(
        "req_flow_log",
        id=新ID(),
        ticket_id=单["id"],
        from_status=起态,
        to_status=止态,
        action=动作,
        operator_id=操作人,
        created_at=时间,
        **额外字段,
    )


def 建验收条(单, 序号, 内容, 已锁定):
    """追加一条结构化验收标准；覆盖率分母按单内条数计（06 第三节）。"""
    return 加行(
        "req_acceptance_criteria",
        id=新ID(),
        ticket_id=单["id"],
        seq=序号,
        content=内容,
        locked=已锁定,
        created_at=单["created_at"],
        updated_at=单["updated_at"],
    )


# --- 单 1：AI 已出需求文档，等提出人确认（N05/N06），且刚走完一轮澄清 ---
单1 = 建演示单(
    "REQ-2026-000001", "MES 报工看板增加批次维度的良率曲线", "REQ_DOC_DRAFTED", "MES",
    description="报工看板目前只能看整体良率，希望支持按批次展开曲线。",
    content_asis="看板只有车间整体良率，批次出问题要导出 Excel 逐批比对。",
    content_tobe="看板支持选择批次，直接显示该批次的良率曲线与判异点。",
    module_code="RPT",
    roi_desc="每批次异常排查从 2 小时缩短到 10 分钟",
    propose_date="2026-10-20", expect_due_date="2026-11-20",
    submitted_at="2026-10-20 09:30:00",
    ai_clarify_round=1,
    sla_due_at="2026-10-22 09:30:00",
)
建流转(单1, "DRAFT", "SUBMITTED", "SUBMIT", 提出人, "2026-10-20 09:30:00")
建流转(单1, "SUBMITTED", "AI_ANALYZING", "AI_PICK", None, "2026-10-20 09:30:02")
建流转(单1, "AI_ANALYZING", "AI_CLARIFYING", "AI_ASK", None, "2026-10-20 09:30:04",
       remark="完整度 62 分，缺「怎么算成功」，发问 2 条")
建流转(单1, "AI_CLARIFYING", "AI_ANALYZING", "CLARIFY_ANSWER", 提出人, "2026-10-20 10:15:00",
       remark="已回答 2 条澄清问题")
建流转(单1, "AI_ANALYZING", "REQ_DOC_DRAFTED", "AI_DOC_READY", None, "2026-10-20 10:15:06",
       remark="规则版查重：最高相似度 0.31，判定 NEW；按 12 节模板出文档")
加行(
    "req_ai_clarify",
    id=新ID(),
    ticket_id=单1["id"],
    round_no=1,
    dimension="SUCCESS",
    question="这个看板做好之后，怎么算成功？有没有可量化的标准（比如排查耗时降到多少）？",
    answer="单批次异常排查时间从 2 小时降到 10 分钟以内，不用再导出 Excel。",
    answered_at="2026-10-20 10:15:00",
    created_at="2026-10-20 09:30:04",
)
加行(
    "req_ai_clarify",
    id=新ID(),
    ticket_id=单1["id"],
    round_no=1,
    dimension="USER",
    question="主要谁在用这个批次曲线？工艺工程师还是设备工程师？",
    answer="工艺工程师为主，设备工程师偶尔看。",
    answered_at="2026-10-20 10:15:00",
    created_at="2026-10-20 09:30:04",
)
加行(
    "req_doc",
    id=新ID(),
    ticket_id=单1["id"],
    version=1,
    content_md=(
        "# 需求文档：MES 报工看板增加批次维度良率曲线\n\n"
        "## 1 需求名称\nMES 报工看板批次良率曲线\n\n"
        "## 2 业务背景（As Is）\n看板只有车间整体良率，批次异常需导出 Excel 逐批比对。\n\n"
        "## 3 痛点与影响\n单批次排查约 2 小时，夜班发现异常往往延后一个班次。\n\n"
        "## 4 期望目标（To Be）\n看板可选择批次，直接显示该批次良率曲线与判异点。\n\n"
        "## 5 功能需求清单\n- F-01 看板增加批次选择器\n- F-02 曲线按批次渲染并标注判异点\n\n"
        "## 6 非功能需求\n曲线加载 ≤3 秒；支持单批次 5000 点。\n\n"
        "## 7 涉及联动范围\n系统：MES；部门：制造部。\n\n"
        "## 8 需求优先级\nP2（影响面中、紧急度中、不涉合规）。\n\n"
        "## 9 工作量与上线时间初估\n8~12 人天，建议 2026-11-20 前上线。（AI 估算，需 MIT 确认）\n\n"
        "## 10 验收标准\n1. 看板可选批次并显示该批次曲线\n2. 判异点按规则标注\n"
        "3. 单批次加载 ≤3 秒\n\n## 11 附件清单\n现状截图见附件。\n\n"
        "## 12 AI 预审结论\n查重最高 0.31（NEW）；完整度 78；风险：历史数据量待确认。\n"
    ),
    doc_json='{"sections":[{"no":1,"title":"需求名称"},{"no":5,"title":"功能需求清单","items":["F-01","F-02"]},{"no":10,"title":"验收标准","count":3}]}',
    ai_model="RULE_TEMPLATE_V1",
    ai_degraded=0,
    created_at="2026-10-20 10:15:06",
    updated_at="2026-10-20 10:15:06",
)

# --- 单 2：评审通过，进需求池待分配（完整走完三方确认 + 需求评审）---
单2 = 建演示单(
    "REQ-2026-000002", "FDC 告警通知增加钉钉群推送通道", "PENDING_ASSIGN", "FDC",
    description="FDC 规则告警只发站内信，夜班没人看，希望推送到设备群。",
    content_asis="告警只在系统内红点提示，夜班经常错过处置窗口。",
    content_tobe="P1 及以上告警同步推送钉钉设备群并 @值班工程师。",
    priority="P1", module_code="AMS",
    roi_desc="告警平均响应从 40 分钟降到 10 分钟内",
    propose_date="2026-10-21", expect_due_date="2026-11-10",
    submitted_at="2026-10-21 10:00:00",
    doc_confirmed_at="2026-10-21 11:00:00",
    superior_confirmed_at="2026-10-22 10:00:00",
    review_passed_at="2026-10-26 14:00:00",
    sla_due_at="2026-10-27 14:00:00",
)
建流转(单2, "DRAFT", "SUBMITTED", "SUBMIT", 提出人, "2026-10-21 10:00:00")
建流转(单2, "SUBMITTED", "AI_ANALYZING", "AI_PICK", None, "2026-10-21 10:00:02")
建流转(单2, "AI_ANALYZING", "REQ_DOC_DRAFTED", "AI_DOC_READY", None, "2026-10-21 10:00:04",
       remark="规则版查重：最高相似度 0.22，判定 NEW；信息完整，未发起澄清")
建流转(单2, "REQ_DOC_DRAFTED", "CONF_MIT", "DOC_CONFIRM", 提出人, "2026-10-21 11:00:00",
       remark="确认需求文档（REQ_DOC 已归档）")
建流转(单2, "CONF_MIT", "CONF_PLATFORM", "CONF_MIT_PASS", 组长, "2026-10-22 09:05:00",
       remark="MIT 确认通过：工作量 8~12 人天认可")
建流转(单2, "CONF_PLATFORM", "CONF_SUPERIOR", "CONF_PLATFORM_PASS", 平台负责人,
       "2026-10-22 09:10:00", remark="平台确认通过：与设备告警平台规划一致")
建流转(单2, "CONF_SUPERIOR", "REQ_REVIEW", "CONF_SUPERIOR_PASS", 管理员, "2026-10-22 10:00:00",
       remark="上级确认通过（制造部无负责人，按规则上溯到中心负责人）")
建流转(单2, "REQ_REVIEW", "PENDING_ASSIGN", "REVIEW_PASS", 评审领导, "2026-10-26 14:00:00",
       remark="评审通过，评审纪要已归档")

# --- 单 3：已派给 lisi，等接收 ---
单3 = 建演示单(
    "REQ-2026-000003", "PMS 保养工单导出增加按班次筛选", "ASSIGNED", "PMS",
    description="保养工单导出只能按日期，希望增加班次维度筛选。",
    content_asis="导出全量再手工按班次拆分。",
    content_tobe="导出弹窗增加班次多选，直接出分班次文件。",
    module_code="RPT",
    roi_desc="每班交接统计节省 15 分钟",
    propose_date="2026-10-22", expect_due_date="2026-11-15",
    submitted_at="2026-10-22 11:00:00",
    doc_confirmed_at="2026-10-22 14:00:00",
    superior_confirmed_at="2026-10-23 09:00:00",
    review_passed_at="2026-10-26 15:00:00",
    assignee_id=开发, assigned_at="2026-10-27 10:00:00",
    sla_due_at="2026-10-27 14:00:00",
)
建流转(单3, "DRAFT", "SUBMITTED", "SUBMIT", 提出人, "2026-10-22 11:00:00")
建流转(单3, "SUBMITTED", "AI_ANALYZING", "AI_PICK", None, "2026-10-22 11:00:02")
建流转(单3, "AI_ANALYZING", "REQ_DOC_DRAFTED", "AI_DOC_READY", None, "2026-10-22 11:00:04",
       remark="规则版查重：最高相似度 0.18，判定 NEW")
建流转(单3, "REQ_DOC_DRAFTED", "CONF_MIT", "DOC_CONFIRM", 提出人, "2026-10-22 14:00:00")
建流转(单3, "CONF_MIT", "CONF_PLATFORM", "CONF_MIT_PASS", 组长, "2026-10-22 16:30:00")
建流转(单3, "CONF_PLATFORM", "CONF_SUPERIOR", "CONF_PLATFORM_PASS", 平台负责人,
       "2026-10-22 17:00:00")
建流转(单3, "CONF_SUPERIOR", "REQ_REVIEW", "CONF_SUPERIOR_PASS", 管理员, "2026-10-23 09:00:00")
建流转(单3, "REQ_REVIEW", "PENDING_ASSIGN", "REVIEW_PASS", 评审领导, "2026-10-26 15:00:00",
       remark="评审通过，评审纪要已归档")
建流转(单3, "PENDING_ASSIGN", "ASSIGNED", "ASSIGN", 组长, "2026-10-27 10:00:00",
       remark="指派给李四，预估 8 小时")

# --- 单 4：设计评审 IT / 平台已过（并行置位），等用户评审 ---
单4 = 建演示单(
    "REQ-2026-000004", "QMS 来料检验单增加供应商批次关联", "DSGN_REVIEWING", "QMS",
    dsgn_it_passed=1, dsgn_platform_passed=1,
    description="来料检验单希望直接关联供应商批次号，替代手工备注。",
    content_asis="检验单的供应商批次填在备注栏，格式五花八门没法统计。",
    content_tobe="检验单增加供应商批次字段，从来料批次表选择，必填校验。",
    module_code="IQC",
    roi_desc="来料追溯查询从人工翻单变为秒级",
    propose_date="2026-10-24", expect_due_date="2026-11-25",
    submitted_at="2026-10-24 14:00:00",
    doc_confirmed_at="2026-10-24 17:00:00",
    superior_confirmed_at="2026-10-26 09:00:00",
    review_passed_at="2026-10-27 10:00:00",
    assignee_id=开发, assigned_at="2026-10-27 14:00:00",
    design_at="2026-10-28 17:00:00",
    acceptance_criteria="1. 检验单供应商批次从来料批次表选择\n2. 批次字段必填，未选不允许提交",
    sla_due_at="2026-10-30 17:00:00",
)
建流转(单4, "DRAFT", "SUBMITTED", "SUBMIT", 提出人, "2026-10-24 14:00:00")
建流转(单4, "SUBMITTED", "AI_ANALYZING", "AI_PICK", None, "2026-10-24 14:00:02")
建流转(单4, "AI_ANALYZING", "REQ_DOC_DRAFTED", "AI_DOC_READY", None, "2026-10-24 14:00:04",
       remark="规则版查重：最高相似度 0.27，判定 NEW")
建流转(单4, "REQ_DOC_DRAFTED", "CONF_MIT", "DOC_CONFIRM", 提出人, "2026-10-24 17:00:00")
建流转(单4, "CONF_MIT", "CONF_PLATFORM", "CONF_MIT_PASS", 组长, "2026-10-25 09:00:00")
建流转(单4, "CONF_PLATFORM", "CONF_SUPERIOR", "CONF_PLATFORM_PASS", 平台负责人,
       "2026-10-25 10:00:00")
建流转(单4, "CONF_SUPERIOR", "REQ_REVIEW", "CONF_SUPERIOR_PASS", 管理员, "2026-10-26 09:00:00")
建流转(单4, "REQ_REVIEW", "PENDING_ASSIGN", "REVIEW_PASS", 评审领导, "2026-10-27 10:00:00",
       remark="评审通过，评审纪要已归档")
建流转(单4, "PENDING_ASSIGN", "ASSIGNED", "ASSIGN", 组长, "2026-10-27 14:00:00",
       remark="指派给李四")
建流转(单4, "ASSIGNED", "DESIGNING", "ACCEPT", 开发, "2026-10-27 15:00:00")
建流转(单4, "DESIGNING", "DSGN_REVIEW_IT", "SUBMIT_DESIGN", 开发, "2026-10-28 17:00:00",
       remark="FDS 已上传")
建流转(单4, "DSGN_REVIEW_IT", "DSGN_REVIEW_PLATFORM", "DSGN_IT_PASS", IT运维,
       "2026-10-29 10:00:00", remark="架构与集成可行，无新增接口")
建流转(单4, "DSGN_REVIEW_PLATFORM", "DSGN_REVIEW_USER", "DSGN_PLATFORM_PASS", 平台负责人,
       "2026-10-29 14:00:00", remark="业务匹配，数据口径与来料批次表一致")
建验收条(单4, 1, "检验单供应商批次从来料批次表下拉选择，不允许自由输入", 0)
建验收条(单4, 2, "批次字段必填，未选不允许提交检验单", 0)

# --- 单 5：UAT 测试中，验收标准已锁定（一期自测通过直跳 UAT，不经过 DEPLOYING）---
单5 = 建演示单(
    "REQ-2026-000005", "WMS 备件出库增加工单关联校验", "UAT", "WMS",
    description="备件出库时强制关联保养工单或故障单，防止备件流失。",
    content_asis="出库单的关联工单是选填，月末盘点经常对不上。",
    content_tobe="出库必须关联有效工单号，无工单需主管审批放行。",
    priority="P1", module_code="SPARE",
    roi_desc="备件月度盘点差异从 3% 降到 0.5% 以内",
    propose_date="2026-10-12", expect_due_date="2026-11-05",
    submitted_at="2026-10-12 09:00:00",
    doc_confirmed_at="2026-10-12 16:00:00",
    superior_confirmed_at="2026-10-13 09:30:00",
    review_passed_at="2026-10-14 10:00:00",
    assignee_id=开发, assigned_at="2026-10-14 10:30:00",
    design_at="2026-10-19 18:00:00", dev_at="2026-10-20 10:00:00",
    acceptance_locked=1, acceptance_locked_at="2026-10-20 10:00:00", acceptance_locked_by=提出人,
    acceptance_criteria="1. 报工完成后 5 秒内批次数据出现在看板\n"
                        "2. 批次取消时看板同步移除该批次\n"
                        "3. 无跨部门权限用户仅能看到本部门批次",
    uat_at="2026-10-27 11:00:00",
    sla_due_at="2026-11-02 19:00:00",
)
建流转(单5, "DRAFT", "SUBMITTED", "SUBMIT", 提出人, "2026-10-12 09:00:00")
建流转(单5, "SUBMITTED", "AI_ANALYZING", "AI_PICK", None, "2026-10-12 09:00:02")
建流转(单5, "AI_ANALYZING", "REQ_DOC_DRAFTED", "AI_DOC_READY", None, "2026-10-12 09:00:03",
       remark="规则版查重：最高相似度 0.35，判定 NEW")
建流转(单5, "REQ_DOC_DRAFTED", "CONF_MIT", "DOC_CONFIRM", 提出人, "2026-10-12 16:00:00")
建流转(单5, "CONF_MIT", "CONF_PLATFORM", "CONF_MIT_PASS", 组长, "2026-10-12 17:00:00")
建流转(单5, "CONF_PLATFORM", "CONF_SUPERIOR", "CONF_PLATFORM_PASS", 平台负责人,
       "2026-10-12 17:30:00")
建流转(单5, "CONF_SUPERIOR", "REQ_REVIEW", "CONF_SUPERIOR_PASS", 管理员, "2026-10-13 09:30:00")
建流转(单5, "REQ_REVIEW", "PENDING_ASSIGN", "REVIEW_PASS", 评审领导, "2026-10-14 10:00:00",
       remark="评审通过，评审纪要已归档")
建流转(单5, "PENDING_ASSIGN", "ASSIGNED", "ASSIGN", 组长, "2026-10-14 10:30:00",
       remark="指派给李四，预估 24 小时")
建流转(单5, "ASSIGNED", "DESIGNING", "ACCEPT", 开发, "2026-10-15 09:00:00")
建流转(单5, "DESIGNING", "DSGN_REVIEW_IT", "SUBMIT_DESIGN", 开发, "2026-10-19 18:00:00")
建流转(单5, "DSGN_REVIEW_IT", "DSGN_REVIEW_PLATFORM", "DSGN_IT_PASS", IT运维,
       "2026-10-19 18:30:00")
建流转(单5, "DSGN_REVIEW_PLATFORM", "DSGN_REVIEW_USER", "DSGN_PLATFORM_PASS", 平台负责人,
       "2026-10-19 19:00:00")
建流转(单5, "DSGN_REVIEW_USER", "DEVELOPING", "DSGN_USER_PASS", 提出人, "2026-10-20 10:00:00",
       remark="用户评审通过，验收标准同步锁定")
建流转(单5, "DEVELOPING", "SELF_TEST", "SUBMIT_SELF_TEST", 开发, "2026-10-26 17:00:00",
       remark="用例与自测报告已上传")
建流转(单5, "SELF_TEST", "UAT", "SELF_TEST_PASS", 开发, "2026-10-27 11:00:00",
       remark="一期手工部署到 UAT 环境，等待张三验收")
建验收条(单5, 1, "报工完成后 5 秒内批次数据出现在看板曲线", 1)
建验收条(单5, 2, "批次取消时看板同步移除该批次且不留残影", 1)
建验收条(单5, 3, "无跨部门权限用户仅能看到本部门批次", 1)

# --- 单 6：同一退回点（⑭ 设计·用户评审）第 3 次，升级挂起待评审 ---
单6 = 建演示单(
    "REQ-2026-000006", "RMS 配方参数修改增加双人复核", "SUSPENDED_REVIEW", "RMS",
    description="配方关键参数修改希望增加第二人复核确认。",
    content_asis="参数修改单人即可生效，有误改风险。",
    content_tobe="关键参数修改需第二人复核后才允许下发设备。",
    priority="P1", module_code="RECIPE",
    roi_desc="消除单人流错的误下发风险",
    propose_date="2026-10-15", expect_due_date="2026-11-15",
    submitted_at="2026-10-15 14:00:00",
    doc_confirmed_at="2026-10-15 17:00:00",
    superior_confirmed_at="2026-10-16 09:00:00",
    review_passed_at="2026-10-19 10:00:00",
    assignee_id=开发, assigned_at="2026-10-19 14:00:00",
    design_at="2026-10-23 17:00:00",
    return_count=3, suspended_from="DSGN_REVIEWING",
    suspend_reason="设计稿两次修改仍不符合业务预期，⑭ 退回点第 3 次，按规则升级挂起待评审",
    sla_due_at="2026-11-07 10:00:00",
)
建流转(单6, "DRAFT", "SUBMITTED", "SUBMIT", 提出人, "2026-10-15 14:00:00")
建流转(单6, "SUBMITTED", "AI_ANALYZING", "AI_PICK", None, "2026-10-15 14:00:02")
建流转(单6, "AI_ANALYZING", "REQ_DOC_DRAFTED", "AI_DOC_READY", None, "2026-10-15 14:00:04",
       remark="规则版查重：最高相似度 0.29，判定 NEW")
建流转(单6, "REQ_DOC_DRAFTED", "CONF_MIT", "DOC_CONFIRM", 提出人, "2026-10-15 17:00:00")
建流转(单6, "CONF_MIT", "CONF_PLATFORM", "CONF_MIT_PASS", 组长, "2026-10-16 08:30:00")
建流转(单6, "CONF_PLATFORM", "CONF_SUPERIOR", "CONF_PLATFORM_PASS", 平台负责人,
       "2026-10-16 08:45:00")
建流转(单6, "CONF_SUPERIOR", "REQ_REVIEW", "CONF_SUPERIOR_PASS", 管理员, "2026-10-16 09:00:00")
建流转(单6, "REQ_REVIEW", "PENDING_ASSIGN", "REVIEW_PASS", 评审领导, "2026-10-19 10:00:00",
       remark="评审通过，评审纪要已归档")
建流转(单6, "PENDING_ASSIGN", "ASSIGNED", "ASSIGN", 组长, "2026-10-19 14:00:00",
       remark="指派给李四")
建流转(单6, "ASSIGNED", "DESIGNING", "ACCEPT", 开发, "2026-10-20 09:00:00")
建流转(单6, "DESIGNING", "DSGN_REVIEW_IT", "SUBMIT_DESIGN", 开发, "2026-10-23 17:00:00")
建流转(单6, "DSGN_REVIEW_IT", "DSGN_REVIEW_PLATFORM", "DSGN_IT_PASS", IT运维,
       "2026-10-24 10:00:00")
建流转(单6, "DSGN_REVIEW_PLATFORM", "DSGN_REVIEW_USER", "DSGN_PLATFORM_PASS", 平台负责人,
       "2026-10-24 14:00:00")
建流转(单6, "DSGN_REVIEW_USER", "DESIGNING", "DSGN_USER_RETURN", 提出人, "2026-10-26 10:00:00",
       reason_code="UI", remark="复核交互与预期不符，退回重做",
       return_point="RT_DSGN_USER", return_seq=1)
建流转(单6, "DESIGNING", "DSGN_REVIEW_IT", "SUBMIT_DESIGN", 开发, "2026-10-28 17:00:00")
建流转(单6, "DSGN_REVIEW_IT", "DSGN_REVIEW_PLATFORM", "DSGN_IT_PASS", IT运维,
       "2026-10-29 10:00:00")
建流转(单6, "DSGN_REVIEW_PLATFORM", "DSGN_REVIEW_USER", "DSGN_PLATFORM_PASS", 平台负责人,
       "2026-10-29 14:00:00")
建流转(单6, "DSGN_REVIEW_USER", "DESIGNING", "DSGN_USER_RETURN", 提出人, "2026-10-30 10:00:00",
       reason_code="NOT_FIT", remark="复核流程没有覆盖参数下发环节，退回重做",
       return_point="RT_DSGN_USER", return_seq=2)
建流转(单6, "DESIGNING", "DSGN_REVIEW_IT", "SUBMIT_DESIGN", 开发, "2026-11-02 09:00:00")
建流转(单6, "DSGN_REVIEW_IT", "DSGN_REVIEW_PLATFORM", "DSGN_IT_PASS", IT运维,
       "2026-11-02 09:20:00")
建流转(单6, "DSGN_REVIEW_PLATFORM", "DSGN_REVIEW_USER", "DSGN_PLATFORM_PASS", 平台负责人,
       "2026-11-02 09:40:00")
建流转(单6, "DSGN_REVIEW_USER", "SUSPENDED_REVIEW", "ESCALATE", None, "2026-11-02 10:00:00",
       reason_code="NOT_FIT", remark="⑭ 第 3 次退回，触发升级规则，双方主管评审",
       return_point="RT_DSGN_USER", return_seq=3)


# =====================================================================
# 十五、HR 岗位映射（默认不自动授权，先「建议 + 人工确认」跑一个月）
# =====================================================================

岗位映射 = [
    ("USER", None, "REPORTER", "普通用户岗 → 提出人"),
    ("DEV_MIT", None, "MIT_DEV", "MIT 开发岗 → 开发人员"),
    ("LEADER_MIT", None, "MIT_LEADER", "MIT 主管岗 → Leader"),
    ("MGR_DEPT", None, "DEPT_MANAGER", "部门经理岗 → 部门负责人（只读）"),
]

for 岗位, 部门码, 角色码, 备注 in 岗位映射:
    加行(
        "sys_hr_job_role_map",
        id=新ID(),
        hr_job_code=岗位,
        dept_code=部门码,
        role_code=角色码,
        auto_grant=0,
        enabled=1,
        remark=备注 + "；auto_grant=0 表示只建议，需管理员确认",
        created_at=建库时间,
        updated_at=建库时间,
    )


# =====================================================================
# 十六、AI 提示词（外置，改文案不发版）
# =====================================================================

提示词 = [
    ("ANALYSIS_SUGGEST", "需求分析建议",
     "你是 CIM 制造系统的需求分析助手。根据需求标题、现状、期望和相似需求清单，"
     "给出三问结论：是否已存在、是否有必要、影响哪些系统；再给出实现建议与 ROI 估算。"
     "只输出 JSON，不要解释文字。"),
    ("CASE_GENERATE", "测试用例生成",
     "根据锁定后的验收标准，逐条生成测试用例，每条用例必须标注对应的验收标准序号。"
     "优先覆盖硬性指标、边界条件、外部交互、权限安全。单需求不超过 15 条。"
     "重点关注项必须写明原因。只输出 JSON。"),
]

for 编码, 名称, 内容 in 提示词:
    加行(
        "sys_ai_prompt",
        id=新ID(),
        prompt_code=编码,
        prompt_name=名称,
        content=内容,
        version_no=1,
        enabled=1,
        remark="改提示词请升 version_no，否则同一单隔周重跑结论不同无法解释",
        created_at=建库时间,
        updated_at=建库时间,
    )


# =====================================================================
# 十七、同义词（CIM 场景必须归并，否则相似度上不去）
# =====================================================================

同义词 = [
    ("看板", "dashboard"), ("大屏", "dashboard"), ("驾驶舱", "dashboard"),
    ("报工", "上工"), ("工单", "单据"), ("需求单", "需求"),
    ("制造执行系统", "MES"), ("设备自动化", "EAP"),
]

for 词, 标准词 in 同义词:
    加行(
        "req_synonym",
        id=新ID(),
        word=词,
        std_word=标准词,
        domain="*",
        enabled=1,
        remark="AI 查重前先归一，避免同义词被算成不同需求",
        created_at=建库时间,
        updated_at=建库时间,
    )


# =====================================================================
# 十八、脚本版本记录（checksum 取当前 schema-sqlite.sql 的实际值）
# =====================================================================

SQLite脚本 = DB目录 / "schema-sqlite.sql"
脚本校验和 = hashlib.sha256(SQLite脚本.read_bytes()).hexdigest()[:16]

加行(
    "sys_schema_version",
    id=新ID(),
    version="1.6.0",
    script_name="schema-sqlite.sql",
    script_checksum=脚本校验和,
    db_dialect="SQLITE",
    applied_at=建库时间,
    applied_by="gen_seed.py",
    cost_ms=0,
    success=1,
    remark="种子数据生成时记录，应用启动自检会比对 checksum",
    created_at=建库时间,
)


# =====================================================================
# 十九、输出 SQL
# =====================================================================

# 表的输出顺序：被引用的表在前，方便人工按顺序阅读
输出顺序 = [
    "sys_dept", "sys_role", "sys_user", "sys_user_dept", "sys_user_role",
    "sys_menu", "sys_role_menu",
    "sys_dict_type", "sys_dict_data",
    "sys_data_scope_rule", "req_sla_config", "sys_work_calendar", "sys_no_sequence",
    "sys_notify_channel", "sys_notify_template", "sys_deploy_config",
    "req_attach_rule", "sys_hr_job_role_map", "sys_ai_prompt", "req_synonym",
    "req_project", "req_system",
    "req_ticket", "req_acceptance_criteria", "req_flow_log",
    "req_doc", "req_ai_clarify", "req_check_item",
    "sys_schema_version",
]

表中文名 = {
    "sys_dept": "部门树",
    "sys_role": "角色",
    "sys_user": "用户与账号",
    "sys_user_dept": "用户-部门关系",
    "sys_user_role": "用户-角色关系（角色按部门生效）",
    "sys_menu": "菜单与按钮权限",
    "sys_role_menu": "角色-菜单授权",
    "sys_dict_type": "字典类型",
    "sys_dict_data": "字典数据",
    "sys_data_scope_rule": "数据权限矩阵",
    "req_sla_config": "SLA 配置",
    "sys_work_calendar": "工作日历（只存例外）",
    "sys_no_sequence": "单据编号发号器",
    "sys_notify_channel": "通知通道",
    "sys_notify_template": "通知模板",
    "sys_deploy_config": "部署配置（示例）",
    "req_attach_rule": "附件必填规则",
    "sys_hr_job_role_map": "HR 岗位映射",
    "sys_ai_prompt": "AI 提示词",
    "req_synonym": "同义词",
    "req_project": "项目主数据（2 个）",
    "req_system": "系统主数据（8 个，全挂 ZS-PILOT）",
    "req_ticket": "演示单据（6 张）",
    "req_acceptance_criteria": "演示单的验收标准条目",
    "req_flow_log": "演示单的流转时间线",
    "req_doc": "演示单的 AI 需求文档",
    "req_ai_clarify": "演示单的 AI 澄清问答",
    "req_check_item": "重点功能点检清单（本应为空，不塞假数据）",
    "sys_schema_version": "脚本版本",
}


def 转字面量(值, 方言):
    """把 Python 值转成 SQL 字面量。字符串统一走单引号转义。"""
    if 值 is None:
        return "NULL"

    if isinstance(值, bool):
        return "1" if 值 else "0"

    if isinstance(值, (int, float)):
        if isinstance(值, float) and 值 == int(值):
            # 8.0 输出成 8，避免 Oracle 里的 .0 噪音
            return str(int(值))
        return str(值)

    # 字符串：只处理单引号，反斜杠不是 SQL 转义符，不需要转
    文本 = str(值).replace("'", "''")

    if 方言 == "oracle" and 文本[:4].isdigit() and len(文本) == 19:
        return "TO_TIMESTAMP('%s','YYYY-MM-DD HH24:MI:SS')" % 文本

    return "'%s'" % 文本


def 生成脚本(方言):
    """按方言生成整份 SQL 文本。"""
    文件头 = [
        "-- =====================================================================",
        "-- 需求全生命周期管理系统 · 种子数据（%s）" % ("SQLite" if 方言 == "sqlite" else "Oracle 12c+"),
        "-- 由 tools/gen_seed.py 生成，请勿手工编辑；要改数据请改生成器后重跑",
        "--",
        "-- 内容：6 个部门、8 个角色、8 个演示账号、菜单树与按钮权限（18 菜单 + 44 按钮）、",
        "--       19 类字典（27 态 / 17 退回点 / 100 附件槽位）、数据权限矩阵、30 条 SLA、",
        "--       工作日历、发号器、通知（12 模板）、附件规则 10 条、2 项目 + 8 系统、",
        "--       HR 映射、AI 提示词、同义词、6 张演示单（含验收标准、流转时间线与需求文档）、",
        "--       脚本版本",
        "--",
        "-- 演示账号（密码统一 Cim#Init2026，上线前必须全部改掉）：",
        "--   admin    / 系统管理员  / ADMIN           / 信息技术部",
        "--   wangwu   / 王五        / MIT_LEADER      / MIT 开发组",
        "--   lisi     / 李四        / MIT_DEV         / MIT 开发组",
        "--   zhangsan / 张三        / REPORTER        / 制造部",
        "--   zhaoliu  / 赵六        / DEPT_MANAGER    / 质量部（只读）",
        "--   sunqi    / 孙七        / PLATFORM_LEADER / 中试平台",
        "--   zhouba   / 周八        / REVIEW_LEADER   / 上海芯源创新中心",
        "--   wujiu    / 吴九        / IT_OPS          / 信息技术部",
        "--",
        "-- 执行顺序：先 schema-*.sql，再本文件。可重复执行前请先清库。",
        "-- =====================================================================",
        "",
        "BEGIN;" if 方言 == "sqlite" else "",
        "",
    ]

    行列表 = list(文件头)

    for 表名 in 输出顺序:
        行组 = 数据[表名]
        if not 行组:
            continue

        行列表.append("-- ---------------------------------------------------------------------")
        行列表.append("-- %s：%s（%d 条）" % (表名, 表中文名.get(表名, ""), len(行组)))
        行列表.append("-- ---------------------------------------------------------------------")

        列清单 = ", ".join(表定义[表名])

        for 行 in 行组:
            值清单 = ", ".join(转字面量(行[列], 方言) for 列 in 表定义[表名])
            行列表.append("INSERT INTO %s (%s) VALUES (%s);" % (表名, 列清单, 值清单))

        行列表.append("")

    行列表.append("COMMIT;" if 方言 == "sqlite" else "COMMIT;")
    行列表.append("")

    文本 = "\n".join(行列表)

    # Oracle 侧去掉空的首行占位
    if 方言 == "oracle":
        文本 = 文本.replace("\n\n\n", "\n\n")

    return 文本


def 主流程():
    两份 = [("sqlite", "seed-sqlite.sql"), ("oracle", "seed-oracle.sql")]

    统计 = []
    for 方言, 文件名 in 两份:
        目标 = DB目录 / 文件名
        目标.write_text(生成脚本(方言), encoding="utf-8")
        统计.append((文件名, 目标))

    总行数 = sum(len(行组) for 行组 in 数据.values())

    print("已生成：")
    for 文件名, 路径 in 统计:
        print("  %s  %d 字节" % (文件名, 路径.stat().st_size))

    print("\n数据量：")
    for 表名 in 输出顺序:
        if 数据[表名]:
            print("  %-24s %3d 条" % (表名, len(数据[表名])))

    print("\n合计 %d 条记录，共 %d 张表" % (总行数, len([k for k in 数据 if 数据[k]])))
    print("脚本校验和（schema-sqlite.sql）：%s" % 脚本校验和)


if __name__ == "__main__":
    主流程()
