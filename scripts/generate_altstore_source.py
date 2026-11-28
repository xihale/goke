#!/usr/bin/env python3
"""Generate an AltStore / SideStore Source v2 JSON manifest (landing/altstore.json).

Stdlib-only Python script that keeps version / buildVersion strictly aligned with
scripts/sync_ios_version.py and extracts bundle ID & permissions from Info.plist /
GokeApp.entitlements.

Usage:
  # Rebuild from published GitHub Releases (ios-v* tags with .ipa assets)
  python3 scripts/generate_altstore_source.py --from-github-releases

  # Upsert a specific release into existing landing/altstore.json (CI-safe)
  python3 scripts/generate_altstore_source.py \
    --add-release \
    --tag ios-v0.0.1-beta.2 \
    --ipa-url https://github.com/xihale/goke/releases/download/ios-v0.0.1-beta.2/goke-v0.0.1-beta.2-ios-unsigned.ipa \
    --ipa-size 5564188 \
    --notes "Bug fixes and improvements."

  # Combine both: fetch GitHub Releases AND upsert the just-published release
  python3 scripts/generate_altstore_source.py --from-github-releases --add-release ...
"""

from __future__ import annotations

import argparse
import json
import os
import plistlib
import sys
import urllib.error
import urllib.request
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

SCRIPT_DIR = Path(__file__).resolve().parent
REPO_ROOT = SCRIPT_DIR.parent
if str(SCRIPT_DIR) not in sys.path:
    sys.path.insert(0, str(SCRIPT_DIR))

from sync_ios_version import parse_version_string  # noqa: E402

DEFAULT_REPO = "xihale/goke"
DEFAULT_OUTPUT = REPO_ROOT / "landing" / "altstore.json"
INFO_PLIST_PATH = REPO_ROOT / "ios" / "GokeApp" / "Resources" / "Info.plist"
ENTITLEMENTS_PATH = REPO_ROOT / "ios" / "GokeApp" / "Resources" / "GokeApp.entitlements"

CANONICAL_WEBSITE = "https://goke.xeed.ink/"
CANONICAL_ICON_URL = (
    "https://raw.githubusercontent.com/xihale/goke/main/ios/GokeApp/Resources/AppIcon.png"
)
TINT_COLOR = "#2E776C"
MIN_OS_VERSION = "17.0"

SCREENSHOT_URLS = [
    "https://goke.xeed.ink/assets/shot-timetable.webp",
    "https://goke.xeed.ink/assets/shot-today.webp",
    "https://goke.xeed.ink/assets/shot-calendar.webp",
    "https://goke.xeed.ink/assets/shot-todo.webp",
]


def load_bundle_metadata() -> tuple[str, dict[str, Any]]:
    bundle_id = "app.goke.ios"
    privacy: dict[str, str] = {}
    entitlements: list[str] = []

    if INFO_PLIST_PATH.is_file():
        with INFO_PLIST_PATH.open("rb") as f:
            plist = plistlib.load(f)
        if isinstance(plist.get("CFBundleIdentifier"), str):
            bundle_id = plist["CFBundleIdentifier"]
        for key in sorted(plist.keys()):
            if key.startswith("NS") and key.endswith("UsageDescription"):
                val = plist[key]
                if isinstance(val, str) and val.strip():
                    privacy[key] = val.strip()

    if ENTITLEMENTS_PATH.is_file():
        with ENTITLEMENTS_PATH.open("rb") as f:
            ent = plistlib.load(f)
        entitlements = sorted(str(k) for k in ent.keys())

    return bundle_id, {
        "entitlements": entitlements,
        "privacy": privacy,
    }


def format_release_notes(tag: str, display_version: str, raw_notes: str | None) -> str:
    body = (raw_notes or "").strip()
    header = f"版本 {display_version}（{tag}）"
    if not body:
        return f"{header}\n未签名 IPA，支持 SideStore / AltStore 侧载与源内一键更新。"
    if display_version in body or tag in body:
        return body
    return f"{header}\n\n{body}"


