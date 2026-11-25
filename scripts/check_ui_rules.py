#!/usr/bin/env python3
"""静态检查 Compose UI 与桌面小组件布局中的常见排版与设计规范红线。"""

from __future__ import annotations

import pathlib
import re
import sys
import xml.sax
import xml.sax.handler

ROOT = pathlib.Path(__file__).resolve().parent.parent
UI_DIR = ROOT / "app" / "src" / "main" / "java" / "app" / "goke" / "ui"
LAYOUT_DIR = ROOT / "app" / "src" / "main" / "res" / "layout"

KT_OPT_OUT = "// ui-lint-ok:"
XML_OPT_OUT = "<!-- ui-lint-ok:"


def has_opt_out(lines: list[str], line_no: int, marker: str) -> bool:
    idx = line_no - 1
    if 0 <= idx < len(lines) and marker in lines[idx]:
        return True
    if idx - 1 >= 0 and marker in lines[idx - 1]:
        return True
    return False


def find_balanced_parens(text: str, open_paren_idx: int) -> int | None:
    """从 '(' 位置起扫描匹配的 ')'，跳过双引号字符串与注释。"""
    depth = 0
    i = open_paren_idx
    n = len(text)
    in_str = False
    while i < n:
        ch = text[i]
        if in_str:
            if ch == "\\" and i + 1 < n:
                i += 2
                continue
            if ch == '"':
                in_str = False
        else:
            if ch == '"':
                in_str = True
            elif ch == "/" and i + 1 < n and text[i + 1] == "/":
                nl = text.find("\n", i)
                if nl == -1:
                    return None
                i = nl + 1
                continue
            elif ch == "(" :
                depth += 1
            elif ch == ")":
                depth -= 1
                if depth == 0:
                    return i + 1
        i += 1
    return None


def iter_calls(text: str, names: tuple[str, ...]):
    pattern = re.compile(r"\b(" + "|".join(names) + r")\s*\(")
    for m in pattern.finditer(text):
        open_idx = m.end() - 1
        end_idx = find_balanced_parens(text, open_idx)
        if end_idx is None:
            continue
        yield m.group(1), m.start(), text[m.start() : end_idx]


def check_kotlin_file(path: pathlib.Path, violations: list[tuple[str, str, int, str]]) -> None:
    rel = path.relative_to(ROOT).as_posix()
    text = path.read_text(encoding="utf-8")
    lines = text.splitlines()

    # 1. no-fixed-text-width & 3. no-unellipsized-constrained-single-line-text
    for name, start, call in iter_calls(text, ("Column", "Row", "Text")):
        call_start_line = text[:start].count("\n") + 1
        for m in re.finditer(r"\.(width|requiredWidth)\((\d+)\.dp\)", call):
            dp_val = int(m.group(2))
            if dp_val < 16:
                continue
            line_no = text[: start + m.start()].count("\n") + 1
            if not has_opt_out(lines, line_no, KT_OPT_OUT):
                violations.append(
                    (
                        "no-fixed-text-width",
                        rel,
                        line_no,
                        f"{name} 使用固定 .{m.group(1)}({dp_val}.dp)，大字号或宽字体下易裁切；请改用 .widthIn(min = {dp_val}.dp)",
                    )
                )

        if name == "Text" and re.search(r"\bmaxLines\s*=\s*1\b", call) and "overflow" not in call:
            if re.search(r"(\bweight\(|fillMaxWidth|\.width\()", call):
                if not has_opt_out(lines, call_start_line, KT_OPT_OUT):
                    violations.append(
                        (
                            "no-unellipsized-constrained-single-line-text",
                            rel,
                            call_start_line,
                            "受限宽度的单行 Text(maxLines = 1) 缺少 overflow = TextOverflow.Ellipsis",
                        )
                    )

    # 4. no-bare-clickable
    if path.name != "Kit.kt":
        for i, line in enumerate(lines, 1):
            stripped = line.lstrip()
            if stripped.startswith("//") or stripped.startswith("*"):
                continue
            if re.search(r"(?<!mint)(?<!combined)\.clickable\s*(\(|\{)", line):
                if not has_opt_out(lines, i, KT_OPT_OUT):
                    violations.append(
                        (
                            "no-bare-clickable",
                            rel,
                            i,
                            "禁止使用裸 .clickable，请改用 mintClickable / mintPressable",
                        )
                    )

    # 5. no-hardcoded-color
    if path.name != "Theme.kt":
        for i, line in enumerate(lines, 1):
            stripped = line.lstrip()
            if stripped.startswith("//") or stripped.startswith("*"):
                continue
            if re.search(r"\bColor\(0x[0-9A-Fa-f]{8}\)", line):
                if not has_opt_out(lines, i, KT_OPT_OUT):
                    violations.append(
                        (
                            "no-hardcoded-color",
                            rel,
                            i,
                            "禁止硬编码 Color(0xFF...)，请使用 MaterialTheme.colorScheme / MintTheme.colors",
                        )
                    )


