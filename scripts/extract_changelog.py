#!/usr/bin/env python3
"""Extract release notes for a given git tag from CHANGELOG.md (or annotated tag fallback)."""

import argparse
import pathlib
import re
import subprocess
import sys

HEADING_RE = re.compile(r"^##\s+\[?v?([0-9]+\.[0-9]+\.[0-9]+(?:-[0-9A-Za-z.-]+)?)\]?(?:\s|$)")


def normalize_version(tag: str) -> str:
    return tag.strip().lstrip("vV")


def extract_from_changelog(changelog_path: pathlib.Path, version: str) -> str | None:
    if not changelog_path.is_file():
        return None
    lines = changelog_path.read_text(encoding="utf-8").splitlines()
    collecting = False
    collected: list[str] = []
    for line in lines:
        match = HEADING_RE.match(line.strip())
        if match:
            if collecting:
                break
            if match.group(1) == version:
                collecting = True
                continue
        elif collecting:
            collected.append(line)
    if not collecting:
        return None
    body = "\n".join(collected).strip()
    return body or None


def extract_from_annotated_tag(tag: str) -> str | None:
    kind = subprocess.run(
        ["git", "cat-file", "-t", tag],
        capture_output=True,
        text=True,
        check=False,
    )
    if kind.returncode != 0 or kind.stdout.strip() != "tag":
        return None
    msg = subprocess.run(
        ["git", "tag", "-l", "--format=%(contents)", tag],
        capture_output=True,
        text=True,
        check=False,
    )
    if msg.returncode != 0:
        return None
    body = msg.stdout.strip()
    return body or None


def main() -> int:
    parser = argparse.ArgumentParser(description="Extract release notes for a version tag.")
    parser.add_argument("tag", help="Git tag name (e.g. v0.0.1-beta.4)")
    parser.add_argument(
        "--changelog",
        default="CHANGELOG.md",
        help="Path to CHANGELOG.md (default: CHANGELOG.md)",
    )
    parser.add_argument(
        "--out",
        help="Optional output file path; prints to stdout when omitted",
    )
    parser.add_argument(
        "--allow-tag-fallback",
        action="store_true",
        help="Fall back to annotated git tag message if version is absent in CHANGELOG.md",
    )
    args = parser.parse_args()

    version = normalize_version(args.tag)
    notes = extract_from_changelog(pathlib.Path(args.changelog), version)
    source = args.changelog

    if notes is None and args.allow_tag_fallback:
        notes = extract_from_annotated_tag(args.tag)
        source = f"annotated tag {args.tag}"

    if not notes:
        print(
            f"::error::未在 {args.changelog} 中找到 {args.tag}（{version}）的更新日志，请在发版前补充后重新打标。",
            file=sys.stderr,
        )
        return 1

    if args.out:
        out_path = pathlib.Path(args.out)
        out_path.parent.mkdir(parents=True, exist_ok=True)
        out_path.write_text(notes + "\n", encoding="utf-8")
        print(f"Extracted release notes for {args.tag} from {source} -> {out_path}")
    else:
        print(notes)
    return 0


if __name__ == "__main__":
    sys.exit(main())
