# -*- coding: utf-8 -*-
"""
建表脚本一致性校验。

用途：
    schema-sqlite.sql 与 schema-oracle.sql 是两份手写 DDL，字段必须严格一一对应。
    手写两份最大的风险不是写错，而是「后来只改了一边」——加了个字段只加在 SQLite 上，
    Oracle 那边没有，等到换库那天才发现，那时的修复成本远高于现在跑一次脚本。

用法：
    python check_schema.py

输出：
    表名差异、列名差异、列顺序差异。有任何差异时退出码为 1，可直接挂到提交前检查里。
"""

import re
import sys
from pathlib import Path


# 这些行不是列定义，解析时要跳过。
# 必须写全「PRIMARY KEY」而不是裸「PRIMARY」：否则 primary_dept_id 这类以 primary 开头的
# 业务列会被当成主键定义跳过，两份脚本一起漏检、还互相「一致」，比直接报错更难发现。
非列定义模式 = re.compile(
    r"^(CONSTRAINT|PRIMARY\s+KEY|UNIQUE|FOREIGN\s+KEY|CHECK|KEY)\b",
    re.IGNORECASE,
)


def 解析建表语句(文件路径):
    """
    从 DDL 文件里提取 {表名: [列名, ...]}。

    只关心表名和列名的先后顺序，不关心类型——类型本来就要两套写法。
    """
    with open(文件路径, "r", encoding="utf-8") as 文件:
        全文 = 文件.read()

    # 去掉行注释，避免注释里的括号和逗号干扰解析
    去注释后 = re.sub(r"--[^\n]*", "", 全文)

    结果 = {}

    建表语句列表 = re.findall(
        r"CREATE\s+TABLE\s+(\w+)\s*\((.*?)\n\s*\);",
        去注释后,
        flags=re.IGNORECASE | re.DOTALL,
    )

    for 表名, 表体 in 建表语句列表:
        列名列表 = []

        for 原始行 in 表体.split("\n"):
            行 = 原始行.strip().rstrip(",").strip()

            if not 行:
                continue
            if 非列定义模式.match(行):
                continue

            # 第一个空白前的部分就是列名
            列名 = 行.split()[0]

            # 排除明显不是列名的残留
            if not re.match(r"^[a-z_][a-z0-9_]*$", 列名):
                continue

            列名列表.append(列名)

        结果[表名.lower()] = 列名列表

    return 结果


def 主流程():
    本目录 = Path(__file__).resolve().parent
    目录 = 本目录 / ".." / "src" / "main" / "resources" / "db"

    SQLite结构 = 解析建表语句(目录 / "schema-sqlite.sql")
    Oracle结构 = 解析建表语句(目录 / "schema-oracle.sql")

    print("SQLite 表数：%d" % len(SQLite结构))
    print("Oracle 表数：%d" % len(Oracle结构))

    有差异 = False

    只有SQLite = sorted(set(SQLite结构) - set(Oracle结构))
    只有Oracle = sorted(set(Oracle结构) - set(SQLite结构))

    if 只有SQLite:
        有差异 = True
        print("\n[表缺失] 只在 SQLite 里存在：")
        for 表名 in 只有SQLite:
            print("  - " + 表名)

    if 只有Oracle:
        有差异 = True
        print("\n[表缺失] 只在 Oracle 里存在：")
        for 表名 in 只有Oracle:
            print("  - " + 表名)

    共有表 = sorted(set(SQLite结构) & set(Oracle结构))
    列总对数 = 0

    for 表名 in 共有表:
        SQLite列 = SQLite结构[表名]
        Oracle列 = Oracle结构[表名]
        列总对数 += len(SQLite列)

        if SQLite列 == Oracle列:
            continue

        有差异 = True
        print("\n[列不一致] 表 " + 表名)

        只在SQLite的列 = [列 for 列 in SQLite列 if 列 not in Oracle列]
        只在Oracle的列 = [列 for 列 in Oracle列 if 列 not in SQLite列]

        if 只在SQLite的列:
            print("  仅 SQLite 有：" + "、".join(只在SQLite的列))
        if 只在Oracle的列:
            print("  仅 Oracle 有：" + "、".join(只在Oracle的列))

        if not 只在SQLite的列 and not 只在Oracle的列:
            print("  列集合相同但顺序不同")
            print("    SQLite：" + "、".join(SQLite列))
            print("    Oracle：" + "、".join(Oracle列))

    print("\n共用表 %d 张，累计比对列 %d 个" % (len(共有表), 列总对数))

    if 有差异:
        print("\n结果：存在差异，请修正后再提交")
        return 1

    print("结果：两份脚本完全一致")
    return 0


if __name__ == "__main__":
    sys.exit(主流程())
