#!/usr/bin/env python3
"""检查 Kotlin / XML 中引用的 R.string.* / R.array.* / @string/* 是否都在 values*/strings.xml 里定义。"""
import re, sys, pathlib

root = pathlib.Path(__file__).resolve().parent.parent / "app" / "src" / "main"
used = {"string": set(), "array": set()}
for p in root.rglob("*.kt"):
    t = p.read_text(encoding="utf-8")
    for kind, name in re.findall(r"R\.(string|array)\.(\w+)", t):
        used[kind].add(name)
for p in list(root.rglob("*.xml")):
    if p.parent.name.startswith("values") and p.name == "strings.xml":
        continue
    t = p.read_text(encoding="utf-8")
    for kind, name in re.findall(r"@(string|array)/(\w+)", t):
        used[kind].add(name)

def defined(folder):
    out = {"string": set(), "array": set()}
    f = root / "res" / folder / "strings.xml"
    if not f.exists():
        return out
    t = f.read_text(encoding="utf-8")
    out["string"] = set(re.findall(r'<string name="(\w+)"', t))
    out["array"] = set(re.findall(r'<string-array name="(\w+)"', t))
    return out

base = defined("values")
bad = 0
for kind in ("string", "array"):
    missing = sorted(used[kind] - base[kind])
    unused = sorted(base[kind] - used[kind])
    if missing:
        bad += 1
        print(f"MISSING {kind} ({len(missing)}):")
        for m in missing: print("  ", m)
    if unused and "--unused" in sys.argv:
        print(f"UNUSED {kind} ({len(unused)}):", ", ".join(unused))
en = defined("values-en")
if (root / "res" / "values-en").exists():
    for kind in ("string", "array"):
        miss = sorted(base[kind] - en[kind])
        if miss: print(f"EN lacks {len(miss)} {kind}s")
sys.exit(1 if bad else 0)