class _WidgetXmlHandler(xml.sax.handler.ContentHandler):
    def __init__(self, rel: str, lines: list[str], violations: list[tuple[str, str, int, str]]) -> None:
        super().__init__()
        self.rel = rel
        self.lines = lines
        self.violations = violations
        self.locator: xml.sax.xmlreader.Locator | None = None
        # [tag, layout_width_str, line_no, has_textview_descendant]
        self.stack: list[list[object]] = []

    def setDocumentLocator(self, locator: xml.sax.xmlreader.Locator) -> None:
        self.locator = locator

    def startElement(self, name: str, attrs: xml.sax.xmlreader.Attributes) -> None:
        line_no = self.locator.getLineNumber() if self.locator else 1
        width_attr = attrs.get("android:layout_width", "")
        if name == "TextView":
            for frame in self.stack:
                if frame[0] == "LinearLayout":
                    frame[3] = True
        self.stack.append([name, width_attr, line_no, name == "TextView"])

    def endElement(self, name: str) -> None:
        frame = self.stack.pop()
        tag, width_attr, line_no, has_tv = frame[0], str(frame[1]), int(frame[2]), bool(frame[3])
        if tag not in ("TextView", "LinearLayout") or not has_tv:
            return
        m = re.fullmatch(r"(\d+)dp", width_attr.strip())
        if m and int(m.group(1)) > 0:
            if not has_opt_out(self.lines, line_no, XML_OPT_OUT):
                dp_val = int(m.group(1))
                self.violations.append(
                    (
                        "no-fixed-xml-text-width",
                        self.rel,
                        line_no,
                        f"<{tag}> 含文本且使用固定 android:layout_width=\"{dp_val}dp\"，请改用 wrap_content + android:minWidth",
                    )
                )


def check_widget_xml(path: pathlib.Path, violations: list[tuple[str, str, int, str]]) -> None:
    rel = path.relative_to(ROOT).as_posix()
    text = path.read_text(encoding="utf-8")
    lines = text.splitlines()
    parser = xml.sax.make_parser()
    parser.setContentHandler(_WidgetXmlHandler(rel, lines, violations))
    parser.parse(str(path))


def main() -> int:
    violations: list[tuple[str, str, int, str]] = []
    kt_files = sorted(UI_DIR.rglob("*.kt"))
    xml_files = sorted(LAYOUT_DIR.glob("widget_*.xml"))
    for p in kt_files:
        check_kotlin_file(p, violations)
    for p in xml_files:
        check_widget_xml(p, violations)

    if violations:
        for rule, rel, line_no, msg in violations:
            print(f"[{rule}] {rel}:{line_no}: {msg}")
        print(f"\nFound {len(violations)} UI rule violation(s).")
        return 1

    print(f"UI rules OK ({len(kt_files)} Kotlin files, {len(xml_files)} widget XMLs checked).")
    return 0


if __name__ == "__main__":
    sys.exit(main())
