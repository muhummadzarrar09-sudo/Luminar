#!/usr/bin/env python3
"""
Recto static checker.

There is no JDK or Android SDK in the authoring sandbox, so the only thing
standing between a typo and a failed build on the phone is this script. It
does not parse Kotlin properly - it is a scanner plus a pile of heuristics,
and every heuristic here corresponds to a bug that actually shipped.

    python3 tools/check_kotlin.py

Checks and the bug behind each:

  ascii           Em-dash in a .ps1 decoded as cp1252 by Windows PowerShell
                  5.1 broke parsing with a misleading line number.
  balance         Unbalanced braces/parens after a hand edit.
  dup-import      Two imports of the same simple name.
  unused-import   Noise, and a sign an edit went sideways.
  missing-import  Modifier.clickable, Spacer(Modifier.width(..)),
                  navigationBarsPadding() used with no import. Shipped four
                  separate times, including the build that prompted this
                  script.
  wrong-package   ExperimentalCoroutinesApi imported from
                  kotlinx.coroutines.flow instead of kotlinx.coroutines.
  items-clash     lazy.items and lazy.grid.items imported unaliased.
  fq-extension    Extension functions written fully qualified. Only real
                  extensions are flagged - a qualified call to a top-level
                  function like androidx.compose.material3.rememberModal-
                  BottomSheetState is legal and is left alone.
  by-delegate     'by remember { mutableStateOf(..) }' without the
                  getValue/setValue imports.
  room-builder    setForeignKeyConstraintsEnabled() - not a real method on
                  RoomDatabase.Builder.
  sequenced       removeLast()/removeFirst() resolve to SequencedCollection
                  on compileSdk 36 and throw NoSuchMethodError below API 35.

Exit code 1 if anything is flagged.
"""

from __future__ import annotations

import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, "app", "src", "main", "java")

problems: list[str] = []


def flag(path: str, line: int | None, msg: str) -> None:
    rel = os.path.relpath(path, ROOT).replace(os.sep, "/")
    problems.append(f"{rel}:{line}: {msg}" if line else f"{rel}: {msg}")


# --------------------------------------------------------------------------
# scanner
# --------------------------------------------------------------------------
#
# One left-to-right pass. Comments and string bodies are replaced with
# spaces so that line numbers survive. Doing this with independent regexes
# is what produced three phantom "unbalanced parens" reports: the // in
# "https://..." was eaten as a line comment before the string rule ran.


def blank(path_text: str) -> str:
    s = path_text
    out: list[str] = []
    i, n = 0, len(s)
    while i < n:
        c = s[i]
        two = s[i:i + 2]
        if two == "//":
            j = s.find("\n", i)
            j = n if j < 0 else j
            out.append(" " * (j - i))
            i = j
        elif two == "/*":
            depth, j = 1, i + 2          # Kotlin block comments nest
            while j < n and depth:
                if s[j:j + 2] == "/*":
                    depth += 1
                    j += 2
                elif s[j:j + 2] == "*/":
                    depth -= 1
                    j += 2
                else:
                    j += 1
            out.append("".join(ch if ch == "\n" else " " for ch in s[i:j]))
            i = j
        elif s[i:i + 3] == '"""':
            j = s.find('"""', i + 3)
            j = n if j < 0 else j + 3
            out.append("".join(ch if ch == "\n" else " " for ch in s[i:j]))
            i = j
        elif c == '"':
            j = i + 1
            while j < n and s[j] != '"':
                if s[j] == "\\":
                    j += 1
                if s[j:j + 1] == "\n":
                    break
                j += 1
            j = min(j + 1, n)
            # keep ${..} interpolations, they hold real code
            body = s[i:j]
            kept = re.sub(r"\$\{[^{}]*\}", lambda m: m.group(0), body)
            out.append(" " * len(body) if kept == body else body)
            i = j
        elif c == "'":
            j = i + 1
            if j < n and s[j] == "\\":
                j += 1
            j = min(j + 2, n)
            out.append(" " * (j - i))
            i = j
        else:
            out.append(c)
            i += 1
    return "".join(out)


IMPORT_RE = re.compile(r"^import\s+([\w.]+)(?:\s+as\s+(\w+))?\s*$")
PACKAGE_RE = re.compile(r"^package\s+([\w.]+)\s*$")

kt_files: list[str] = []
for dirpath, _dirs, names in os.walk(SRC):
    for nm in sorted(names):
        if nm.endswith(".kt"):
            kt_files.append(os.path.join(dirpath, nm))

text_files: list[str] = list(kt_files)
for sub, exts in (("scripts", (".ps1",)), ("tools", (".py",)),
                  ("gradle", (".toml",))):
    for dirpath, _dirs, names in os.walk(os.path.join(ROOT, sub)):
        for nm in sorted(names):
            if nm.endswith(exts):
                text_files.append(os.path.join(dirpath, nm))
