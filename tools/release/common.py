"""Shared helpers for the WishyClip release tools. Standard library only (Python 3.9+).

Nothing here ever prints a password or key. Passwords reach `keytool` through environment
variables (`-storepass:env`), never on a command line.
"""
from __future__ import annotations

import hashlib
import os
import re
import shutil
import subprocess
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
GRADLE_FILE = REPO / "app" / "build.gradle.kts"
# Public fingerprint (SHA-256 of the release certificate) of the real signing key. Safe to commit:
# it only lets the tools notice if a DIFFERENT key is ever used, which would break updates for every user.
PINNED_FILE = REPO / "release" / "signing-cert.sha256"
APK_NAME_RE = re.compile(r"^WishyClip-[0-9][0-9A-Za-z.]*\.apk$")  # must match update/ReleaseParser.kt
PACKAGE_ID = "org.wishyclip.app"


class ReleaseError(Exception):
    """A rule was broken. The message says what to do about it."""


def signing_dir() -> Path:
    return Path(os.environ.get("WISHYCLIP_SIGNING_DIR") or (Path.home() / ".wishyclip-signing")).expanduser()


def read_properties(path: Path) -> dict[str, str]:
    props: dict[str, str] = {}
    for line in path.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        k, v = line.split("=", 1)
        props[k.strip()] = v.strip()
    return props


def normalize_fingerprint(text: str) -> str:
    """'AA:BB:..' or 'sha256: aabb..' -> 'aabb..' (64 lowercase hex chars)."""
    hexed = re.sub(r"[^0-9a-fA-F]", "", text.split("SHA256")[-1] if "SHA256" in text.upper() else text)
    hexed = hexed.lower()
    if len(hexed) != 64:
        raise ReleaseError(f"Not a SHA-256 fingerprint: {text!r}")
    return hexed


def read_pinned() -> str | None:
    if not PINNED_FILE.exists():
        return None
    return normalize_fingerprint(PINNED_FILE.read_text(encoding="utf-8").strip().splitlines()[-1])


def sha256_file(path: Path) -> str:
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def run(cmd: list[str], *, env: dict[str, str] | None = None, cwd: Path | None = None,
        check: bool = True, capture: bool = True) -> subprocess.CompletedProcess:
    full_env = dict(os.environ)
    if env:
        full_env.update(env)
    proc = subprocess.run(cmd, env=full_env, cwd=str(cwd or REPO), text=True,
                          stdout=subprocess.PIPE if capture else None,
                          stderr=subprocess.STDOUT if capture else None)
    if check and proc.returncode != 0:
        raise ReleaseError(f"Command failed ({proc.returncode}): {' '.join(cmd[:3])} ...\n{proc.stdout or ''}")
    return proc


def parse_version(gradle_text: str) -> tuple[str, int]:
    """(versionName, versionCode) from app/build.gradle.kts text."""
    name = re.search(r'versionName\s*=\s*"([^"]+)"', gradle_text)
    code = re.search(r"versionCode\s*=\s*(\d+)", gradle_text)
    if not name or not code:
        raise ReleaseError("Could not read versionName / versionCode from app/build.gradle.kts")
    return name.group(1), int(code.group(1))


def current_version() -> tuple[str, int]:
    return parse_version(GRADLE_FILE.read_text(encoding="utf-8"))


def version_tuple(v: str) -> tuple[int, ...]:
    core = v.lstrip("vV").split("-")[0].split("+")[0]
    try:
        return tuple(int(p) for p in core.split("."))
    except ValueError:
        raise ReleaseError(f"Version is not numeric: {v!r}") from None


def find_android_tool(name: str) -> str | None:
    """apksigner / aapt2 from the newest build-tools, or from PATH."""
    found = shutil.which(name)
    if found:
        return found
    for var in ("ANDROID_HOME", "ANDROID_SDK_ROOT"):
        root = os.environ.get(var)
        if not root:
            continue
        bt = Path(root) / "build-tools"
        if not bt.is_dir():
            continue
        for d in sorted(bt.iterdir(), key=lambda p: [int(x) if x.isdigit() else 0 for x in re.split(r"[.\-]", p.name)], reverse=True):
            for candidate in (d / name, d / f"{name}.bat"):
                if candidate.exists():
                    return str(candidate)
    return None


def keystore_fingerprint(keystore: Path, alias: str, store_password: str) -> str:
    """SHA-256 of the certificate stored under [alias]; the password goes through an env var."""
    if shutil.which("keytool") is None:
        raise ReleaseError("keytool not found. Install a JDK (17+) and make sure it is on PATH.")
    proc = run(["keytool", "-list", "-v", "-keystore", str(keystore), "-alias", alias,
                "-storepass:env", "WISHY_STOREPASS"],
               env={"WISHY_STOREPASS": store_password}, check=False)
    if proc.returncode != 0:
        raise ReleaseError("keytool could not open the keystore (wrong password or alias?).")
    m = re.search(r"SHA-?256:\s*([0-9A-Fa-f:]{95})", proc.stdout or "")
    if not m:
        raise ReleaseError("keytool did not print a SHA-256 fingerprint.")
    return normalize_fingerprint(m.group(1))


def info(msg: str) -> None:
    print(msg, flush=True)


def fail(msg: str) -> "None":
    print(f"\nSTOP: {msg}\n", file=sys.stderr, flush=True)
    raise SystemExit(1)
