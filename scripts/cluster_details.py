#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
mushroom.db 簇级明细 + 回填计划生成

输出:
  1. Top 30 含别名供体的簇(donor 数 / 空 alias 成员数 / 簇大小 / 名字)
  2. 孤儿簇(* sp. 占位符,无任何 cn/alias)大小分布
  3. 生成 SQL:对每条空 alias 记录,从簇内 donor 合并 alias_names,
     输出 UPDATE 语句到 scripts/backfill_plan.sql(dry-run,目标 db 需自行复制)。
"""

import json
import sqlite3
import sys
from collections import defaultdict
from pathlib import Path

DB_PATH = "/Users/yangzhiguo/AndroidStudioProjects/mushroom/app/src/main/assets/mushroom.db"
SQL_OUT = "/Users/yangzhiguo/AndroidStudioProjects/mushroom/scripts/backfill_plan.sql"


def normalize(name: str, is_latin: bool) -> str:
    s = (name or "").strip()
    if not s:
        return ""
    return s.lower() if is_latin else s


def parse_aliases(raw: str) -> list[str]:
    if not raw or not raw.strip():
        return []
    try:
        arr = json.loads(raw)
    except (json.JSONDecodeError, TypeError):
        return []
    if not isinstance(arr, list):
        return []
    return [x.strip() for x in arr if isinstance(x, str) and x.strip()]


def main() -> int:
    if not Path(DB_PATH).exists():
        print(f"数据库不存在: {DB_PATH}", file=sys.stderr)
        return 1

    con = sqlite3.connect(DB_PATH)
    con.row_factory = sqlite3.Row
    cur = con.cursor()

    rows = cur.execute(
        "SELECT id, scientific_name, chinese_name, alias_names FROM mushroom_species"
    ).fetchall()
    total = len(rows)

    rows_data = []
    for idx, r in enumerate(rows):
        rows_data.append({
            "id": r["id"],
            "idx": idx,
            "sci": normalize(r["scientific_name"], is_latin=True),
            "chn": normalize(r["chinese_name"], is_latin=False),
            "aliases": parse_aliases(r["alias_names"]),
        })

    # Union-Find
    parent = list(range(total))

    def find(x):
        while parent[x] != x:
            parent[x] = parent[parent[x]]
            x = parent[x]
        return x

    def union(a, b):
        ra, rb = find(a), find(b)
        if ra != rb:
            parent[ra] = rb

    token2ids = defaultdict(set)
    for idx, d in enumerate(rows_data):
        if d["sci"]:
            token2ids[d["sci"]].add(idx)
        if d["chn"]:
            token2ids[d["chn"]].add(idx)
        for a in d["aliases"]:
            token2ids[a].add(idx)

    for token, ids in token2ids.items():
        ids_list = list(ids)
        if len(ids_list) < 2:
            continue
        head = ids_list[0]
        for other in ids_list[1:]:
            union(head, other)

    clusters = defaultdict(set)
    for idx in range(total):
        clusters[find(idx)].add(idx)

    multi = [c for c in clusters.values() if len(c) >= 2]
    isolated = total - sum(len(c) for c in multi)
    cid_to_members = {id(c): c for c in multi}

    # 簇摘要
    cluster_info = []  # (cid_repr, size, donors, empty, sci, chn)
    for members in multi:
        donors = sum(1 for idx in members if rows_data[idx]["aliases"])
        empty = sum(1 for idx in members if not rows_data[idx]["aliases"])
        sci_rep = ""
        chn_rep = ""
        for idx in members:
            if rows_data[idx]["sci"] and not sci_rep:
                sci_rep = rows_data[idx]["sci"]
            if rows_data[idx]["chn"] and not chn_rep:
                chn_rep = rows_data[idx]["chn"]
            if sci_rep and chn_rep:
                break
        cluster_info.append((id(members), len(members), donors, empty, sci_rep, chn_rep, members))

    # —— 1) Top 30 含 donor 簇 ——
    print("== Top 30 可回填簇(按空 alias 成员数降序) ==")
    donor_clusters = [c for c in cluster_info if c[2] >= 1 and c[3] >= 1]
    donor_clusters.sort(key=lambda x: -x[3])
    print(f"  {'簇大小':>5} {'供体':>4} {'待回填':>5}  sci / chn")
    for _, sz, donors, empty, sci, chn, _ in donor_clusters[:30]:
        sci_disp = sci or "(无)"
        chn_disp = chn or "(无)"
        print(f"  {sz:>5} {donors:>4} {empty:>5}  {sci_disp}  /  {chn_disp}")
    total_backfillable_all = sum(c[3] for c in donor_clusters)
    print(f"  ... 总 {len(donor_clusters)} 个含 donor 簇,共 {total_backfillable_all} 条待回填\n")

    # —— 2) 孤儿簇(* sp. 占位符,无 cn/alias)——
    print("== 孤儿簇(无 cn 且 无 alias,sci='* sp.' 占位符) ==")
    orphan_clusters = []
    for _, sz, donors, empty, sci, chn, members in cluster_info:
        if not chn and donors == 0:
            orphan_clusters.append((sz, sci, members))
    orphan_clusters.sort(key=lambda x: -x[0])
    total_orphan_records = sum(sz for sz, _, _ in orphan_clusters)
    print(f"  共 {len(orphan_clusters)} 个孤儿簇,{total_orphan_records} 条记录 "
          f"({total_orphan_records / total:.2%})")
    print(f"  {'簇大小':>5}  sci")
    for sz, sci, _ in orphan_clusters[:20]:
        print(f"  {sz:>5}  {sci}")
    if len(orphan_clusters) > 20:
        print(f"  ... 还有 {len(orphan_clusters) - 20} 个")
    print()

    # —— 3) 孤立记录 ——
    print(f"== 完全孤立记录 ==")
    print(f"  {isolated} 条无任何重复({isolated/total:.2%})\n")

    # —— 4) 生成回填 SQL ——
    print("== 生成回填 SQL ==")
    sql_lines = [
        "-- mushroom.db alias_names 回填计划",
        "-- 由 scripts/cluster_details.py 自动生成",
        "-- 逻辑:同 Union-Find 簇内,所有非空 alias 成员合并去重,回填到空 alias 成员",
        "-- 使用前请先 cp app/src/main/assets/mushroom.db app/src/main/assets/mushroom.db.bak",
        "",
        "BEGIN TRANSACTION;",
        "",
    ]
    plan_count = 0
    for _, sz, donors, empty, sci, chn, members in donor_clusters:
        merged = []
        seen = set()
        for idx in members:
            for a in rows_data[idx]["aliases"]:
                if a not in seen:
                    seen.add(a)
                    merged.append(a)
        if not merged:
            continue
        new_alias_json = json.dumps(merged, ensure_ascii=False)
        # 单引号转义(SQLite 风格)
        escaped = new_alias_json.replace("'", "''")
        for idx in members:
            if not rows_data[idx]["aliases"]:
                sql_lines.append(
                    f"UPDATE mushroom_species SET alias_names = '{escaped}' "
                    f"WHERE id = {rows_data[idx]['id']};"
                )
                plan_count += 1

    sql_lines.append("")
    sql_lines.append("COMMIT;")
    sql_lines.append(f"-- 共计划更新 {plan_count} 条")

    Path(SQL_OUT).write_text("\n".join(sql_lines), encoding="utf-8")
    print(f"  写入 {SQL_OUT}")
    print(f"  共 {plan_count} 条 UPDATE 语句")

    # —— 5) 额外摘要:回填前后 alias 覆盖率 ——
    cur_after = sum(1 for d in rows_data if d["aliases"]) + plan_count
    print(f"\n== 回填效果预估 ==")
    print(f"  当前 alias 非空: {sum(1 for d in rows_data if d['aliases'])} 条 "
          f"({sum(1 for d in rows_data if d['aliases'])/total:.2%})")
    print(f"  回填后:        {cur_after} 条 ({cur_after/total:.2%})")
    print(f"  提升:          +{plan_count} 条 "
          f"({(cur_after - sum(1 for d in rows_data if d['aliases']))/total:.2%})")

    con.close()
    return 0


if __name__ == "__main__":
    sys.exit(main())