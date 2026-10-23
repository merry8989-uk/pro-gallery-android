#!/usr/bin/env python3
"""Checks that the documentation's factual claims still match the code.

Written after an audit found the plans claiming things the code did not do. `ROADMAP.md` said
"Phase 3 is complete" while roughly half of it had never been built; its header said v1.5.1 and
targetSdk 35 while the build was v1.36.0 and 36; the README said 35 in one place and 36 in another.
None of that was a coding mistake. It was a *documentation* mistake, and nothing was checking.

The most important check here is the one for **decorative settings**. The project's own rule is that
every setting must affect real behaviour, and an audit found five that did not — stored, exported
and rendered, read by nothing. That is now a build failure, not a discovery.

Run:  python3 tools/check_docs.py
Exit: 0 when every claim holds, 1 when one does not.
"""

from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
APP = ROOT / "app" / "src" / "main" / "java" / "com" / "progallery" / "app"
DOCS = ["README.md", "ROADMAP.md", "PLAYER-AND-READERS-PLAN.md", "CONTRIBUTING.md"]

# Storage and explanation, where a read proves nothing about behaviour: the store persists the
# value, the export writes it into a file, and the dependency explainer describes it.
SETTINGS_PLUMBING = {
    "data/settings/SettingsExport.kt",
    "data/settings/SettingsRepository.kt",
    "data/settings/SettingsDependencies.kt",
}

# `AppSettings.kt` is deliberately **not** plumbing. Its computed properties are the logic that
# turns stored values into what the app acts on — `enabledKinds`, `acceptsSize`,
# `effectiveConcurrency` — so a field read there is read by something real.

# In a screen, merely showing a value is not behaviour: `checked = settings.x` on a switch nobody
# reads is exactly the bug this check exists for. A read that drives a **decision** is different —
# hiding a section, disabling a row — so only those count.
DECISION = re.compile(r"\b(if|when|else|return)\b|&&|\|\||\?:|!\s*settings\.")


# What each document must call itself. Added because one of them was **overwritten with another**:
# a script bound its output path to the wrong variable (`p` where `c` was meant) and wrote the
# contributing guide over the roadmap, destroying it. Nothing noticed for two releases — the version
# in the file still matched, because the file was a different document entirely. This is the cheapest
# possible check for the most destructive possible mistake.
EXPECTED_TITLES = {
    "README.md": "# ProGallery — Android Gallery App",
    "ROADMAP.md": "# ProGallery — Pending Work Plan",
    "PLAYER-AND-READERS-PLAN.md": "# The player and reader plan",
    "CONTRIBUTING.md": "# Contributing to ProGallery",
    "CHANGELOG.md": "# Changelog",
}


def gradle_truth() -> dict[str, str]:
    """The version, the code and the SDK levels, read from the build file."""
    text = (ROOT / "app" / "build.gradle.kts").read_text()
    return {
        "versionName": re.search(r'versionName = "([^"]+)"', text).group(1),
        "versionCode": re.search(r"versionCode = (\d+)", text).group(1),
        "compileSdk": re.search(r"compileSdk = (\d+)", text).group(1),
        "targetSdk": re.search(r"targetSdk = (\d+)", text).group(1),
    }


def settings_fields() -> list[str]:
    """The **stored** settings, and only those.

    Two things are deliberately excluded, because counting them was a false positive the first time
    this ran and a checker that cries wolf gets switched off:
    a **computed property** (`val x: T get() = …`) is read through whatever reads it, so it cannot be
    decorative; and a **constructor parameter of another class** (the `PerformancePolicy` values) is
    not a setting at all. Only the `AppSettings` constructor's own stored fields count.
    """
    text = (APP / "data" / "settings" / "AppSettings.kt").read_text()
    start = text.index("data class AppSettings(")
    # the constructor's parameters end at the first line that closes it at column zero-ish
    body = text[start:]
    depth = 0
    end = len(body)
    for i, ch in enumerate(body):
        if ch == "(":
            depth += 1
        elif ch == ")":
            depth -= 1
            if depth == 0:
                end = i
                break
    params = body[:end]
    return [
        m.group(1)
        for m in re.finditer(r"^\s{4}val (\w+)\s*:\s*[\w<>?.]+\s*=", params, re.M)
    ]


def settings_without_a_reader() -> list[str]:
    """The **decorative controls**: settings the Settings screen shows, that nothing acts on.

    The rule is the project's own — every setting must affect real application behaviour — and the
    bug it was written for was five settings that were stored, exported and rendered as real
    controls while no logic read them. A person changed them and believed something happened.

    The check is deliberately narrow: it looks only at settings the screen actually **renders**,
    because those are the ones that can lie to a person. A field in `AppSettings` with no control is
    not decorative — it is app state, and a different (milder) question. `hasSeenDefaultGalleryPrompt`
    is exactly that: whether a one-time prompt has been shown, read once by the home screen.

    In a screen, merely showing a value is not behaviour. A read that drives a **decision** is, so
    only those count — plus a negation, which is logic even when it sits in a plain assignment.
    """
    screen = (APP / "ui" / "screens" / "settings" / "SettingsScreen.kt").read_text()
    rendered = {name for name in settings_fields() if re.search(r"\b" + re.escape(name) + r"\b", screen)}

    files = [p for p in APP.rglob("*.kt")]
    inert = []
    for name in settings_fields():
        if name not in rendered:
            continue
        pattern = re.compile(r"\b" + re.escape(name) + r"\b")
        found = False
        for path in files:
            rel = str(path.relative_to(APP))
            if rel in SETTINGS_PLUMBING:
                continue
            for line in path.read_text().split("\n"):
                if not pattern.search(line):
                    continue
                # The declaration is not a read of the setting. Without this, every field proves
                # its own liveness and the check passes on exactly the bug it was written for —
                # which is what it did the first time it was tested.
                if re.match(r"\s*(?:private |internal )?val " + re.escape(name) + r"\s*:", line):
                    continue
                # A view model is **logic**: it reads a setting and decides what the screen should
                # be, so every read there counts. A screen is **rendering**, and only a read that
                # drives a decision counts — `checked = settings.x` is exactly how a decorative
                # switch looks, and that is the defect this rule exists for.
                if rel.startswith("ui/") and not rel.endswith("ViewModel.kt") and not DECISION.search(line):
                    continue
                found = True
                break
            if found:
                break
        if not found:
            inert.append(name)
    return inert


