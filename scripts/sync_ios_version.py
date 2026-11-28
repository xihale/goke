#!/usr/bin/env python3
"""Sync iOS app & widget version numbers from Git SemVer tags (mirroring app/build.gradle.kts).

Supports both iOS-specific tags (`ios-v<semver>`) and repository tags (`v<semver>`).

Version mapping:
  - CFBundleShortVersionString / MARKETING_VERSION: MAJOR.MINOR.PATCH (Apple numeric requirement)
  - CFBundleVersion / CURRENT_PROJECT_VERSION: monotonic integer matching Android computeVersionCode
  - GokeDisplayVersion: full SemVer string (e.g. "0.0.1-beta.1"; pass --include-commits for "+N")

Usage:
  python3 scripts/sync_ios_version.py                       # update Info.plist files and ios/project.yml
  python3 scripts/sync_ios_version.py --write               # same as default
  python3 scripts/sync_ios_version.py --check               # exit 0 if all files are in sync, 1 otherwise
  python3 scripts/sync_ios_version.py --print               # print resolved version info as JSON
  python3 scripts/sync_ios_version.py --tag ios-v0.0.1-beta.1 --print
  python3 scripts/sync_ios_version.py --include-commits     # append +<commits_after> when post-tag
"""

from __future__ import annotations

import argparse
import json
import plistlib
import re
import subprocess
import sys
from dataclasses import dataclass
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent

INFO_PLISTS = [
    REPO_ROOT / "ios" / "GokeApp" / "Resources" / "Info.plist",
    REPO_ROOT / "ios" / "GokeWidgetExtension" / "Resources" / "Info.plist",
]
PROJECT_YML = REPO_ROOT / "ios" / "project.yml"

DESCRIBE_RE = re.compile(
    r"^(?:ios-)?v?(\d+)\.(\d+)\.(\d+)(?:-([0-9A-Za-z.-]+?))?(?:-(\d+)-g[0-9a-f]+)?(?:-dirty)?$"
)
PRE_RE = re.compile(r"^(alpha|beta|rc)[.-]?(\d+)?", re.IGNORECASE)


@dataclass(frozen=True)
class IosVersion:
    short_version: str      # CFBundleShortVersionString / MARKETING_VERSION (MAJOR.MINOR.PATCH)
    build_number: str       # CFBundleVersion / CURRENT_PROJECT_VERSION (monotonic int)
    display_version: str    # GokeDisplayVersion (full SemVer without -dirty clutter)
    git_describe: str       # raw git describe output or explicit tag


def compute_version_code(
    major: int,
    minor: int,
    patch: int,
    pre_release: str | None,
    commits_after: int,
) -> int:
    """Mirror app/build.gradle.kts computeVersionCode exactly."""
    base = major * 100_000_000 + minor * 1_000_000 + patch * 10_000
    if not pre_release or not pre_release.strip():
        return base + 9_000 + max(0, min(commits_after, 999))
    pre_match = PRE_RE.match(pre_release)
    stage_name = (pre_match.group(1).lower() if pre_match and pre_match.group(1) else "")
    stage = {"alpha": 1, "beta": 2, "rc": 3}.get(stage_name, 4)
    pre_num_raw = int(pre_match.group(2)) if pre_match and pre_match.group(2) else 0
    pre_num = max(0, min(pre_num_raw, 99))
    return base + stage * 1_000 + pre_num * 10 + max(0, min(commits_after, 9))


def _git_describe(repo_root: Path, match_pattern: str) -> str:
    try:
        proc = subprocess.run(
            ["git", "describe", "--tags", "--match", match_pattern, "--long", "--always"],
            cwd=repo_root,
            capture_output=True,
            text=True,
            timeout=10,
            check=False,
        )
        return proc.stdout.strip() if proc.returncode == 0 else ""
    except OSError:
        return ""


def parse_version_string(
    raw: str,
    include_commits: bool = False,
) -> IosVersion | None:
    match = DESCRIBE_RE.match(raw.strip())
    if not match:
        return None

    major = int(match.group(1))
    minor = int(match.group(2))
    patch = int(match.group(3))
    pre = match.group(4) or None
    commits_after = int(match.group(5)) if match.group(5) else 0

    short_version = f"{major}.{minor}.{patch}"
    base_display = f"{short_version}-{pre}" if pre else short_version
    display_version = (
        f"{base_display}+{commits_after}"
        if include_commits and commits_after > 0
        else base_display
    )
    code = compute_version_code(major, minor, patch, pre, commits_after)

    return IosVersion(
        short_version=short_version,
        build_number=str(code),
        display_version=display_version,
        git_describe=raw.strip(),
    )


