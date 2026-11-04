"""附件闭环验证：上传、去重、拦截联动、下载、确认、删除。

用 http.client 手写 multipart，避免引入 requests 依赖。
"""
import http.client
import json
import pathlib
import sqlite3
import sys
import uuid
import zipfile

sys.stdout.reconfigure(encoding="utf-8")

HOST = "localhost"
PORT = 18080
BASE = "/api"
BACKEND = pathlib.Path("D:/ShhXinyuan/CIM-WorkSpace/Project/cim-management-system/backend")

FIXTURE_DIR = BACKEND / "data/tmp"
FIXTURE_MD = FIXTURE_DIR / "设计说明书.md"
FIXTURE_DOCX = FIXTURE_DIR / "设计文档.docx"
FIXTURE_EXE = FIXTURE_DIR / "evil.exe"


def 准备测试文件():
    """生成三份测试文件：中文名 md、最小 docx、可执行文件假头。

    自包含，不依赖仓库里的二进制文件；每次覆盖写，改内容也不会污染上一轮。
    """
    FIXTURE_DIR.mkdir(parents=True, exist_ok=True)
    FIXTURE_MD.write_text(
        "# FDS 功能设计说明\n\n"
        "## 1 背景\n来料检验单的供应商批次目前填在备注栏，格式不统一，无法统计。\n\n"
        "## 2 方案\n检验单增加供应商批次字段，从来料批次表下拉选择，提交前必填校验。\n\n"
        "## 3 影响范围\nQMS 来料检验模块，不涉及非功能需求。\n",
        encoding="utf-8")
    xml = (
        '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>'
        '<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">'
        '<w:body>'
        '<w:p><w:r><w:t>第一段：设计说明</w:t></w:r></w:p>'
        '<w:p><w:r><w:t>第二段：验收标准三条</w:t></w:r></w:p>'
        '</w:body></w:document>')
    with zipfile.ZipFile(FIXTURE_DOCX, "w", zipfile.ZIP_DEFLATED) as archive:
        archive.writestr("word/document.xml", xml)
    FIXTURE_EXE.write_bytes(b"MZ" + b"\x00" * 60)
    print("测试文件已生成于 %s" % FIXTURE_DIR)



def 请求(method, path, body=None, token=None, headers=None):
    conn = http.client.HTTPConnection(HOST, PORT, timeout=60)
    请求头 = dict(headers or {})
    if token:
        请求头["Authorization"] = "Bearer " + token
    conn.request(method, BASE + path, body=body, headers=请求头)
    resp = conn.getresponse()
    raw = resp.read()
    conn.close()
    return resp.status, raw


def json请求(method, path, payload=None, token=None):
    body = None
    headers = {}
    if payload is not None:
        body = json.dumps(payload).encode()
        headers["Content-Type"] = "application/json"
    status, raw = 请求(method, path, body, token, headers)
    return json.loads(raw.decode())


def 上传(path, attach_type, file_path, token):
    boundary = "----cim" + uuid.uuid4().hex
    文件 = pathlib.Path(file_path)
    content = 文件.read_bytes()
    头部 = []
    头部.append(("--" + boundary).encode())
    头部.append(b'Content-Disposition: form-data; name="attachType"')
    头部.append(b"")
    头部.append(attach_type.encode())
    头部.append(("--" + boundary).encode())
    头部.append(('Content-Disposition: form-data; name="file"; filename="%s"' % 文件.name).encode("utf-8"))
    头部.append(b"Content-Type: application/octet-stream")
    头部.append(b"")
    body = (b"\r\n".join(头部) + b"\r\n" + content + b"\r\n"
            + ("--" + boundary + "--").encode() + b"\r\n")
    headers = {"Content-Type": "multipart/form-data; boundary=" + boundary}
    status, raw = 请求("POST", path, body, token, headers)
    return status, json.loads(raw.decode())


def 打印(标题):
    print("\n" + "=" * 12 + " " + 标题 + " " + "=" * 12)


# ---------- 准备 ----------
准备测试文件()

# ---------- 登录 ----------
token = json请求("POST", "/auth/login", {"username": "admin", "password": "Cim#Init2026"})["data"]["accessToken"]

con = sqlite3.connect(BACKEND / "data/cim.db")
单 = con.execute("select id, ticket_no, status, project_id from req_ticket "
                 "where ticket_no='REQ-2026-000004'").fetchone()
ticket_id, 单号, 状态, 项目id = 单
项目编码 = con.execute("select project_code from req_project where id=?", (项目id,)).fetchone()[0]
print("目标单据 %s，状态 %s，项目 %s" % (单号, 状态, 项目编码))

# 清理上一轮可能残留的附件，保证从 0 开始
旧 = con.execute("select id, relative_path from req_attachment where ticket_id=?", (ticket_id,)).fetchall()
for 附件id, 相对路径 in 旧:
    con.execute("delete from req_attachment where id=?", (附件id,))
    (BACKEND / "data/attachments" / 相对路径).unlink(missing_ok=True)
con.commit()
if 旧:
    print("清理历史附件 %d 条" % len(旧))

# ---------- 1. 缺口 ----------
打印("1. 当前环节的附件缺口")
r = json请求("GET", "/ticket/%s/attach-rules" % ticket_id, token=token)
for x in r["data"]:
    print("   → %-18s 需要 %-12s %d/%d  %s" % (
        x["targetStatusLabel"], x["attachTypeLabel"], x["uploaded"], x["minCount"],
        "已满足" if x["satisfied"] else "缺"))

