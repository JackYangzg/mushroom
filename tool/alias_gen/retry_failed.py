#!/usr/bin/env python3
"""Retry the species that the main run dropped (network timeout / parse error).

Reads `failed_ids.txt` (one scientific_name per line) and re-queries the LLM.
Field names follow the task's spec: `scientific_name` / `common_name`.
"""
from __future__ import annotations

import json
import os
import re
import sqlite3
import sys
import time
import urllib.error
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
DST_DB = ROOT / "app/src/main/assets/alias_mushroom.db"
SPECIES_JSONL = Path(__file__).resolve().parent / "species.jsonl"
FAILED_FILE = Path(__file__).resolve().parent / "failed_ids.txt"

API_BASE: str = os.environ.get("ANTHROPIC_BASE_URL", "https://api.minimaxi.com/anthropic").rstrip("/")
API_KEY: str = os.environ.get("ANTHROPIC_AUTH_TOKEN", "")
MODEL: str = os.environ.get("ANTHROPIC_MODEL", "MiniMax-M3")

# Re-use the prompt template from generate_aliases.py verbatim.
src = (Path(__file__).resolve().parent / "generate_aliases.py").read_text(encoding="utf-8")
match = re.search(r'PROMPT_TEMPLATE\s*=\s*"""(.*?)"""', src, re.DOTALL)
PROMPT: str = match.group(1).strip() if match else ""


def call_llm(items: list[dict], retries: int = 4) -> dict:
    user_text = PROMPT.replace(
        "{BATCH_JSON}",
        json.dumps({"items": items}, ensure_ascii=False),
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
    last: Exception | None = None
    for attempt in range(1, retries + 1):
        try:
            with urllib.request.urlopen(req, timeout=180) as resp:
                payload = json.loads(resp.read().decode("utf-8"))
            text = "".join(b.get("text", "") for b in payload.get("content", []) if b.get("type") == "text")
            text = re.sub(r"^```(?:json)?\s*", "", text.strip())
            text = re.sub(r"\s*```$", "", text)
            return json.loads(text)
        except (urllib.error.URLError, urllib.error.HTTPError, json.JSONDecodeError, KeyError) as e:
            last = e
            wait = min(2 ** attempt, 30)
            print(f"   ⚠ attempt {attempt}/{retries} failed: {e}; retry in {wait}s", flush=True)
            time.sleep(wait)
    raise RuntimeError(f"failed: {last}")


def main() -> None:
    if not API_KEY:
        print("ERROR: ANTHROPIC_AUTH_TOKEN empty", file=sys.stderr)
        sys.exit(1)

    failed_ids: list[str] = [s.strip() for s in FAILED_FILE.read_text(encoding="utf-8").splitlines() if s.strip()]
    print(f"Retrying {len(failed_ids)} species…", flush=True)

    raw = SPECIES_JSONL.read_text(encoding="utf-8").strip()
    rows: list[dict] = (
        [json.loads(l) for l in raw.splitlines() if l.strip()]
        if raw and not raw.startswith("[")
        else (json.loads(raw) if raw else [])
    )
    sci_to_common: dict[str, str] = {
        (r.get("scientific_name") or "").strip(): (r.get("common_name") or "").strip()
        for r in rows
    }

    payload: list[dict] = [
        {"id": sid, "scientific_name": sid, "common_name": sci_to_common.get(sid, "")}
        for sid in failed_ids
    ]

    BATCH = 8
    out_items: list[dict] = []
    still_failed: list[str] = []
    for i in range(0, len(payload), BATCH):
        chunk = payload[i : i + BATCH]
        print(f"  retry {i + 1}-{i + len(chunk)} of {len(payload)}…", flush=True)
        try:
            data = call_llm(chunk)
            items = data.get("items", []) if isinstance(data, dict) else []
        except Exception as e:
            print(f"   ✗ chunk failed: {e}", flush=True)
            items = []
        returned_ids = {it.get("id") for it in items}
        out_items.extend(items)
        for c in chunk:
            if c["id"] not in returned_ids:
                still_failed.append(c["id"])

    print(f"  got {len(out_items)} items back, {len(still_failed)} still missing", flush=True)

    conn = sqlite3.connect(str(DST_DB))
    cur = conn.cursor()
    rows_to_insert: list[tuple] = []
    for it in out_items:
        sci = it.get("scientific_name", "")
        com = it.get("common_name", "")
        # Same fold-in as generate_aliases.py: append common_name (non-empty)
        # to the LLM-returned aliases, dedup, cap at 10.
        aliases: list[str] = []
        seen: set[str] = set()
        for src in (list(it.get("aliases", []) or []), [com]):
            for v in src:
                if not isinstance(v, str):
                    continue
                s = v.strip()
                if not s or s in seen:
                    continue
                seen.add(s)
                aliases.append(s)
                if len(aliases) >= 10:
                    break
            if len(aliases) >= 10:
                break
        rows_to_insert.append((sci, com, json.dumps(aliases, ensure_ascii=False)))
    cur.executemany(
        "INSERT INTO mushroom_alias (scientificName, commonName, aliases) VALUES (?, ?, ?);",
        rows_to_insert,
    )
    conn.commit()
    cur.execute("SELECT COUNT(*) FROM mushroom_alias;")
    print(f"  DB rows now: {cur.fetchone()[0]}", flush=True)
    conn.close()

    if still_failed:
        FAILED_FILE.write_text("\n".join(still_failed), encoding="utf-8")
        print(f"  updated failed_ids.txt with {len(still_failed)} remaining ids", flush=True)


if __name__ == "__main__":
    main()