def resolve_version(
    repo_root: Path = REPO_ROOT,
    include_commits: bool = False,
    explicit_tag: str | None = None,
) -> IosVersion:
    if explicit_tag:
        parsed = parse_version_string(explicit_tag, include_commits=include_commits)
        if parsed is not None:
            return parsed

    last_describe = ""
    for pattern in ("ios-v[0-9]*", "v[0-9]*"):
        describe = _git_describe(repo_root, pattern)
        if describe:
            last_describe = describe
            parsed = parse_version_string(describe, include_commits=include_commits)
            if parsed is not None:
                return parsed

    code = compute_version_code(0, 0, 1, "beta.1", 0)
    return IosVersion(
        short_version="0.0.1",
        build_number=str(code),
        display_version="0.0.1-beta.1",
        git_describe=explicit_tag or last_describe or "fallback",
    )


def sync_plist(path: Path, ver: IosVersion, write: bool) -> list[str]:
    with path.open("rb") as f:
        data = plistlib.load(f)

    expected = {
        "CFBundleShortVersionString": ver.short_version,
        "CFBundleVersion": ver.build_number,
        "GokeDisplayVersion": ver.display_version,
    }
    diffs: list[str] = []
    for key, val in expected.items():
        cur = data.get(key)
        if cur != val:
            diffs.append(f"{path.relative_to(REPO_ROOT)}: {key} {cur!r} -> {val!r}")
            data[key] = val

    if diffs and write:
        with path.open("wb") as f:
            plistlib.dump(data, f, fmt=plistlib.FMT_XML, sort_keys=True)
    return diffs


def sync_project_yml(path: Path, ver: IosVersion, write: bool) -> list[str]:
    text = path.read_text(encoding="utf-8")
    new_text = text

    replacements = [
        (
            r'^(\s*MARKETING_VERSION:\s*)"[^"]*"',
            rf'\1"{ver.short_version}"',
            "MARKETING_VERSION",
            ver.short_version,
        ),
        (
            r'^(\s*CURRENT_PROJECT_VERSION:\s*)"[^"]*"',
            rf'\1"{ver.build_number}"',
            "CURRENT_PROJECT_VERSION",
            ver.build_number,
        ),
    ]

    diffs: list[str] = []
    for pattern, repl, key, expected_val in replacements:
        m = re.search(pattern, new_text, flags=re.MULTILINE)
        if not m:
            raise RuntimeError(f"Could not find {key} in {path}")
        updated, count = re.subn(pattern, repl, new_text, count=1, flags=re.MULTILINE)
        if count != 1:
            raise RuntimeError(f"Failed to update {key} in {path}")
        if updated != new_text:
            diffs.append(f"{path.relative_to(REPO_ROOT)}: {key} -> {expected_val!r}")
            new_text = updated

    if diffs and write:
        path.write_text(new_text, encoding="utf-8")
    return diffs


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Sync iOS version numbers from Git SemVer tags.")
    mode = parser.add_mutually_exclusive_group()
    mode.add_argument("--write", action="store_true", help="Write synced versions to files (default).")
    mode.add_argument("--check", action="store_true", help="Check if files are in sync without modifying.")
    mode.add_argument("--print", dest="print_only", action="store_true", help="Print resolved version as JSON.")
    parser.add_argument(
        "--tag",
        default=None,
        help="Explicit SemVer tag (e.g. ios-v0.0.1-beta.1) to resolve instead of git describe.",
    )
    parser.add_argument(
        "--include-commits",
        action="store_true",
        help="Append +<commits_after> to GokeDisplayVersion when HEAD is past the latest tag.",
    )
    args = parser.parse_args(argv)

    ver = resolve_version(include_commits=args.include_commits, explicit_tag=args.tag)
    if args.print_only:
        print(
            json.dumps(
                {
                    "CFBundleShortVersionString": ver.short_version,
                    "CFBundleVersion": ver.build_number,
                    "GokeDisplayVersion": ver.display_version,
                    "gitDescribe": ver.git_describe,
                },
                indent=2,
            )
        )
        return 0

    write = not args.check
    all_diffs: list[str] = []
    for plist_path in INFO_PLISTS:
        all_diffs.extend(sync_plist(plist_path, ver, write=write))
    all_diffs.extend(sync_project_yml(PROJECT_YML, ver, write=write))

    if args.check:
        if all_diffs:
            print("iOS version files out of sync:", file=sys.stderr)
            for d in all_diffs:
                print(f"  - {d}", file=sys.stderr)
            return 1
        print(
            f"OK: iOS versions in sync "
            f"(short={ver.short_version}, build={ver.build_number}, display={ver.display_version})"
        )
        return 0

    if all_diffs:
        print(
            f"Updated iOS versions "
            f"(short={ver.short_version}, build={ver.build_number}, display={ver.display_version}):"
        )
        for d in all_diffs:
            print(f"  - {d}")
    else:
        print(
            f"Already up to date "
            f"(short={ver.short_version}, build={ver.build_number}, display={ver.display_version})"
        )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