for nm in ("build.gradle.kts", "settings.gradle.kts", "gradle.properties",
           os.path.join("app", "build.gradle.kts")):
    p = os.path.join(ROOT, nm)
    if os.path.exists(p):
        text_files.append(p)


def read(path: str) -> str:
    with open(path, "r", encoding="utf-8", errors="surrogateescape") as fh:
        return fh.read()


# 1. ASCII only ------------------------------------------------------------

for path in text_files:
    for i, line in enumerate(read(path).splitlines(), 1):
        bad = next((ch for ch in line if ord(ch) > 126), None)
        if bad:
            flag(path, i, f"non-ASCII U+{ord(bad):04X} ({bad!r}) - "
                          f"keep source pure ASCII")

# parse ---------------------------------------------------------------------

files: dict[str, dict] = {}
for path in kt_files:
    raw = read(path)
    lines = raw.splitlines()
    code = blank(raw)
    code_lines = code.splitlines()

    package = ""
    imports: dict[str, tuple[str, int]] = {}
    body_parts: list[str] = []

    for i, line in enumerate(lines, 1):
        t = line.strip()
        m = PACKAGE_RE.match(t)
        if m:
            package = m.group(1)
            body_parts.append("")
            continue
        m = IMPORT_RE.match(t)
        if m:
            fqn, alias = m.group(1), m.group(2)
            simple = alias or fqn.rsplit(".", 1)[-1]
            if simple in imports:
                flag(path, i, f"duplicate import name '{simple}' "
                              f"({imports[simple][0]} and {fqn})")
            imports[simple] = (fqn, i)
            body_parts.append("")
            continue
        body_parts.append(code_lines[i - 1] if i - 1 < len(code_lines) else "")

    body = "\n".join(body_parts)
    # join '.' continuations so 'androidx.compose.foundation.layout\n.Padding'
    # reads as one qualified name
    joined = re.sub(r"\.[ \t]*\n[ \t]*", ".", body)
    joined = re.sub(r"\n[ \t]*\.", ".", joined)

    files[path] = {"lines": lines, "code": code, "package": package,
                   "imports": imports, "body": body, "joined": joined}

# 2. balance ---------------------------------------------------------------

for path, f in files.items():
    for op, cl, name in (("{", "}", "braces"), ("(", ")", "parens"),
                         ("[", "]", "brackets")):
        d = f["code"].count(op) - f["code"].count(cl)
        if d:
            flag(path, None, f"unbalanced {name}: {abs(d)} unmatched "
                             f"{op if d > 0 else cl}")

# 3. unused imports --------------------------------------------------------
#
# getValue/setValue/provideDelegate are operator conventions - the compiler
# resolves them for 'by' without the name ever appearing in the source.

DELEGATE_OPS = {"getValue", "setValue", "provideDelegate"}

for path, f in files.items():
    for simple, (fqn, ln) in f["imports"].items():
        if simple in DELEGATE_OPS or fqn.endswith(".*"):
            continue
        if f["body"].count(simple) == 0:
            flag(path, ln, f"unused import '{fqn}'")

# 4. missing imports -------------------------------------------------------
#
# Hand-curated, all verified against androidx source. Deliberately narrow:
# a bootstrapped repo-wide table produced a flood of false positives on
# member calls (.map, .launch) and on the 'catch' keyword.

