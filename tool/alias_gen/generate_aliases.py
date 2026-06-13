#!/usr/bin/env python3
"""Batch-generate mushroom aliases via LLM and write to alias_mushroom.db.

Source : mushroom.db  -> mushroom_species  (scientific_name, species_common)
         NOTE: the schema has no `common_name` column; `species_common` holds
         the people's / folk name and is treated as `common_name` per the task.
Target : alias_mushroom.db -> mushroom_alias  (cleared, then inserted)

Usage:
  python3 generate_aliases.py [--batch-size 25] [--max-retries 4] [--limit 0]
"""
from __future__ import annotations

import argparse
import json
import os
import re
import sqlite3
import sys
import time
import urllib.error
import urllib.request
from pathlib import Path

# ---------------------------------------------------------------------------
# Paths
# ---------------------------------------------------------------------------
ROOT = Path(__file__).resolve().parents[2]  # mushroom/
SRC_DB = ROOT / "app/src/main/assets/mushroom.db"
DST_DB = ROOT / "app/src/main/assets/alias_mushroom.db"
SPECIES_JSONL = Path(__file__).resolve().parent / "species.jsonl"

# ---------------------------------------------------------------------------
# LLM config
# ---------------------------------------------------------------------------
API_BASE: str = os.environ.get("ANTHROPIC_BASE_URL", "https://api.minimaxi.com/anthropic").rstrip("/")
API_KEY: str = os.environ.get("ANTHROPIC_AUTH_TOKEN", "")
MODEL: str = os.environ.get("ANTHROPIC_MODEL", "MiniMax-M3")

# ---------------------------------------------------------------------------
# Prompt (verbatim from the task)
# ---------------------------------------------------------------------------
PROMPT_TEMPLATE: str = """你是一名真菌名称标准化助手。请根据输入的蘑菇 scientific_name 和 common_name，为每个物种生成民众日常可能使用的俗名 aliases。

核心要求：
1. scientific_name 和 common_name 是输入字段，必须在输出中原样保留，不得翻译、改写、大小写转换或缩写。备注：其中字段可能为空，保持原样即可
2. aliases 只表示"民众称呼的俗名 / 常见叫法 / 民间叫法/俗称"。
3. 不得改写 common_name 和 scientific_name。
4. aliases 可包含高置信度的中文俗名、英文俗名、地区性常见叫法。
5. 不确定的俗名不要编造。
6. 不要把其他物种名称作为 aliases。
7. 每个物种最多返回 10 个 aliases，去重，去掉空字符串。
8. 返回严格 JSON，不要 Markdown，不要解释。

输入格式：
{
"items": [
{
"id": "唯一标识",
"scientific_name": "Amanita muscaria",
"common_name": "Fly Agaric"
}
]
}

输出格式：
{
"items": [
{
"id": "唯一标识",
"scientific_name": "Amanita muscaria",
"common_name": "Fly Agaric",
"aliases": [
"Fly Agaric",
"fly amanita",
"毒蝇伞"
]
}
]
}

现在请处理以下批量数据：
{BATCH_JSON}
"""

# ---------------------------------------------------------------------------
# Logging helpers
# ---------------------------------------------------------------------------
ANSI: dict[str, str] = {
    "reset": "\x1b[0m", "bold": "\x1b[1m", "dim": "\x1b[2m",
    "green": "\x1b[32m", "cyan": "\x1b[36m", "yellow": "\x1b[33m",
    "red": "\x1b[31m", "magenta": "\x1b[35m",
}


def c(color: str, text: str) -> str:
    return f"{ANSI[color]}{text}{ANSI['reset']}" if sys.stdout.isatty() else text


def log(msg: str) -> None:
    print(msg, flush=True)


def log_step(msg: str) -> None:
    log(c("cyan", "▸ ") + msg)


def progress_bar(done: int, total: int, width: int = 30) -> str:
    if total <= 0:
        return ""
    pct = done / total
    fill = int(width * pct)
    return "[" + "█" * fill + "·" * (width - fill) + f"] {pct*100:5.1f}%"


# ---------------------------------------------------------------------------
# Data
# ---------------------------------------------------------------------------
def load_species_from_db() -> list[dict]:
    """Read distinct species from mushroom.db, mapping columns to the task's
    `scientific_name` / `common_name` (which lives in `species_common`)."""
    conn = sqlite3.connect(str(SRC_DB))
    try:
        cur = conn.cursor()
        rows = cur.execute(
            """
            SELECT scientific_name, species_common
              FROM mushroom_species
             WHERE scientific_name IS NOT NULL
               AND TRIM(scientific_name) <> ''
            """
        ).fetchall()
    finally:
        conn.close()

    seen: set[str] = set()
    out: list[dict] = []
    for sci, com in rows:
        sci_s = (sci or "").strip()
        com_s = (com or "").strip()  # species_common may legitimately be empty
        if sci_s in seen:
            continue
        seen.add(sci_s)
        out.append({
            "id": sci_s,
            "scientific_name": sci_s,
            "common_name": com_s,
        })
    return out