def build_version_entry(
    *,
    tag: str,
    ipa_url: str,
    ipa_size: int,
    date: str,
    notes: str | None,
) -> dict[str, Any]:
    parsed = parse_version_string(tag)
    if parsed is None:
        raise ValueError(f"无法从 tag 解析 iOS 版本号: {tag!r}")

    return {
        "version": parsed.short_version,
        "buildVersion": parsed.build_number,
        "date": date,
        "localizedDescription": format_release_notes(tag, parsed.display_version, notes),
        "downloadURL": ipa_url,
        "size": int(ipa_size),
        "minOSVersion": MIN_OS_VERSION,
    }


def pick_ipa_asset(assets: list[dict[str, Any]]) -> dict[str, Any] | None:
    for asset in assets:
        name = asset.get("name", "")
        url = asset.get("browser_download_url", "")
        if isinstance(name, str) and name.lower().endswith(".ipa") and isinstance(url, str) and url:
            return asset
    return None


def fetch_github_ios_releases(repo: str) -> list[dict[str, Any]]:
    url = f"https://api.github.com/repos/{repo}/releases?per_page=100"
    headers = {
        "Accept": "application/vnd.github+json",
        "User-Agent": "goke-altstore-source-generator",
    }
    token = os.environ.get("GH_TOKEN") or os.environ.get("GITHUB_TOKEN")
    if token:
        headers["Authorization"] = f"Bearer {token}"

    req = urllib.request.Request(url, headers=headers)
    try:
        with urllib.request.urlopen(req, timeout=20) as resp:
            releases = json.loads(resp.read().decode("utf-8"))
    except urllib.error.HTTPError as exc:
        detail = exc.read().decode("utf-8", errors="replace")
        raise RuntimeError(f"GitHub API 请求失败 ({exc.code}): {detail}") from exc
    except urllib.error.URLError as exc:
        raise RuntimeError(f"GitHub API 网络请求失败: {exc}") from exc

    if not isinstance(releases, list):
        raise RuntimeError("GitHub API 返回格式异常（预期 list）")

    versions: list[dict[str, Any]] = []
    for rel in releases:
        if not isinstance(rel, dict) or rel.get("draft"):
            continue
        tag = rel.get("tag_name")
        if not isinstance(tag, str) or not tag.startswith("ios-v"):
            continue
        assets = rel.get("assets")
        if not isinstance(assets, list):
            continue
        ipa = pick_ipa_asset(assets)
        if ipa is None:
            continue
        published_at = rel.get("published_at") or rel.get("created_at") or ""
        if not published_at:
            published_at = datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
        try:
            entry = build_version_entry(
                tag=tag,
                ipa_url=ipa["browser_download_url"],
                ipa_size=int(ipa.get("size", 0)),
                date=published_at,
                notes=rel.get("body"),
            )
        except ValueError:
            continue
        versions.append(entry)

    return versions


def load_existing_versions(output_path: Path) -> list[dict[str, Any]]:
    if not output_path.is_file():
        return []
    try:
        data = json.loads(output_path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError):
        return []
    apps = data.get("apps")
    if not isinstance(apps, list) or not apps:
        return []
    versions = apps[0].get("versions")
    return [v for v in versions if isinstance(v, dict)] if isinstance(versions, list) else []


def merge_and_sort_versions(versions: list[dict[str, Any]]) -> list[dict[str, Any]]:
    by_key: dict[tuple[str, str], dict[str, Any]] = {}
    for entry in versions:
        ver = str(entry.get("version", ""))
        build = str(entry.get("buildVersion", ""))
        if not ver or not build:
            continue
        by_key[(ver, build)] = entry

    def sort_key(item: dict[str, Any]) -> tuple[int, str]:
        try:
            build_int = int(str(item.get("buildVersion", "0")))
        except ValueError:
            build_int = 0
        return (build_int, str(item.get("date", "")))

    return sorted(by_key.values(), key=sort_key, reverse=True)


