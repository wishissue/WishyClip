#!/usr/bin/env python3
"""Fail if anything that looks like a signing secret is about to enter (or is already in) the repository.

  python tools/release/check_no_secrets.py            checks tracked + not-ignored files
  python tools/release/check_no_secrets.py --history  also checks every file that was ever committed

Runs in CI on every push, in the git pre-commit / pre-push hooks (install_hooks.py) and before every release.
"""
from __future__ import annotations

import argparse
import re
import shutil
import subprocess
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from common import REPO, run  # noqa: E402

FORBIDDEN_SUFFIXES = {".jks", ".keystore", ".p12", ".pfx", ".pem", ".key", ".pkcs12", ".der"}
FORBIDDEN_NAMES = {"keystore.properties", "signing.properties", "release.jks", ".wishyclip-signing"}
# The ONLY keystore allowed in the repo: the public shared debug key, and only if it opens with "android".
DEBUG_KEYSTORE = "tools/signing/debug.keystore"

CONTENT_EXTS = {".gradle", ".kts", ".properties", ".yml", ".yaml", ".json", ".py", ".kt", ".java", ".sh", ".toml", ".xml"}
SELF_DIR = "tools/release/"  # this tool's own files contain the patterns they search for
PATTERNS = [
    (re.compile(r"-----BEGIN (?:RSA |EC |DSA |OPENSSH |ENCRYPTED )?PRIVATE KEY-----"), "private key block"),
    (re.compile(r"(?i)\b(storePassword|keyPassword)\s*[=:]\s*\"(?!android\")[^\"\s]{3,}\""), "hard-coded signing password"),
    (re.compile(r"(?im)^\s*(storePassword|keyPassword)\s*=\s*[^\s$]{3,}\s*$"), "signing password in a properties file", {".properties"}),
    (re.compile(r"(?i)secrets\.(KEYSTORE\w*|KEY_PASSWORD|KEY_ALIAS|SIGNING\w*|STORE_PASSWORD)"), "CI secret used for signing"),
    (re.compile(r"(?i)android\.injected\.signing"), "signing parameters passed to Gradle"),
    (re.compile(r"(?i)\bKEYSTORE_BASE64\b"), "base64 keystore"),
]


def _git(args: list[str], root: Path) -> list[str]:
    out = subprocess.run(["git", *args], cwd=str(root), text=True, stdout=subprocess.PIPE, stderr=subprocess.DEVNULL)
    return [l for l in out.stdout.split("\n") if l]


def _debug_keystore_is_public(path: Path) -> bool:
    if shutil.which("keytool") is None:
        return False
    proc = subprocess.run(["keytool", "-list", "-keystore", str(path), "-storepass", "android"],
                          stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
    return proc.returncode == 0


def forbidden_path(rel: str) -> str | None:
    p = Path(rel)
    if rel == DEBUG_KEYSTORE:
        return None  # handled separately (must open with the public password)
    if p.name in FORBIDDEN_NAMES or any(part == ".wishyclip-signing" for part in p.parts):
        return "signing configuration file"
    if p.suffix.lower() in FORBIDDEN_SUFFIXES:
        return f"key / certificate file ({p.suffix})"
    return None


def scan(root: Path = REPO, history: bool = False) -> list[str]:
    problems: list[str] = []
    files = _git(["ls-files", "-co", "--exclude-standard"], root)
    for rel in files:
        why = forbidden_path(rel)
        if why:
            problems.append(f"{rel}: {why}")
        if rel == DEBUG_KEYSTORE and (root / rel).exists() and not _debug_keystore_is_public(root / rel):
            problems.append(f"{rel}: does not open with the public debug password; it must be the shared debug key only")
        path = root / rel
        if rel.startswith(SELF_DIR) or not path.is_file() or path.suffix.lower() not in CONTENT_EXTS:
            continue
        try:
            if path.stat().st_size > 1_000_000:
                continue
            text = path.read_text(encoding="utf-8", errors="ignore")
        except OSError:
            continue
        for entry in PATTERNS:
            rx, label = entry[0], entry[1]
            if len(entry) > 2 and path.suffix.lower() not in entry[2]:
                continue
            m = rx.search(text)
            if m:
                line = text.count("\n", 0, m.start()) + 1
                problems.append(f"{rel}:{line}: {label}")
    if history:
        for rel in sorted(set(_git(["log", "--all", "--pretty=format:", "--name-only", "--diff-filter=AM"], root))):
            why = forbidden_path(rel)
            if why:
                problems.append(f"{rel}: in git HISTORY ({why}). Treat that key as public and rotate it.")
    return problems


def main() -> None:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--history", action="store_true")
    a = ap.parse_args()
    problems = scan(REPO, a.history)
    if problems:
        print("SECRET CHECK FAILED - signing material must never be in the repository:\n", file=sys.stderr)
        for p in problems:
            print(f"  - {p}", file=sys.stderr)
        print("\nRemove the file/line (and `git rm --cached` it). Keys live in ~/.wishyclip-signing only.", file=sys.stderr)
        raise SystemExit(1)
    print("Secret check passed: no signing keys or passwords in the repository.")


if __name__ == "__main__":
    main()