def _enum_block(text: str, name: str) -> str:
    """The body of one enum, so one enum's entries are never counted as another's."""
    start = text.index(f"enum class {name}")
    depth = 0
    for i in range(start, len(text)):
        if text[i] == "{":
            depth += 1
        elif text[i] == "}":
            depth -= 1
            if depth == 0:
                return text[start:i]
    return text[start:]


def log_vocabulary() -> dict[str, int]:
    text = (APP / "data" / "eventlog" / "LogVocabulary.kt").read_text()
    levels = _enum_block(text, "LogLevel")
    categories = _enum_block(text, "LogCategory")
    events = _enum_block(text, "EventName")
    return {
        "events": len(re.findall(r'^\s+[A-Z][A-Z_0-9]*\("([a-z_0-9]+)",', events, re.M)),
        "levels": len(re.findall(r'^\s+[A-Z][A-Z_0-9]*\("([a-z]+)"\)', levels, re.M)),
        "categories": len(re.findall(r'^\s+[A-Z][A-Z_0-9]*\("([a-z_]+)",\s*"', categories, re.M)),
    }


def main() -> int:
    problems: list[str] = []
    truth = gradle_truth()

    # ---- 0. every document is the document it claims to be ----
    for doc, expected in EXPECTED_TITLES.items():
        path = ROOT / doc
        if not path.exists():
            problems.append(f"{doc} is missing")
            continue
        first = path.read_text().split("\n", 1)[0].strip()
        if first != expected:
            problems.append(
                f"{doc} starts with {first!r} but should start with {expected!r} — "
                "it may have been overwritten with another document"
            )

    # ---- 1. every version claim in a document must be the current version ----
    for doc in DOCS:
        path = ROOT / doc
        if not path.exists():
            continue
        for line_no, line in enumerate(path.read_text().split("\n"), 1):
            for found in re.findall(r"\bv(\d+\.\d+\.\d+)\b", line):
                if found == truth["versionName"]:
                    continue
                # history is allowed to name old versions, but not as the current release
                if re.search(r"current release|current version", line, re.I):
                    problems.append(
                        f"{doc}:{line_no}: calls v{found} the current release, but the build is "
                        f"v{truth['versionName']}"
                    )
            for found in re.findall(r"versionCode (\d+)", line):
                if found != truth["versionCode"]:
                    problems.append(
                        f"{doc}:{line_no}: says versionCode {found}, the build is {truth['versionCode']}"
                    )

    # ---- 2. the SDK levels, which disagreed with themselves in the README ----
    for doc in DOCS:
        path = ROOT / doc
        if not path.exists():
            continue
        for line_no, line in enumerate(path.read_text().split("\n"), 1):
            for key in ("compileSdk", "targetSdk"):
                # "compileSdk 36", "compileSdk = 36" and "compileSdk **36**" all occur in these
                # documents, so the operator is optional and at most a couple of characters wide.
                for found in re.findall(rf"{key}\W{{0,3}}(\d+)", line):
                    if found != truth[key]:
                        problems.append(f"{doc}:{line_no}: says {key} {found}, the build is {truth[key]}")

    # ---- 3. no setting may be decorative ----
    inert = settings_without_a_reader()
    if inert:
        problems.append(
            "these settings are stored, exported or shown but read by nothing: "
            + ", ".join(sorted(inert))
            + " — every setting must affect real behaviour, so either wire it or remove it"
        )

    # ---- 4. the numbers the docs quote about the log must be the real ones ----
    vocab = log_vocabulary()
    for doc in DOCS:
        path = ROOT / doc
        if not path.exists():
            continue
        text = path.read_text()
        claims = [
            (r"(\d+) (?:log )?categor(?:y|ies)", "categories", vocab["categories"]),
            (r"(\d+) levels", "levels", vocab["levels"]),
            (r"(\d+) events", "events", vocab["events"]),
        ]
        for pattern, label, real in claims:
            for found in re.findall(pattern, text, re.I):
                # a document may name a historical count; only the current-state phrasing is checked
                if int(found) not in (real,) and re.search(
                    rf"{found} {label}|{label}.*{found}", text, re.I
                ):
                    if f"{found} {label}" in text and real != int(found):
                        problems.append(
                            f"{doc}: says {found} {label}, the vocabulary has {real}"
                        )

    if problems:
        print("DOCUMENTATION DOES NOT MATCH THE CODE:")
        for problem in problems:
            print("  " + problem)
        return 1

    rendered = [
        name
        for name in settings_fields()
        if re.search(
            r"\b" + re.escape(name) + r"\b",
            (APP / "ui" / "screens" / "settings" / "SettingsScreen.kt").read_text(),
        )
    ]
    print(
        "Docs match the code: "
        f"v{truth['versionName']} ({truth['versionCode']}), "
        f"SDK {truth['compileSdk']}/{truth['targetSdk']}, "
        f"{len(settings_fields())} settings of which the {len(rendered)} shown as controls all do "
        f"something, {vocab['categories']} log categories / {vocab['levels']} levels / "
        f"{vocab['events']} events."
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
