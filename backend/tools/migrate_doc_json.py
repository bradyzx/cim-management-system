#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""把 req_doc.doc_json 从旧结构迁移到契约 11 §8.1.1 的 12 节标准键名。

**为什么迁移**：早期生成文档时键名是 s1_background / s2_goal / sN_placeholder，
契约定稿后应为 s1_name / s2_asis / … / s12_ai_review。前端按定稿的 12 个 key
渲染卡片，旧键名取不到值，而且是「不报错、只空白」，比报错更难发现。

**做法**：不从旧 doc_json 反推映射（那条数据里语义也放错了：s1_background 存的是
「现状」而不是「背景」），而是直接拿 req_ticket 的字段重建 —— 提单页填的那几个
字段才是真相源。旧文档里的验收标准也因此按行重新拆 seq，剥掉「1. 」前缀。

**幂等**：doc_json 里已经出现 s1_name 的直接跳过，可反复执行。

用法：
    python tools/migrate_doc_json.py            # 只看，不改
    python tools/migrate_doc_json.py --apply    # 实际写入
"""

import json
import os
import shutil
import sqlite3
import sys

# 库路径：脚本放在 backend/tools/，库在 backend/data/cim.db
BACKEND_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DB_PATH = os.path.join(BACKEND_DIR, "data", "cim.db")

# 12 节的顺序与中文标题，与 Java 侧 DocSections 保持一致
SECTION_KEYS = [
    "s1_name", "s2_asis", "s3_pain", "s4_tobe", "s5_functions", "s6_nfr",
    "s7_scope", "s8_priority", "s9_effort", "s10_acceptance",
    "s11_attachments", "s12_ai_review",
]


def strip_seq_prefix(text):
    """去掉行首的「1.」「2、」类手写序号，保留正文。"""
    text = text.strip()
    if not text:
        return ""
    i = 0
    while i < len(text) and text[i].isdigit():
        i += 1
    if i > 0 and i < len(text) and text[i] in ".、．":
        return text[i + 1:].strip()
    return text


def build_acceptance(text):
    """验收标准按行拆条，seq 从 1 连续递增。"""
    items = []
    if not text:
        return items
    for line in text.splitlines():
        content = strip_seq_prefix(line)
        if not content:
            continue
        items.append({"seq": len(items) + 1, "text": content})
    return items


def completeness(sections):
    """规则版完整度：五个必填节各 20 分，与 Java 侧算完整度同口径。"""
    score = 0
    for key in ("s1_name", "s2_asis", "s4_tobe"):
        if sections.get(key, {}).get("content", "").strip():
            score += 20
    if sections.get("s5_functions", {}).get("items"):
        score += 20
    if sections.get("s10_acceptance", {}).get("items"):
        score += 20
    return score


def build_doc_json(ticket):
    """按单据字段重建 12 节，结构与新代码生成的一致。"""
    sections = {
        "s1_name": {"content": ticket["title"] or ""},
        "s2_asis": {"content": ticket["content_asis"] or ""},
        "s3_pain": {"content": "", "metrics": []},
        "s4_tobe": {"content": ticket["content_tobe"] or ""},
        "s5_functions": {"items": []},
        "s6_nfr": {"items": []},
        "s7_scope": {
            "systems": [{"systemCode": ticket["system_code"] or "", "impact": ""}],
            "depts": [],
        },
        "s8_priority": {
            "level": ticket["priority"] or "",
            "scores": {"impact": 0, "urgency": 0, "compliance": 0},
            "reason": "",
        },
        "s9_effort": {
            "minDays": None,
            "maxDays": None,
            "suggestReleaseDate": None,
            "aiEstimated": 1,
            "confirmedBy": None,
        },
        "s10_acceptance": {"items": build_acceptance(ticket["acceptance_criteria"])},
        "s11_attachments": {"items": []},
        "s12_ai_review": {
            "dupScore": 0,
            "verdict": "NEW",
            "completeness": 0,
            "risks": [],
            "unclarified": [],
        },
    }
    sections["s12_ai_review"]["completeness"] = completeness(sections)
    return json.dumps({"schemaVersion": 1, "sections": sections},
                      ensure_ascii=False, separators=(",", ":"))


def main():
    apply = "--apply" in sys.argv

    if not os.path.exists(DB_PATH):
        print("找不到库文件：%s" % DB_PATH)
        return 1

    conn = sqlite3.connect(DB_PATH)
    conn.row_factory = sqlite3.Row

    rows = conn.execute("""
        SELECT d.id AS doc_id, d.ticket_id, d.doc_json, d.confirmed_at,
               t.ticket_no, t.title, t.content_asis, t.content_tobe,
               t.acceptance_criteria, t.priority, t.system_code
        FROM req_doc d
        LEFT JOIN req_ticket t ON t.id = d.ticket_id
    """).fetchall()

    todo = []
    for row in rows:
        if not row["ticket_no"]:
            print("跳过 %s：对应单据不存在" % row["doc_id"])
            continue
        try:
            old = json.loads(row["doc_json"] or "{}")
        except ValueError:
            old = {}
        old_sections = old.get("sections")
        # 已经是新结构（sections 为对象且含 s1_name）就跳过
        if isinstance(old_sections, dict) and "s1_name" in old_sections:
            print("跳过 %s：已是 12 节新结构" % row["ticket_no"])
            continue
        todo.append(row)

    if not todo:
        print("没有需要迁移的文档。")
        return 0

    for row in todo:
        new_json = build_doc_json(row)
        print("待迁移 %s（%s）" % (row["ticket_no"], row["title"]))
        print("  新结构节数：%d" % len(json.loads(new_json)["sections"]))
        if apply:
            conn.execute("UPDATE req_doc SET doc_json = ? WHERE id = ?",
                         (new_json, row["doc_id"]))

    if apply:
        # 先备份再写，虽然 UPDATE 只动一列，回滚也要有退路
        backup = DB_PATH + ".before-doc-migrate"
        if not os.path.exists(backup):
            conn.commit()
            shutil.copy2(DB_PATH, backup)
            print("已备份到 %s" % backup)
        conn.commit()
        print("已写入 %d 条。" % len(todo))
    else:
        print("共 %d 条待迁移。确认无误后加 --apply 执行。" % len(todo))

    conn.close()
    return 0


if __name__ == "__main__":
    sys.exit(main())