WANT = {
    # foundation
    "clickable": "androidx.compose.foundation.clickable",
    "combinedClickable": "androidx.compose.foundation.combinedClickable",
    "selectable": "androidx.compose.foundation.selection.selectable",
    "toggleable": "androidx.compose.foundation.selection.toggleable",
    "background": "androidx.compose.foundation.background",
    "border": "androidx.compose.foundation.border",
    "verticalScroll": "androidx.compose.foundation.verticalScroll",
    "horizontalScroll": "androidx.compose.foundation.horizontalScroll",
    "rememberScrollState": "androidx.compose.foundation.rememberScrollState",
    # layout modifiers
    "padding": "androidx.compose.foundation.layout.padding",
    "size": "androidx.compose.foundation.layout.size",
    "sizeIn": "androidx.compose.foundation.layout.sizeIn",
    "width": "androidx.compose.foundation.layout.width",
    "widthIn": "androidx.compose.foundation.layout.widthIn",
    "height": "androidx.compose.foundation.layout.height",
    "heightIn": "androidx.compose.foundation.layout.heightIn",
    "fillMaxSize": "androidx.compose.foundation.layout.fillMaxSize",
    "fillMaxWidth": "androidx.compose.foundation.layout.fillMaxWidth",
    "fillMaxHeight": "androidx.compose.foundation.layout.fillMaxHeight",
    "aspectRatio": "androidx.compose.foundation.layout.aspectRatio",
    "offset": "androidx.compose.foundation.layout.offset",
    "wrapContentWidth": "androidx.compose.foundation.layout.wrapContentWidth",
    "wrapContentHeight": "androidx.compose.foundation.layout.wrapContentHeight",
    "wrapContentSize": "androidx.compose.foundation.layout.wrapContentSize",
    "navigationBarsPadding":
        "androidx.compose.foundation.layout.navigationBarsPadding",
    "statusBarsPadding": "androidx.compose.foundation.layout.statusBarsPadding",
    "systemBarsPadding": "androidx.compose.foundation.layout.systemBarsPadding",
    "safeDrawingPadding":
        "androidx.compose.foundation.layout.safeDrawingPadding",
    "displayCutoutPadding":
        "androidx.compose.foundation.layout.displayCutoutPadding",
    "imePadding": "androidx.compose.foundation.layout.imePadding",
    "windowInsetsPadding":
        "androidx.compose.foundation.layout.windowInsetsPadding",
    # ui modifiers
    "clip": "androidx.compose.ui.draw.clip",
    "alpha": "androidx.compose.ui.draw.alpha",
    "shadow": "androidx.compose.ui.draw.shadow",
    "rotate": "androidx.compose.ui.draw.rotate",
    "scale": "androidx.compose.ui.draw.scale",
    "drawBehind": "androidx.compose.ui.draw.drawBehind",
    "drawWithContent": "androidx.compose.ui.draw.drawWithContent",
    "graphicsLayer": "androidx.compose.ui.graphics.graphicsLayer",
    "pointerInput": "androidx.compose.ui.input.pointer.pointerInput",
    "onGloballyPositioned":
        "androidx.compose.ui.layout.onGloballyPositioned",
    "onSizeChanged": "androidx.compose.ui.layout.onSizeChanged",
    "zIndex": "androidx.compose.ui.zIndex",
    "semantics": "androidx.compose.ui.semantics.semantics",
    "testTag": "androidx.compose.ui.platform.testTag",
    "focusRequester": "androidx.compose.ui.focus.focusRequester",
    # units and misc
    "dp": "androidx.compose.ui.unit.dp",
    "sp": "androidx.compose.ui.unit.sp",
    "em": "androidx.compose.ui.unit.em",
}

# dp/sp/em are extension properties, not functions - matched differently
UNIT_PROPS = {"dp", "sp", "em"}

DECL_RE = re.compile(
    r"^\s*(?:@\w+(?:\([^()]*\))?\s*)*"
    r"(?:public |private |internal |protected |inline |suspend |override |"
    r"open |abstract |sealed |data |enum |annotation |value |expect |actual )*"
    r"(?:fun|val|var|class|object|interface|typealias)\b")
NAME_RE = re.compile(
    r"\b(?:fun|val|var|class|object|interface|typealias)\s+"
    r"(?:<[^>]*>\s*)?(?:[\w.<>?]+\.)?(\w+)")


def declared_names(body: str) -> set[str]:
    out: set[str] = set()
    for line in body.splitlines():
        if DECL_RE.match(line):
            m = NAME_RE.search(line)
            if m:
                out.add(m.group(1))
    return out


pkg_names: dict[str, set[str]] = {}
for path, f in files.items():
    pkg_names.setdefault(f["package"], set()).update(declared_names(f["body"]))

for path, f in files.items():
    s = f["joined"]
    own = declared_names(f["body"])
    visible = pkg_names.get(f["package"], set())
    for simple, fqn in WANT.items():
        if simple in f["imports"] or simple in own or simple in visible:
            continue
        if fqn.startswith(f["package"] + "."):
            continue
        # Modifier functions are always calls, so demand a call - but a call
        # can be '.offset(x)' OR '.offset { .. }', because Kotlin allows a
        # trailing lambda with no parens. Matching only '(' let a missing
        # 'import ...layout.offset' through, which is precisely the bug this
        # check exists to catch. Matching a bare '.size' instead over-fires
        # on IntSize.size, hence the '[({]' rather than anything looser.
        if simple in UNIT_PROPS:
            hit = re.search(rf"[\d)\w]\s*\.\s*{re.escape(simple)}\b", s)
        else:
            hit = re.search(rf"\.{re.escape(simple)}\s*[({{]", s) or \
                  re.search(rf"(?<![\w.]){re.escape(simple)}\s*[({{]", s)
        if not hit:
            continue
        # skip fully-qualified use sites
        back = s[max(0, hit.start() - len(fqn) - 4):hit.start() + len(simple) + 1]
        if fqn in back:
            continue
        ln = f["body"][:0].count("\n")  # joined offsets do not map back
        for i, line in enumerate(f["lines"], 1):
            if re.search(rf"(?<![\w.]){re.escape(simple)}\s*[(\s]", line) or \
               re.search(rf"\.{re.escape(simple)}\b", line):
                ln = i
                break
        flag(path, ln or None, f"'{simple}' used but not imported "
                               f"(want {fqn})")

