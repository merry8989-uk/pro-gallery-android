#!/usr/bin/env python3
"""
Static checks that catch the mistakes which have actually reached CI in this
project, before a build is spent on them.

Run from the repository root:

    python3 tools/check_kotlin.py

Checks
  1. Brace, parenthesis and bracket balance per file (comments and strings ignored).
  2. Every `Icons.Filled.*` / `Icons.AutoMirrored.Filled.*` use has its import.
  3. No duplicate imports.
  3c. At most one `companion object` per file — Kotlin rejects a second one.
  3b. Every `import` appears before any declaration — Kotlin rejects an import
      that follows code ("imports are only allowed in the beginning of file").
  4. Every project type a file references is imported, declared locally, or in the
     same package — the check that catches a missing `import` the Kotlin compiler
     would only report after a long build.

Exit code is non-zero when anything is found.
"""

import os
import re
import sys
import pathlib

ROOT = os.path.join("app", "src", "main")


def strip_comments_and_strings(src: str) -> str:
    out = []
    i, n = 0, len(src)
    while i < n:
        c = src[i]
        if c == "/" and i + 1 < n and src[i + 1] == "/":
            j = src.find("\n", i)
            i = n if j < 0 else j
        elif c == "/" and i + 1 < n and src[i + 1] == "*":
            j = src.find("*/", i + 2)
            i = n if j < 0 else j + 2
        elif c == '"':
            i += 1
            while i < n and src[i] != '"':
                if src[i] == "\\":
                    i += 1
                i += 1
            i += 1
        elif c == "'":
            i += 1
            while i < n and src[i] != "'":
                if src[i] == "\\":
                    i += 1
                i += 1
            i += 1
        else:
            out.append(c)
            i += 1
    return "".join(out)


def kotlin_files(root: str):
    for dirpath, _, files in os.walk(root):
        for f in files:
            if f.endswith(".kt"):
                yield os.path.join(dirpath, f)


def main() -> int:
    problems = []

    # 1 + 2 + 3
    for path in kotlin_files(ROOT):
        raw = open(path).read()
        code = strip_comments_and_strings(raw)
        name = os.path.basename(path)

        for opener, closer in (("{", "}"), ("(", ")"), ("[", "]")):
            if code.count(opener) != code.count(closer):
                problems.append(
                    f"{name}: {opener}{closer} unbalanced "
                    f"({code.count(opener)} vs {code.count(closer)})"
                )

        icon_imports = set(re.findall(r"^import (androidx\.compose\.material\.icons[\w.]*)", raw, re.M))
        for icon in set(re.findall(r"Icons\.(?:Filled|AutoMirrored\.Filled)\.(\w+)", code)):
            if not any(i.endswith("." + icon) for i in icon_imports):
                problems.append(f"{name}: Icons.*.{icon} used but not imported")

        companions = len(re.findall(r"\bcompanion object\b", code))
        if companions > 1:
            problems.append(
                f"{name}: {companions} companion objects — Kotlin allows only one per class"
            )

        seen = set()
        seen_code = False
        for line in raw.split("\n"):
            stripped = line.strip()
            if line.startswith("import "):
                if line in seen:
                    problems.append(f"{name}: duplicate import {line.strip()}")
                seen.add(line)
                if seen_code:
                    problems.append(
                        f"{name}: import after code — Kotlin only allows imports at the top "
                        f"({line.strip()})"
                    )
            elif stripped and not stripped.startswith("//") and not stripped.startswith("*") \
                    and not stripped.startswith("/*") and not stripped.startswith("package "):
                seen_code = True

    # 4. every referenced project type must be importable
    declarations = {}
    for path in kotlin_files(ROOT):
        txt = open(path).read()
        m = re.search(r"^package\s+([\w.]+)", txt, re.M)
        pkg = m.group(1) if m else ""
        for d in re.finditer(
            r"^(?:@\w+(?:\([^)]*\))?\s*)*"
            r"(?:public\s+|internal\s+|private\s+)?"
            r"(?:data\s+|sealed\s+|abstract\s+|open\s+|enum\s+|annotation\s+)*"
            r"(?:class|interface|object)\s+(\w+)",
            txt,
            re.M,
        ):
            declarations.setdefault(d.group(1), (pkg, path))

    for path in kotlin_files(ROOT):
        raw = open(path).read()
        code = strip_comments_and_strings(raw)
        m = re.search(r"^package\s+([\w.]+)", raw, re.M)
        pkg = m.group(1) if m else ""
        imported_simple = {
            line[len("import "):].strip().split(".")[-1]
            for line in raw.split("\n")
            if line.startswith("import ")
        }
        local = set(re.findall(r"(?:class|interface|object)\s+(\w+)", code))
        for decl, (home_pkg, _) in declarations.items():
            if home_pkg == pkg or decl in local or decl in imported_simple:
                continue
            if re.search(r"\b" + re.escape(decl) + r"\b", code):
                problems.append(
                    f"{os.path.basename(path)}: uses {decl} (from {home_pkg}) with no import"
                )

    # ---- A private top-level function that nothing calls ----
    #
    # This rule exists because of a real bug that shipped: a settings dialog was declared,
    # its open flag was set, and the block that rendered it was missing — so pressing the row
    # opened nothing at all. It compiled, it passed lint and every unit test, and the only
    # trace was a private function nobody called. That is exactly what this catches.
    all_code = []
    for root in ("app/src/main/java", "app/src/test"):
        for path in kotlin_files(root):
            all_code.append(strip_comments_and_strings(pathlib.Path(path).read_text()))
    haystack = "\n".join(all_code)

    for root in ("app/src/main/java", "app/src/test"):
        for path in kotlin_files(root):
            name = os.path.basename(path)
            raw = pathlib.Path(path).read_text()
            for declaration in re.findall(r"^private fun ([A-Za-z_]\w*)\s*\(", raw, re.M):
                if declaration in {"main"}:
                    continue
                # Called, or referenced as a function value.
                if re.search(r"\b" + re.escape(declaration) + r"\s*\(", haystack.replace(
                    f"private fun {declaration}", "", 1
                )) or re.search(r"::" + re.escape(declaration) + r"\b", haystack):
                    continue
                problems.append(
                    f"{name}: private fun {declaration}() is never called — "
                    f"is a call site missing?"
                )

    if problems:
        print("PROBLEMS FOUND:")
        for p in problems:
            print("  " + p)
        return 1

    print("All checks passed: balance, icons, imports.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