def build_source_manifest(versions: list[dict[str, Any]]) -> dict[str, Any]:
    bundle_id, app_permissions = load_bundle_metadata()
    sorted_versions = merge_and_sort_versions(versions)

    return {
        "name": "Goke",
        "identifier": "app.goke.source",
        "subtitle": "广工离线课表与日程",
        "description": (
            "Goke（工课）官方 SideStore / AltStore 源。"
            "添加此源后，可在 SideStore / AltStore 内一键安装并持续接收 iOS 版本更新。"
        ),
        "iconURL": CANONICAL_ICON_URL,
        "website": CANONICAL_WEBSITE,
        "tintColor": TINT_COLOR,
        "featuredApps": [bundle_id],
        "apps": [
            {
                "name": "Goke",
                "bundleIdentifier": bundle_id,
                "developerName": "xihale",
                "subtitle": "为广工而写的离线课表与日程",
                "localizedDescription": (
                    "Goke（工课）是专为广东工业大学设计的离线课表与日程应用，"
                    "支持教务课表同步、空教室查询、考试成绩、待办事项、桌面小组件与多端同步。"
                    "无广告、不接受捐款，极端非商业许可（Goke-NC-SA-2.0）。"
                ),
                "iconURL": CANONICAL_ICON_URL,
                "tintColor": TINT_COLOR,
                "category": "utilities",
                "screenshotURLs": SCREENSHOT_URLS,
                "appPermissions": app_permissions,
                "versions": sorted_versions,
            }
        ],
        "news": [],
    }


def parse_args(argv: list[str]) -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Generate AltStore / SideStore Source v2 JSON (landing/altstore.json)."
    )
    parser.add_argument(
        "--output",
        type=Path,
        default=DEFAULT_OUTPUT,
        help=f"Output JSON file path (default: {DEFAULT_OUTPUT})",
    )
    parser.add_argument(
        "--repo",
        default=DEFAULT_REPO,
        help=f"GitHub repository in owner/name format (default: {DEFAULT_REPO})",
    )
    parser.add_argument(
        "--from-github-releases",
        action="store_true",
        help="Fetch ios-v* releases with .ipa assets from GitHub Releases API.",
    )
    parser.add_argument(
        "--add-release",
        action="store_true",
        help="Add or update a single release entry from CLI flags.",
    )
    parser.add_argument("--tag", help="Release tag (e.g. ios-v0.0.1-beta.1)")
    parser.add_argument("--ipa-url", help="Direct HTTPS download URL for the .ipa asset")
    parser.add_argument("--ipa-size", type=int, help="File size of the .ipa asset in bytes")
    parser.add_argument(
        "--date",
        help="ISO-8601 release timestamp (default: current UTC time)",
    )
    parser.add_argument("--notes", help="Release notes text")
    parser.add_argument(
        "--notes-file",
        type=Path,
        help="Read release notes text from file",
    )
    return parser.parse_args(argv)


def main(argv: list[str] | None = None) -> int:
    args = parse_args(argv if argv is not None else sys.argv[1:])

    if not args.from_github_releases and not args.add_release:
        print(
            "Error: specify at least one of --from-github-releases or --add-release",
            file=sys.stderr,
        )
        return 2

    versions: list[dict[str, Any]] = []

    if args.from_github_releases:
        versions.extend(fetch_github_ios_releases(args.repo))
    else:
        versions.extend(load_existing_versions(args.output))

    if args.add_release:
        missing = [
            flag
            for flag, val in (
                ("--tag", args.tag),
                ("--ipa-url", args.ipa_url),
                ("--ipa-size", args.ipa_size),
            )
            if val is None or val == ""
        ]
        if missing:
            print(
                f"Error: --add-release requires {', '.join(missing)}",
                file=sys.stderr,
            )
            return 2

        notes = args.notes
        if args.notes_file is not None and args.notes_file.is_file():
            notes = args.notes_file.read_text(encoding="utf-8")

        release_date = args.date or datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
        versions.append(
            build_version_entry(
                tag=args.tag,
                ipa_url=args.ipa_url,
                ipa_size=args.ipa_size,
                date=release_date,
                notes=notes,
            )
        )

    manifest = build_source_manifest(versions)
    rendered = json.dumps(manifest, indent=2, ensure_ascii=False) + "\n"

    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(rendered, encoding="utf-8")
    print(
        f"Generated {args.output} with {len(manifest['apps'][0]['versions'])} version(s).",
        file=sys.stderr,
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
