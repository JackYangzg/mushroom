#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
mushroom.db 跨字段关联分析 v2

改进点(相比 v1):
  1. 跨字段匹配:一条记录的 alias_names 元素命中另一条记录的 chinese_name /
     scientific_name 时,这两条记录也应被关联(用户明确要求)。
  2. 跨记录别名匹配:A 的 alias 与 B 的 alias 相等也构成关联。
  3. 名称规范化:TRIM + 对拉丁名 LOWERCASE,合并 "sp." / "SP." 之类差异。
  4. 同一记录内,自身 cn 与自身 alias 重复不算"关联"(去重 per-record 后再 union)。
  5. 输出 5 种匹配来源的拆分,sci/cn/alias-cn/alias-sci/alias-alias。

判断逻辑:
  - 收集所有"名称 token"(规范化后)在所有记录中出现的 record_id。
  - 对每个 token,如果它出现在 ≥2 个不同 record_id 上,把全部这些 record_id
    用 Union-Find 合并到同一簇。
  - 簇大小 ≥2 的记录 = 可关联。
"""

import json
import sqlite3
import sys
from collections import Counter, defaultdict
from pathlib import Path

DB_PATH = "/Users/yangzhiguo/AndroidStudioProjects/mushroom/app/src/main/assets/mushroom.db"


def normalize(name: str, is_latin: bool) -> str:
    """名称规范化:首尾空白;拉丁名 LOWERCASE。空串返回 ''。"""
    s = (name or "").strip()
    if not s:
        return ""
    return s.lower() if is_latin else s


def parse_aliases(raw: str) -> list[str]:
    """解析 alias_names JSON 数组,失败或空时返回 []."""
    if not raw or not raw.strip():
        return []
    try:
        arr = json.loads(raw)
    except (json.JSONDecodeError, TypeError):
        return []
    if not isinstance(arr, list):
        return []
    out = []
    for x in arr:
        if isinstance(x, str):
            t = x.strip()
            if t:
                out.append(t)
    return out


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
    print(f"总记录数: {total}\n")

    # —— 规范化差异统计 ——
    sci_changed = sum(
        1 for r in rows
        if (r["scientific_name"] or "").strip()
        and (r["scientific_name"] or "").strip().lower()
        != (r["scientific_name"] or "").strip()
    )
    sci_padded = sum(
        1 for r in rows
        if (r["scientific_name"] or "").strip() != (r["scientific_name"] or "")
    )
    chn_padded = sum(
        1 for r in rows
        if (r["chinese_name"] or "").strip() != (r["chinese_name"] or "")
    )
    print(f"scientific_name 含首尾空格: {sci_padded} 条")
    print(f"scientific_name 大小写不同(规范化后会合并): {sci_changed} 条")
    print(f"chinese_name    含首尾空格: {chn_padded} 条\n")

    # 规范化后的字段
    rows_data: list[dict] = []
    for idx, r in enumerate(rows):
        rows_data.append({
            "id": r["id"],
            "idx": idx,
            "sci": normalize(r["scientific_name"], is_latin=True),
            "chn": normalize(r["chinese_name"], is_latin=False),
            "aliases": [
                a for a in (normalize(x, is_latin=False) for x in parse_aliases(r["alias_names"]))
                if a
            ],
        })

    # Union-Find
    parent = list(range(total))

    def find(x: int) -> int:
        while parent[x] != x:
            parent[x] = parent[parent[x]]
            x = parent[x]
        return x

    def union(a: int, b: int) -> None:
        ra, rb = find(a), find(b)
        if ra != rb:
            parent[ra] = rb

    # 名称 token → 出现在哪些记录里
    token2ids: dict[str, set[int]] = defaultdict(set)
    for idx, d in enumerate(rows_data):
        if d["sci"]:
            token2ids[d["sci"]].add(idx)
        if d["chn"]:
            token2ids[d["chn"]].add(idx)
        for a in d["aliases"]:
            token2ids[a].add(idx)

    # pair 来源计数
    pair_sources: dict[tuple[int, int], set[str]] = defaultdict(set)

    def fields_of(d: dict, tok: str) -> set[str]:
        fs = set()
        if d["sci"] == tok:
            fs.add("sci")
        if d["chn"] == tok:
            fs.add("chn")
        if tok in d["aliases"]:
            fs.add("alias")
        return fs

    for token, ids in token2ids.items():
        ids_list = list(ids)
        if len(ids_list) < 2:
            continue
        head_idx = ids_list[0]
        head_fs = fields_of(rows_data[head_idx], token)
        for other_idx in ids_list[1:]:
            union(head_idx, other_idx)
            other_fs = fields_of(rows_data[other_idx], token)
            for hf in head_fs:
                for of in other_fs:
                    pair_sources[(head_idx, other_idx)].add(f"{hf}<->{of}")

    # 连通分量
    clusters: dict[int, set[int]] = defaultdict(set)
    for idx in range(total):
        clusters[find(idx)].add(idx)

    multi_clusters = [c for c in clusters.values() if len(c) >= 2]
    associated_idx = {i for c in multi_clusters for i in c}

    print(f"关联簇数(≥2 条): {len(multi_clusters)}")
    print(f"可关联记录数(任一簇): {len(associated_idx)}")
    print(f"占总记录: {len(associated_idx) / total:.2%}")
    print(f"完全无法关联(独立条目): {total - len(associated_idx)}\n")

    # pair 来源分类
    src_counter: Counter = Counter()
    for (_a, _b), srcs in pair_sources.items():
        for s in srcs:
            src_counter[s] += 1
    print("== 边(匹配对)来源统计 ==")
    for s, c in src_counter.most_common():
        print(f"  {c:>6} 对 | {s}")
    print(f"  共 {sum(src_counter.values())} 对 (去重后 {len(pair_sources)} 对独立边)\n")

    # 字段级覆盖
    has_sci = sum(1 for idx in associated_idx if rows_data[idx]["sci"])
    has_chn = sum(1 for idx in associated_idx if rows_data[idx]["chn"])
    has_alias = sum(1 for idx in associated_idx if rows_data[idx]["aliases"])
    print("== 关联记录的字段构成 ==")
    print(f"  有 scientific_name: {has_sci}")
    print(f"  有 chinese_name:    {has_chn}")
    print(f"  有 aliases:         {has_alias}\n")

    # 簇大小分布
    sizes = sorted((len(c) for c in multi_clusters), reverse=True)
    print("== 簇大小分布 ==")
    print(f"  最大簇: {sizes[0] if sizes else 0} 条")
    print(f"  Top 10 簇大小: {sizes[:10]}")
    print(f"  仅 2 条的小簇: {sum(1 for s in sizes if s == 2)}")
    print(f"  ≥10 条的簇:    {sum(1 for s in sizes if s >= 10)}\n")

    # 大簇样例
    print("== 大簇 Top 10 样例 ==")
    sorted_clusters = sorted(multi_clusters, key=lambda c: -len(c))[:10]
    for i, c in enumerate(sorted_clusters, 1):
        sample = [rows_data[idx] for idx in list(c)[:4]]
        ids_sample = [d["id"] for d in sample]
        sci_sample = ", ".join(f"'{d['sci'] or '∅'}'" for d in sample)
        chn_sample = ", ".join(f"'{d['chn'] or '∅'}'" for d in sample)
        alias_sample = "; ".join(f"{d['aliases']}" for d in sample[:2])
        print(f"  #{i} 簇大小={len(c)}  ids[:4]={ids_sample}")
        print(f"      sci: {sci_sample}")
        print(f"      chn: {chn_sample}")
        print(f"      alias[:2]: {alias_sample}")

    # —— 可回填别名(用户真正关心的视角)——
    # 簇大小 ≥2 且 簇内至少一条记录有 alias_names → 其他空 alias 的记录可回填
    print("\n== 可回填别名 视角(用户实际目标) ==")
    backfillable = 0
    alias_donor_clusters = 0
    for c in multi_clusters:
        has_alias_member = any(rows_data[idx]["aliases"] for idx in c)
        if not has_alias_member:
            continue
        alias_donor_clusters += 1
        for idx in c:
            if not rows_data[idx]["aliases"]:
                backfillable += 1

    print(f"  含别名供体的簇数:                  {alias_donor_clusters} 个")
    print(f"  可被回填别名的记录数(空 alias):    {backfillable} 条")
    print(f"  当前 alias_names 已非空的记录数:   "
          f"{sum(1 for d in rows_data if d['aliases'])} 条")
    print(f"  回填后潜在非空别名记录数:          "
          f"{sum(1 for d in rows_data if d['aliases']) + backfillable} 条")

    # 对比 v1
    print("\n== 与 v1 对比 ==")
    v1 = 5360
    print(f"  v1 (仅同字段重复):          {v1} 条可关联 (90.51%)")
    print(f"  v2 (含跨字段 + 大小写合并): {len(associated_idx)} 条可关联 "
          f"({len(associated_idx)/total:.2%})")
    print(f"  新增可关联记录: +{len(associated_idx) - v1} 条")

    con.close()
    return 0


if __name__ == "__main__":
    sys.exit(main())