def export_species_jsonl(species: list[dict]) -> None:
    """Write a JSON-lines file so retry_failed.py can look up common_name by id."""
    with SPECIES_JSONL.open("w", encoding="utf-8") as fh:
        for s in species:
            fh.write(json.dumps({
                "scientific_name": s["scientific_name"],
                "common_name": s["common_name"],
            }, ensure_ascii=False) + "\n")


# ---------------------------------------------------------------------------
# LLM
# ---------------------------------------------------------------------------
def call_llm(batch_items: list[dict], retries: int) -> dict:
    """Call the Anthropic Messages API and return parsed JSON dict."""
    user_text = PROMPT_TEMPLATE.replace(
        "{BATCH_JSON}",
        json.dumps({"items": batch_items}, ensure_ascii=False),
    )
    body = {
        "model": MODEL,
        "max_tokens": 8192,
        "messages": [{"role": "user", "content": user_text}],
    }
    data = json.dumps(body).encode("utf-8")
    req = urllib.request.Request(
        f"{API_BASE}/v1/messages",
        data=data,
        method="POST",
        headers={
            "content-type": "application/json",
            "x-api-key": API_KEY,
            "anthropic-version": "2023-06-01",
        },
    )
    last_err: Exception | None = None
    for attempt in range(1, retries + 1):
        try:
            with urllib.request.urlopen(req, timeout=180) as resp:
                payload = json.loads(resp.read().decode("utf-8"))
            text = ""
            for blk in payload.get("content", []):
                if blk.get("type") == "text":
                    text += blk.get("text", "")
            text = text.strip()
            text = re.sub(r"^```(?:json)?\s*", "", text)
            text = re.sub(r"\s*```$", "", text)
            return json.loads(text)
        except (urllib.error.URLError, urllib.error.HTTPError, json.JSONDecodeError, KeyError) as e:
            last_err = e
            wait = min(2 ** attempt, 30)
            log(c("yellow", f"   ⚠ attempt {attempt}/{retries} failed: {e!s}; retry in {wait}s"))
            time.sleep(wait)
    raise RuntimeError(f"LLM call failed after {retries} attempts: {last_err!s}")


# ---------------------------------------------------------------------------
# DB I/O
# ---------------------------------------------------------------------------
def clear_alias_table(conn: sqlite3.Connection) -> None:
    cur = conn.cursor()
    cur.execute("DELETE FROM mushroom_alias;")
    try:
        cur.execute("DELETE FROM sqlite_sequence WHERE name='mushroom_alias';")
    except sqlite3.OperationalError:
        pass
    conn.commit()


def _merge_aliases(llm_aliases: list, common_name: str) -> list[str]:
    """Merge the LLM's aliases with the input common_name.

    The LLM is told not to invent names and to return an empty list when there
    are no high-confidence folk names. In that case the input `common_name`
    is itself a folk name and would otherwise be lost. We fold it back in
    (dedup, drop empty, cap at 10) so every species has at least one alias.
    """
    out: list[str] = []
    seen: set[str] = set()
    for src in (list(llm_aliases or []), [common_name]):
        for v in src:
            if not isinstance(v, str):
                continue
            s = v.strip()
            if not s or s in seen:
                continue
            seen.add(s)
            out.append(s)
            if len(out) >= 10:
                return out
    return out


def insert_aliases(conn: sqlite3.Connection, results: list[dict]) -> int:
    cur = conn.cursor()
    rows: list[tuple] = []
    for item in results:
        sci = item.get("scientific_name", "")
        com = item.get("common_name", "")
        merged = _merge_aliases(item.get("aliases", []), com)
        aliases_json = json.dumps(merged, ensure_ascii=False)
        rows.append((sci, com, aliases_json))
    cur.executemany(
        "INSERT INTO mushroom_alias (scientificName, commonName, aliases) VALUES (?, ?, ?);",
        rows,
    )
    conn.commit()
    return len(rows)


# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------
def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--batch-size", type=int, default=25)
    ap.add_argument("--max-retries", type=int, default=4)
    ap.add_argument("--limit", type=int, default=0, help="process only first N species (for testing)")
    args = ap.parse_args()

    if not API_KEY:
        log(c("red", "ERROR: ANTHROPIC_AUTH_TOKEN env var is empty. Aborting."))
        sys.exit(1)

    log_step(f"Source DB : {SRC_DB}")
    log_step(f"Target DB : {DST_DB}")
    log_step(f"Model     : {MODEL} @ {API_BASE}")
    log_step("Field map: mushroom_species.scientific_name -> scientific_name  |  species_common -> common_name")
    log("")

    species = load_species_from_db()
    export_species_jsonl(species)
    log_step(f"Exported species.jsonl with {len(species)} unique entries")

    if args.limit:
        species = species[: args.limit]
    total = len(species)
    log_step(f"Species to process: {total}")
    log("")

    dst = sqlite3.connect(str(DST_DB))
    clear_alias_table(dst)
    log_step(c("magenta", f"Cleared mushroom_alias table in {DST_DB.name}"))
    log("")

    batch_size = max(1, args.batch_size)
    batches = [species[i : i + batch_size] for i in range(0, total, batch_size)]
    log_step(f"Batch size: {batch_size}  ->  {len(batches)} batches")
    log(c("dim", "─" * 60))

    overall_start = time.time()
    processed = 0
    failed_items: list[str] = []
    all_results: list[dict] = []

    for b_idx, batch in enumerate(batches, start=1):
        t0 = time.time()
        log(
            c("bold", f"[Batch {b_idx:>3}/{len(batches)}]")
            + c("dim", f"  size={len(batch)}  processed={processed}/{total}")
        )
        try:
            data = call_llm(batch, retries=args.max_retries)
        except Exception as e:
            log(c("red", f"   ✗ batch {b_idx} FAILED: {e}"))
            failed_items.extend([b["id"] for b in batch])
            continue

        items = data.get("items") if isinstance(data, dict) else None
        if not isinstance(items, list):
            log(c("red", f"   ✗ batch {b_idx}: invalid response shape (no items[]). Skipped."))
            failed_items.extend([b["id"] for b in batch])
            continue

        returned_ids = {it.get("id") for it in items}
        missing = [b["id"] for b in batch if b["id"] not in returned_ids]
        if missing:
            log(c("yellow", f"   ⚠ batch {b_idx}: missing {len(missing)} ids, retrying missing only"))
            try:
                id2batch = {b["id"]: b for b in batch}
                retry_payload = [
                    {"id": m, "scientific_name": id2batch[m]["scientific_name"], "common_name": id2batch[m]["common_name"]}
                    for m in missing
                ]
                retry_data = call_llm(retry_payload, retries=args.max_retries)
                retry_items = retry_data.get("items") if isinstance(retry_data, dict) else None
                if isinstance(retry_items, list):
                    items.extend(retry_items)
            except Exception as e:
                log(c("red", f"   ✗ retry failed: {e}"))

        all_results.extend(items)
        inserted = insert_aliases(dst, items)
        processed += inserted
        dt = time.time() - t0
        rate = processed / max(1e-6, (time.time() - overall_start))
        eta = (total - processed) / max(1e-6, rate)
        bar = progress_bar(processed, total, width=30)
        log(
            c("green", f"   ✓ +{inserted} rows")
            + c("dim", f"  ({dt:.1f}s, total {processed}/{total}, {rate:.2f}/s, ETA {eta:.0f}s)")
            + "  " + bar
        )

    dst.commit()
    cur = dst.cursor()
    cur.execute("SELECT COUNT(*) FROM mushroom_alias;")
    db_count = cur.fetchone()[0]
    dst.close()

    elapsed = time.time() - overall_start
    log(c("dim", "─" * 60))
    log("")
    log_step("Summary")
    log(f"  Unique species   : {total}")
    log(f"  Successfully proc: {len(all_results)}")
    log(f"  Failed/missing   : {len(failed_items)}")
    log(f"  Rows in DB       : {db_count}")
    log(f"  Elapsed          : {elapsed:.1f}s")
    if failed_items:
        sample = failed_items[:20]
        log(c("yellow", f"  Failed sample    : {sample}{' …' if len(failed_items) > 20 else ''}"))
        (Path(__file__).resolve().parent / "failed_ids.txt").write_text(
            "\n".join(failed_items), encoding="utf-8"
        )
        log(c("yellow", f"  → wrote failed_ids.txt ({len(failed_items)} ids). Run retry_failed.py next."))
    else:
        log(c("green", "  No failures ✔"))

    log("")
    log_step(c("green", f"✔ Done. alias_mushroom.db is ready at {DST_DB}"))


if __name__ == "__main__":
    main()
