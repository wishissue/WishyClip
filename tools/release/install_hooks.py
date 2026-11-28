#!/usr/bin/env python3
"""Install git hooks that run the secret check before every commit and push (local safety net)."""
from __future__ import annotations

import stat
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
HOOK = """#!/bin/sh
# Installed by tools/release/install_hooks.py: blocks commits/pushes that contain signing secrets.
python3 "$(git rev-parse --show-toplevel)/tools/release/check_no_secrets.py" || exit 1
"""


def main() -> None:
    hooks = REPO / ".git" / "hooks"
    if not hooks.is_dir():
        print("Not a git checkout (no .git/hooks).", file=sys.stderr)
        raise SystemExit(1)
    for name in ("pre-commit", "pre-push"):
        target = hooks / name
        if target.exists() and "install_hooks.py" not in target.read_text(errors="ignore"):
            print(f"{name}: an existing hook is there; add this line to it yourself:\n  python3 tools/release/check_no_secrets.py || exit 1")
            continue
        target.write_text(HOOK)
        target.chmod(target.stat().st_mode | stat.S_IXUSR | stat.S_IXGRP | stat.S_IXOTH)
        print(f"Installed {name} hook.")


if __name__ == "__main__":
    main()
