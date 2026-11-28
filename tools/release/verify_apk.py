#!/usr/bin/env python3
"""Prove a built APK is the real release: signed by the pinned key, right package, not a debug build.

  python tools/release/verify_apk.py path/to/WishyClip-1.2.0.apk
"""
from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from common import (APK_NAME_RE, PACKAGE_ID, ReleaseError, fail, find_android_tool, info,  # noqa: E402
                    normalize_fingerprint, read_pinned, run)


def apk_cert_fingerprint(apk: Path) -> str:
    apksigner = find_android_tool("apksigner")
    if not apksigner:
        raise ReleaseError("apksigner not found. Install Android build-tools and set ANDROID_HOME (or put apksigner on PATH).")
    proc = run([apksigner, "verify", "--verbose", "--print-certs", str(apk)], check=False)
    out = proc.stdout or ""
    if proc.returncode != 0 or "Verifies" not in out:
        raise ReleaseError(f"apksigner says the APK is NOT validly signed:\n{out}")
    certs = re.findall(r"Signer #\d+ certificate SHA-256 digest:\s*([0-9a-fA-F]{64})", out)
    if len(certs) != 1:
        raise ReleaseError(f"Expected exactly one signer, found {len(certs)}.")
    return normalize_fingerprint(certs[0])


def badging(apk: Path) -> dict[str, str]:
    aapt2 = find_android_tool("aapt2")
    if not aapt2:
        raise ReleaseError("aapt2 not found. Install Android build-tools.")
    out = run([aapt2, "dump", "badging", str(apk)]).stdout or ""
    m = re.search(r"package: name='([^']+)' versionCode='(\d+)' versionName='([^']*)'", out)
    if not m:
        raise ReleaseError("Could not read the package details from the APK.")
    return {"package": m.group(1), "versionCode": m.group(2), "versionName": m.group(3)}


def verify(apk: Path, expect_code: int | None = None, expect_name: str | None = None) -> None:
    if not apk.is_file():
        raise ReleaseError(f"No such APK: {apk}")
    if not APK_NAME_RE.match(apk.name):
        raise ReleaseError(f"APK must be named like WishyClip-1.2.0.apk (the in-app updater only accepts that); got {apk.name}")
    pinned = read_pinned()
    if pinned is None:
        raise ReleaseError("release/signing-cert.sha256 is missing. Run tools/release/setup_signing.py first.")
    fp = apk_cert_fingerprint(apk)
    if fp != pinned:
        raise ReleaseError(
            "APK is signed with a DIFFERENT key than the pinned release key.\n"
            f"  APK    : {fp}\n  pinned : {pinned}\n"
            "Publishing it would break in-place updates for every user. Do not publish.")
    b = badging(apk)
    if b["package"] != PACKAGE_ID:
        raise ReleaseError(f"Package is {b['package']}, expected {PACKAGE_ID} (a debug build?).")
    if "debug" in b["versionName"].lower():
        raise ReleaseError(f"versionName {b['versionName']!r} looks like a debug build.")
    if expect_code is not None and int(b["versionCode"]) != expect_code:
        raise ReleaseError(f"versionCode in the APK is {b['versionCode']}, build.gradle.kts says {expect_code}.")
    if expect_name is not None and b["versionName"] != expect_name:
        raise ReleaseError(f"versionName in the APK is {b['versionName']}, build.gradle.kts says {expect_name}.")
    info(f"OK: {apk.name} is signed by the pinned release key, package {b['package']}, "
         f"version {b['versionName']} ({b['versionCode']}).")


def main() -> None:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("apk", type=Path)
    a = ap.parse_args()
    try:
        verify(a.apk)
    except ReleaseError as e:
        fail(str(e))


if __name__ == "__main__":
    main()