# ---------- 2. 上传前的按钮 ----------
打印("2. 上传前 SUBMIT_DESIGN 是否可点")
按钮 = json请求("GET", "/ticket/%s/actions" % ticket_id, token=token)["data"]
设计按钮 = [a for a in 按钮 if a["action"] == "SUBMIT_DESIGN"][0]
print("   enabled =", 设计按钮["enabled"], "| blockers =",
      [(b["bizCode"], (b.get("details") or {}).get("attachType")) for b in 设计按钮["blockers"]])

# ---------- 3. 上传 md ----------
打印("3. 上传设计说明书.md（FDS）")
status, r = 上传("/ticket/%s/attachment" % ticket_id, "FDS",
                 FIXTURE_MD, token)
print("   HTTP", status, "| code", r["code"], "| duplicated", (r.get("data") or {}).get("duplicated"))
附件1 = r["data"]["attachment"]
print("   文件名=%s 大小=%s 类型=%s 节点=%s 确认=%s 可删=%s 正文=%s" % (
    附件1["originalName"], 附件1["sizeText"], 附件1["attachTypeLabel"],
    附件1["stageLabel"], 附件1["confirmed"], 附件1["canDelete"], 附件1["textExtracted"]))

# ---------- 4. 上传后按钮 ----------
打印("4. 上传后 SUBMIT_DESIGN 是否可点")
按钮 = json请求("GET", "/ticket/%s/actions" % ticket_id, token=token)["data"]
设计按钮 = [a for a in 按钮 if a["action"] == "SUBMIT_DESIGN"][0]
print("   enabled =", 设计按钮["enabled"], "| blockers =", len(设计按钮["blockers"]))

# ---------- 5. 重复上传 ----------
打印("5. 同一文件重复上传")
status, r = 上传("/ticket/%s/attachment" % ticket_id, "FDS",
                 FIXTURE_MD, token)
print("   HTTP", status, "| duplicated =", r["data"]["duplicated"],
      "| 返回的附件 ID 是否与首次相同 =", r["data"]["attachment"]["id"] == 附件1["id"])

# ---------- 6. docx ----------
打印("6. 上传设计文档.docx（验证正文提取）")
status, r = 上传("/ticket/%s/attachment" % ticket_id, "FDS",
                 FIXTURE_DOCX, token)
附件2 = r["data"]["attachment"]
print("   code", r["code"], "| 文件名", 附件2["originalName"],
      "| 正文已提取 =", 附件2["textExtracted"])
正文 = con.execute("select extracted_text from req_attachment where id=?", (附件2["id"],)).fetchone()[0]
print("   提取内容 >>>", (正文 or "").replace("\n", " | ")[:80])

# ---------- 7. 拒绝场景 ----------
打印("7. 拒绝场景")
status, r = 上传("/ticket/%s/attachment" % ticket_id, "FDS",
                 FIXTURE_EXE, token)
print("   可执行文件 → code", r["code"], "|", r.get("message"))
status, r = 上传("/ticket/%s/attachment" % ticket_id, "NOT_EXIST",
                 FIXTURE_MD, token)
print("   非法类型   → code", r["code"], "|", r.get("message"))
status, r = 上传("/ticket/%s/attachment" % ticket_id, "",
                 FIXTURE_MD, token)
print("   空类型     → code", r["code"], "|", r.get("message"))

# ---------- 8. 列表 ----------
打印("8. 附件列表")
列表 = json请求("GET", "/ticket/%s/attachments" % ticket_id, token=token)["data"]
for x in 列表:
    print("   %-8s %-22s %-10s %-12s 可删=%s" % (
        x["attachType"], x["originalName"], x["sizeText"], x["stageLabel"], x["canDelete"]))

# ---------- 9. 下载 ----------
打印("9. 下载比对")
status, raw = 请求("GET", "/ticket/attachment/%s/download" % 附件1["id"], token=token)
原件 = (FIXTURE_MD).read_bytes()
print("   HTTP", status, "| 字节数", len(raw), "| 与原件一致 =", raw == 原件)

# ---------- 10. 确认版 ----------
打印("10. 标记确认版")
r = json请求("POST", "/ticket/attachment/%s/confirm" % 附件1["id"], {}, token)
print("   code", r["code"], "| confirmed =", r["data"]["confirmed"], "| canDelete =", r["data"]["canDelete"])
r = json请求("DELETE", "/ticket/attachment/%s" % 附件1["id"], None, token)
print("   删除已确认版 → code", r["code"], "|", r.get("message"))

# ---------- 11. 删除 ----------
打印("11. 删除未确认的附件")
r = json请求("DELETE", "/ticket/attachment/%s" % 附件2["id"], None, token)
print("   code", r["code"], "|", r.get("message") or "已删除")
列表 = json请求("GET", "/ticket/%s/attachments" % ticket_id, token=token)["data"]
print("   剩余附件", len(列表), "条")

# ---------- 12. 磁盘目录 ----------
打印("12. 磁盘目录结构")
根 = BACKEND / "data/attachments"
for p in sorted(根.rglob("*")):
    if p.is_file():
        print("   ", p.relative_to(根).as_posix(), p.stat().st_size, "字节")

# ---------- 13. 关闭后拒绝 ----------
打印("13. 关闭后是否拒绝上传")
con.execute("update req_ticket set status='CLOSED' where id=?", (ticket_id,))
con.commit()
status, r = 上传("/ticket/%s/attachment" % ticket_id, "FDS",
                 FIXTURE_MD, token)
print("   code", r["code"], "|", r.get("message"))
con.execute("update req_ticket set status='DESIGNING' where id=?", (ticket_id,))
con.commit()
print("   已还原状态为 DESIGNING")