# 5. wrong package ---------------------------------------------------------

WRONG = {
    "ExperimentalCoroutinesApi": "kotlinx.coroutines.ExperimentalCoroutinesApi",
    "flatMapLatest": "kotlinx.coroutines.flow.flatMapLatest",
    "collectAsStateWithLifecycle":
        "androidx.lifecycle.compose.collectAsStateWithLifecycle",
    "viewModel": "androidx.lifecycle.viewmodel.compose.viewModel",
}

for path, f in files.items():
    for simple, (fqn, ln) in f["imports"].items():
        want = WRONG.get(simple)
        if want and fqn != want:
            flag(path, ln, f"'{simple}' imported from {fqn}, should be {want}")

    fqns = {fqn for fqn, _ in f["imports"].values()}
    if {"androidx.compose.foundation.lazy.items",
        "androidx.compose.foundation.lazy.grid.items"} <= fqns:
        names = [s for s, (fq, _) in f["imports"].items()
                 if fq.endswith("lazy.items") or fq.endswith("grid.items")]
        if len(set(names)) < 2:
            flag(path, None, "lazy.items and lazy.grid.items both imported "
                             "unaliased - they clash")

# 6. fully-qualified extension calls ---------------------------------------
#
# Only extensions. A qualified call to a top-level function such as
# androidx.compose.material3.rememberModalBottomSheetState(), or a static
# like ContextCompat.checkSelfPermission(), compiles fine.

EXTENSIONS = set(WANT) | {"items", "itemsIndexed", "stickyHeader",
                          "collectAsStateWithLifecycle", "collectAsState",
                          "asFlow", "asLiveData"}

for path, f in files.items():
    for m in re.finditer(r"androidx(?:\.\w+)+\.(\w+)\s*\(", f["joined"]):
        if m.group(1) in EXTENSIONS:
            flag(path, None, f"fully-qualified extension call "
                             f"'{m.group(0).strip()}' - extensions must be "
                             f"imported, not qualified")

# 7. 'by' delegation -------------------------------------------------------

for path, f in files.items():
    s, imports = f["body"], f["imports"]
    delegated = re.search(
        r"\b(?:val|var)\s+\w+\s+by\s+"
        r"(remember|rememberSaveable|mutableStateOf|produceState|"
        r"\w+\.collectAs|animate\w*AsState|derivedStateOf)", s)
    if delegated:
        if "getValue" not in imports:
            flag(path, None, "'by' state delegation without "
                             "androidx.compose.runtime.getValue")
        if re.search(r"\bvar\s+\w+\s+by\b", s) and "setValue" not in imports:
            flag(path, None, "'var .. by' without "
                             "androidx.compose.runtime.setValue")

# 8. Room builder ----------------------------------------------------------

ROOM_OK = {
    "addCallback", "addMigrations", "addTypeConverter",
    "allowMainThreadQueries", "build", "createFromAsset", "createFromFile",
    "createFromInputStream", "enableMultiInstanceInvalidation",
    "fallbackToDestructiveMigration", "fallbackToDestructiveMigrationFrom",
    "fallbackToDestructiveMigrationOnDowngrade", "openHelperFactory",
    "setAutoCloseTimeout", "setDriver", "setJournalMode",
    "setMultiInstanceInvalidationServiceIntent", "setQueryCallback",
    "setQueryCoroutineContext", "setQueryExecutor", "setTransactionExecutor",
}

for path, f in files.items():
    s = f["joined"]
    i = s.find("Room.databaseBuilder")
    if i < 0:
        continue
    chain = s[i:i + 1500].split("\n\n")[0]
    for m in re.finditer(r"\.(\w+)\s*\(", chain):
        nm = m.group(1)
        if nm[0].islower() and nm not in ROOM_OK and nm != "databaseBuilder":
            flag(path, None, f"'{nm}()' is not a RoomDatabase.Builder method")

# 9. SequencedCollection ---------------------------------------------------

for path, f in files.items():
    for i, line in enumerate(f["code"].splitlines(), 1):
        if re.search(r"\.(removeLast|removeFirst)\s*\(\s*\)", line):
            flag(path, i, "removeLast()/removeFirst() bind to "
                          "java.util.SequencedCollection on compileSdk 36 "
                          "and throw NoSuchMethodError below API 35 - "
                          "use removeAt()")

# --------------------------------------------------------------------------

if problems:
    print(f"{len(problems)} problem(s):\n")
    for p in problems:
        print("  " + p)
    sys.exit(1)

print(f"clean: {len(kt_files)} Kotlin files, {len(text_files)} files scanned")
