#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
分析 mushroom.db 中能通过 scientific_name / chinese_name 关联的条目。
考虑字段前后可能存在空格(用 TRIM 处理)。

判断逻辑:
  1. 对每条记录,scientific_name / chinese_name 先做 strip 处理(同时把空串视为空)。
  2. 找出"scientific_name 在多条记录中重复"的组,以及 "chinese_name 重复"的组。
  3. 统计:能通过 scientific_name 关联到的记录数、chinese_name 关联到的记录数、
     以及两者取并集(任一名称重复)的记录数。
  4. 额外报告:这些重复名称本身的数量(去重后)、Top 样例,以及关联簇(连通分量)数。
"""

import sqlite3
import sys
from collections import defaultdict
from pathlib import Path

DB_PATH = "/Users/yangzhiguo/AndroidStudioProjects/mushroom/app/src/main/assets/mushroom.db"


def main() -> int:
    if not Path(DB_PATH).exists():
        print(f"数据库不存在: {DB_PATH}", file=sys.stderr)
        return 1

    con = sqlite3.connect(DB_PATH)
    con.row_factory = sqlite3.Row
    cur = con.cursor()

    # 用 SQL TRIM 排除两端空格影响;空串视为空
    rows = cur.execute(
        """
        SELECT
            id,
            TRIM(COALESCE(scientific_name, '')) AS scientific_name,
            TRIM(COALESCE(chinese_name, ''))   AS chinese_name
        FROM mushroom_species
        """
    ).fetchall()
    total = len(rows)
    print(f"总记录数: {total}\n")

    # —— 检查"原始数据含首尾空格"的记录数 ——
    raw_rows = cur.execute(
        "SELECT scientific_name, chinese_name FROM mushroom_species"
    ).fetchall()
    sci_padded = sum(
        1 for r in raw_rows
        if r["scientific_name"] and r["scientific_name"] != r["scientific_name"].strip()
    )
    chn_padded = sum(
        1 for r in raw_rows
        if r["chinese_name"] and r["chinese_name"] != r["chinese_name"].strip()
    )
    print(f"原始数据中含首尾空格的记录:")
    print(f"  scientific_name: {sci_padded}")
    print(f"  chinese_name:    {chn_padded}\n")

    # —— 按名称分组(已 trim) ——
    sci_index: dict[str, list[int]] = defaultdict(list)
    chn_index: dict[str, list[int]] = defaultdict(list)
    sci_empty = chn_empty = 0
    for r in rows:
        sn, cn = r["scientific_name"], r["chinese_name"]
        if sn:
            sci_index[sn].append(r["id"])
        else:
            sci_empty += 1
        if cn:
            chn_index[cn].append(r["id"])
        else:
            chn_empty += 1

    # 重复组:出现 ≥2 次
    sci_dup_names = {k: v for k, v in sci_index.items() if len(v) >= 2}
    chn_dup_names = {k: v for k, v in chn_index.items() if len(v) >= 2}

    sci_assoc_ids: set[int] = {i for ids in sci_dup_names.values() for i in ids}
    chn_assoc_ids: set[int] = {i for ids in chn_dup_names.values() for i in ids}
    union_assoc_ids = sci_assoc_ids | chn_assoc_ids

    print(f"scientific_name 为空的记录数: {sci_empty}")
    print(f"chinese_name    为空的记录数: {chn_empty}\n")

    print(f"按 scientific_name 重复的去重名称数: {len(sci_dup_names)}")
    print(f"按 chinese_name    重复的去重名称数: {len(chn_dup_names)}\n")

    print(f"通过 scientific_name 关联到的记录数: {len(sci_assoc_ids)}")
    print(f"通过 chinese_name    关联到的记录数: {len(chn_assoc_ids)}")
    print(f"两者并集(任一名称能关联):           {len(union_assoc_ids)}")
    print(f"占总记录比例: {len(union_assoc_ids) / total:.2%}\n")

    # —— Top 样例 ——
    print("== scientific_name 重复 Top 10 ==")
    for name, ids in sorted(sci_dup_names.items(), key=lambda kv: -len(kv[1]))[:10]:
        suffix = "..." if len(ids) > 6 else ""
        print(f"  {len(ids):>3} 条 | {name}  ids={ids[:6]}{suffix}")

    print("\n== chinese_name 重复 Top 10 ==")
    for name, ids in sorted(chn_dup_names.items(), key=lambda kv: -len(kv[1]))[:10]:
        suffix = "..." if len(ids) > 6 else ""
        print(f"  {len(ids):>3} 条 | {name}  ids={ids[:6]}{suffix}")

    # —— 来源拆分 ——
    sci_only = sci_assoc_ids - chn_assoc_ids
    chn_only = chn_assoc_ids - sci_assoc_ids
    both     = sci_assoc_ids & chn_assoc_ids
    print("\n== 关联来源拆分 ==")
    print(f"  仅 scientific_name 重复: {len(sci_only)} 条")
    print(f"  仅 chinese_name    重复: {len(chn_only)} 条")
    print(f"  两者都重复:             {len(both)} 条")

    # —— 关联簇(以 sci/cn 重复为边的连通分量) ——
    parent = list(range(total + 1))
    id2idx = {r["id"]: idx for idx, r in enumerate(rows)}

    def find(x):
        while parent[x] != x:
            parent[x] = parent[parent[x]]
            x = parent[x]
        return x

    def union(a, b):
        ra, rb = find(a), find(b)
        if ra != rb:
            parent[ra] = rb

    for ids in sci_dup_names.values():
        head = id2idx[ids[0]]
        for i in ids[1:]:
            union(head, id2idx[i])
    for ids in chn_dup_names.values():
        head = id2idx[ids[0]]
        for i in ids[1:]:
            union(head, id2idx[i])

    roots: dict[int, set[int]] = defaultdict(set)
    for r in rows:
        if r["id"] in union_assoc_ids:
            roots[find(id2idx[r["id"]])].add(r["id"])

    cluster_sizes = [len(s) for s in roots.values() if len(s) >= 2]
    print(f"\n关联簇(连通分量)数: {len(cluster_sizes)}")
    if cluster_sizes:
        print(f"  最大簇:  {max(cluster_sizes)} 条")
        print(f"  平均大小:{sum(cluster_sizes) / len(cluster_sizes):.2f} 条")
        print(f"  仅 2 条的簇: {sum(1 for s in cluster_sizes if s == 2)}")

    con.close()
    return 0


if __name__ == "__main__":
    sys.exit(main())