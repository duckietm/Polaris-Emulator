#!/usr/bin/env python3
"""Generate the runtime table/column contract from the canonical Polaris database."""

from __future__ import annotations

import argparse
import json
import re
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "Database" / "Default Database" / "FullDatabase.sql"
OUTPUT = ROOT / "Emulator" / "src" / "main" / "resources" / "db" / "schema-contract.json"
CREATE_TABLE = re.compile(
    r"CREATE\s+TABLE(?:\s+IF\s+NOT\s+EXISTS)?\s+`([^`]+)`\s*\((.*?)\)\s*"
    r"(?:ENGINE\s*=\s*[^;]+)?;",
    re.IGNORECASE | re.DOTALL,
)
COLUMN = re.compile(r"^\s*`([^`]+)`\s+", re.MULTILINE)
OPTIONAL_CANONICAL_COLUMNS = {
    # Runtime addresses pet actions by pet_type and never reads the dump-only
    # surrogate identifier. Legacy Polaris databases legitimately omit it.
    ("pet_actions", "id"),
}


def generate() -> str:
    sql = SOURCE.read_text(encoding="utf-8")
    tables: dict[str, list[str]] = {}
    for match in CREATE_TABLE.finditer(sql):
        table = match.group(1).lower()
        columns = [
            name.lower()
            for name in COLUMN.findall(match.group(2))
            if (table, name.lower()) not in OPTIONAL_CANONICAL_COLUMNS
        ]
        if table in tables:
            raise SystemExit(f"duplicate CREATE TABLE: {table}")
        if not columns:
            raise SystemExit(f"table has no parsed columns: {table}")
        tables[table] = columns

    if len(tables) != 146:
        raise SystemExit(f"expected 146 tables, parsed {len(tables)}")

    payload = {
        "schemaVersion": 1,
        "source": "Database/Default Database/FullDatabase.sql",
        "tables": dict(sorted(tables.items())),
    }
    return json.dumps(payload, indent=2, ensure_ascii=True) + "\n"


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    generated = generate()
    if args.check:
        current = OUTPUT.read_text(encoding="utf-8") if OUTPUT.exists() else ""
        if current != generated:
            raise SystemExit("schema contract is stale; run scripts/generate_schema_contract.py")
        print(f"schema contract is current: {OUTPUT}")
        return

    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    OUTPUT.write_text(generated, encoding="utf-8", newline="\n")
    print(OUTPUT)


if __name__ == "__main__":
    main()
