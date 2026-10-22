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

        # One companion per *class*, not per file. Counting per file was wrong and produced a
        # false positive the moment a file held three enums that each had one — and a check that
        # cries wolf teaches everyone to ignore it. So the count is taken between one top-level
        # declaration and the next.
        declarations = [
            match.start()
            for match in re.finditer(r"^(?:internal |private |public )?(?:data |sealed |enum |abstract )?class \w+|^object \w+|^enum class \w+", code, re.M)
        ]
        bounds = declarations + [len(code)]
        for index in range(len(declarations)):
            body = code[bounds[index]:bounds[index + 1]]
            companions = len(re.findall(r"\bcompanion object\b", body))
            if companions > 1:
                problems.append(
                    f"{name}: {companions} companion objects in one class — Kotlin allows one"
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

    # Every top-level function in the project, with the package it lives in. Only the ones
    # declared at column zero are collected: a member function is the class's business.
    functions = {}
    for path in kotlin_files(ROOT):
        txt = open(path).read()
        code = strip_comments_and_strings(txt)
        m = re.search(r"^package\s+([\w.]+)", txt, re.M)
        pkg = m.group(1) if m else ""
        # The type-parameter list is optional and easy to forget: `fun <T> foo(` is a top-level
        # function too, and leaving it out of the pattern is exactly how this rule would have
        # missed one of the two calls it was written for.
        for fname in re.findall(
            r"^(?:inline |suspend |private |internal )*fun (?:<[^>]*>\s*)?([a-z]\w*)\s*\(",
            code,
            re.M,
        ):
            functions.setdefault(fname, (pkg, path))

    # Read every file once. Two rules walk the whole tree, and doing that twice per rule made
    # the checker slower than the build it is meant to save time on.
    parsed = []
    for path in kotlin_files(ROOT):
        raw = open(path).read()
        code = strip_comments_and_strings(raw)
        m = re.search(r"^package\s+([\w.]+)", raw, re.M)
        parsed.append(
            {
                "path": path,
                "raw": raw,
                "code": code,
                "pkg": m.group(1) if m else "",
                "imports": {
                    line[len("import "):].strip().split(".")[-1]
                    for line in raw.split("\n")
                    if line.startswith("import ")
                },
            }
        )

    # ---- A project type used with no import ----
    for entry in parsed:
        local = set(re.findall(r"(?:class|interface|object)\s+(\w+)", entry["code"]))
        for decl, (home_pkg, _) in declarations.items():
            if home_pkg == entry["pkg"] or decl in local or decl in entry["imports"]:
                continue
            if re.search(r"\b" + re.escape(decl) + r"\b", entry["code"]):
                problems.append(
                    f"{os.path.basename(entry['path'])}: uses {decl} (from {home_pkg}) with no import"
                )

    # ---- A project function called with no import ----
    #
    # The same rule for top-level *functions* rather than types. It exists because of a real
    # build failure: `sameUri` and `runCatchingCancellable` are both top-level functions in this
    # project, and calling either without its import is a compile error the type rule cannot see,
    # because that rule only looks at capitalised names. A CI round trip costs minutes.
    for entry in parsed:
        local_functions = set(re.findall(r"^fun ([a-z]\w*)\s*\(", entry["code"], re.M))
        for fname, (home_pkg, _) in functions.items():
            if home_pkg == entry["pkg"] or fname in local_functions or fname in entry["imports"]:
                continue
            # A trailing lambda counts as a call and has no parentheses at all:
            # `runCatchingCancellable { ... }`. The same blind spot that made the "never called"
            # rule cry wolf, in the other direction.
            if re.search(r"\b" + re.escape(fname) + r"\s*[({]", entry["code"]):
                problems.append(
                    f"{os.path.basename(entry['path'])}: calls {fname}() (from {home_pkg}) with no import"
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
                # Called, referenced as a function value, or called with a trailing lambda.
                #
                # The trailing-lambda form is the one that was missed: a composable whose last
                # parameter is a lambda is called as `Centred { ... }`, with no parentheses at
                # all — so the rule called a function with four call sites "never called". A
                # check that cries wolf is a check people learn to skip.
                without_declaration = haystack.replace(f"private fun {declaration}", "", 1)
                called = (
                    re.search(r"\b" + re.escape(declaration) + r"\s*\(", without_declaration)
                    or re.search(r"\b" + re.escape(declaration) + r"\s*\{", without_declaration)
                    or re.search(r"::" + re.escape(declaration) + r"\b", without_declaration)
                )
                if called:
                    continue
                problems.append(
                    f"{name}: private fun {declaration}() is never called — "
                    f"is a call site missing?"
                )

    # ---- A slash-star inside a block comment ----
    #
    # Kotlin block comments *nest*. So a comment that quotes a wildcard — writing the two
    # characters that open a comment, inside a comment — opens an inner comment that then has to
    # be closed too, and the file fails to compile with "Unclosed comment". That happened here
    # while writing a MIME table, and this checker did not catch it; the compiler did, one CI
    # round later. It costs nothing to catch it here instead.
    #
    # The scan has to understand strings, character literals, line comments and raw strings, or
    # it reports a wildcard inside a string as a fault — and a check that cries wolf is worse
    # than no check, because it teaches everyone to ignore it.
    for root in ("app/src/main/java", "app/src/test"):
        for path in kotlin_files(root):
            name = os.path.basename(path)
            in_comment = False
            in_raw_string = False
            for number, line in enumerate(pathlib.Path(path).read_text().split("\n"), 1):
                index = 0
                while index < len(line):
                    if in_raw_string:
                        if line.startswith('"""', index):
                            in_raw_string = False
                            index += 3
                        else:
                            index += 1
                        continue

                    if not in_comment and line.startswith('"""', index):
                        in_raw_string = True
                        index += 3
                        continue

                    pair = line[index:index + 2]

                    if in_comment:
                        if pair == "*/":
                            in_comment = False
                            index += 2
                        elif pair == "/*":
                            problems.append(
                                f"{name}:{number}: a slash-star inside a block comment opens a "
                                f"nested comment in Kotlin — describe the pattern instead of "
                                f"quoting it"
                            )
                            index += 2
                        else:
                            index += 1
                        continue

                    if pair == "//":
                        break
                    if pair == "/*":
                        in_comment = True
                        index += 2
                        continue

                    character = line[index]
                    if character in ('"', "'"):
                        quote = character
                        index += 1
                        while index < len(line):
                            if line[index] == "\\":
                                index += 2
                                continue
                            if line[index] == quote:
                                index += 1
                                break
                            index += 1
                        continue

                    index += 1

    if problems:
        print("PROBLEMS FOUND:")
        for p in problems:
            print("  " + p)
        return 1

    print("All checks passed: balance, icons, imports.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
