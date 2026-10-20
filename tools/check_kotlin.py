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
  4. Every project type a file references is imported, declared locally, or in the
     same package — the check that catches a missing `import` the Kotlin compiler
     would only report after a long build.

Exit code is non-zero when anything is found.
"""

import os
import re
import sys

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

        seen = set()
        for line in raw.split("\n"):
            if line.startswith("import "):
                if line in seen:
                    problems.append(f"{name}: duplicate import {line.strip()}")
                seen.add(line)

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

    if problems:
        print("PROBLEMS FOUND:")
        for p in problems:
            print("  " + p)
        return 1

    print("All checks passed: balance, icons, imports.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
