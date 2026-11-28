#!/usr/bin/env python3
"""Build the REAL release APK on this machine, verify it, and (optionally) publish it to GitHub.

  python tools/release/build_release.py                 build + verify, files in release-out/
  python tools/release/build_release.py --publish       ...then tag, push the tag and create the GitHub release

Rules enforced here (each one exists because breaking it breaks updates or leaks the key):
  * runs only on a machine that has the signing folder; refuses to run on CI;
  * the key must match the pinned fingerprint in release/signing-cert.sha256;
  * no secrets in the repository; git tree clean; tag not used yet;
  * versionCode and versionName must be higher than the previous release tag;
  * unit tests pass; APK is verified with apksigner/aapt2 before anything is uploaded;
  * only WishyClip-<version>.apk and its .sha256 are ever uploaded - never a key.
"""
from __future__ import annotations

import argparse
import os
import shutil
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import check_no_secrets  # noqa: E402
import verify_apk  # noqa: E402
from common import (REPO, ReleaseError, current_version, fail, info, keystore_fingerprint, parse_version,  # noqa: E402
                    read_pinned, read_properties, run, sha256_file, signing_dir, version_tuple)

OUT = REPO / "release-out"


def previous_release() -> tuple[str, str, int] | None:
    """(tag, versionName, versionCode) of the newest v* tag, read from that tag's build.gradle.kts."""
    tags = [t for t in run(["git", "tag", "--list", "v*"]).stdout.split() if t.lstrip("v").replace(".", "").isdigit()]
    if not tags:
        return None
    tags.sort(key=lambda t: version_tuple(t))
    tag = tags[-1]
    text = run(["git", "show", f"{tag}:app/build.gradle.kts"], check=False)
    if text.returncode != 0:
        return None
    name, code = parse_version(text.stdout)
    return tag, name, code


def preflight(args: argparse.Namespace) -> tuple[str, int, Path]:
    if os.environ.get("CI"):
        raise ReleaseError("CI is set. Real releases are built on the maintainer's machine only (the key never goes to CI).")
    folder = signing_dir()
    props_file = folder / "keystore.properties"
    if not props_file.exists():
        raise ReleaseError(f"No signing key in {folder}. Run: python tools/release/setup_signing.py")
    props = read_properties(props_file)
    ks = Path(props["storeFile"])
    ks = ks if ks.is_absolute() else folder / ks
    if not ks.exists():
        raise ReleaseError(f"Keystore {ks} is missing.")
    if os.name == "posix" and (props_file.stat().st_mode & 0o077):
        raise ReleaseError(f"{props_file} is readable by other users. Run: chmod 600 {props_file}")
    pinned = read_pinned()
    fp = keystore_fingerprint(ks, props["keyAlias"], props["storePassword"])
    if pinned is None:
        raise ReleaseError("release/signing-cert.sha256 is missing; commit the pinned fingerprint first (setup_signing.py writes it).")
    if fp != pinned:
        raise ReleaseError("The key in the signing folder is NOT the pinned release key. Releasing with it would stop updates\n"
                           "for every installed copy. Restore the original key from your backup.")
    problems = check_no_secrets.scan(REPO)
    if problems:
        raise ReleaseError("Secrets found in the repository:\n  " + "\n  ".join(problems))
    name, code = current_version()
    tag = f"v{name}"
    if "-" in name or "debug" in name.lower():
        raise ReleaseError(f"versionName {name!r} must be a plain number like 1.2.0 for a release.")
    if not args.allow_dirty and run(["git", "status", "--porcelain"]).stdout.strip():
        raise ReleaseError("The git working tree has uncommitted changes. Commit them first (releases must be reproducible).")
    if run(["git", "tag", "--list", tag]).stdout.strip():
        raise ReleaseError(f"Tag {tag} already exists. Bump versionName and versionCode in app/build.gradle.kts.")
    prev = previous_release()
    if prev:
        ptag, pname, pcode = prev
        if code <= pcode:
            raise ReleaseError(f"versionCode {code} must be greater than {pcode} (in {ptag}); Android refuses updates otherwise.")
        if version_tuple(name) <= version_tuple(pname):
            raise ReleaseError(f"versionName {name} must be greater than {pname} (in {ptag}).")
    return name, code, folder


def gradle(*tasks: str, signing: Path | None = None) -> None:
    exe = "gradlew.bat" if os.name == "nt" else "./gradlew"
    cmd = [exe, "--no-daemon", "--console=plain", *tasks]
    env = {"WISHYCLIP_SIGNING_DIR": str(signing)} if signing else None
    info("> " + " ".join(cmd))
    proc = run(cmd, env=env, check=False, capture=False)
    if proc.returncode != 0:
        raise ReleaseError(f"Gradle failed: {' '.join(tasks)}")


def find_release_apk() -> Path:
    base = REPO / "app" / "build" / "outputs" / "apk" / "release"
    candidates = sorted(base.glob("*universal*release*.apk")) or sorted(base.glob("*release*.apk"))
    signed = [c for c in candidates if "unsigned" not in c.name]
    if not signed:
        raise ReleaseError("No signed release APK was produced (is the signing key visible to Gradle?).")
    if len(signed) > 1 and not any("universal" in c.name for c in signed):
        raise ReleaseError(f"Several APKs found and none is universal: {[c.name for c in signed]}")
    return signed[0]


def build(args: argparse.Namespace) -> Path:
    name, code, folder = preflight(args)
    info(f"Building WishyClip {name} (versionCode {code})")
    if not args.skip_tests:
        gradle("testDebugUnitTest")
    gradle("clean", "assembleRelease", signing=folder)
    built = find_release_apk()
    OUT.mkdir(exist_ok=True)
    final = OUT / f"WishyClip-{name}.apk"
    shutil.copy2(built, final)
    verify_apk.verify(final, expect_code=code, expect_name=name)
    sha_file = OUT / f"{final.name}.sha256"
    sha_file.write_text(f"{sha256_file(final)}  {final.name}\n", encoding="utf-8")
    info(f"\nReady: {final}\n       {sha_file}")
    return final


def publish(apk: Path, notes: Path | None, assume_yes: bool) -> None:
    name = apk.stem.removeprefix("WishyClip-")
    tag = f"v{name}"
    sha_file = apk.with_name(apk.name + ".sha256")
    assets = [apk, sha_file]
    if any(a.suffix.lower() not in (".apk", ".sha256") for a in assets):
        raise ReleaseError("Refusing to upload anything but the APK and its checksum.")
    verify_apk.verify(apk)  # once more, right before uploading
    if run(["gh", "auth", "status"], check=False).returncode != 0:
        raise ReleaseError("gh is not logged in. Run: gh auth login")
    if not assume_yes:
        print(f"\nAbout to create tag {tag}, push it, and publish a GitHub release with:\n  " +
              "\n  ".join(a.name for a in assets))
        if input("Type 'publish' to continue: ").strip() != "publish":
            raise ReleaseError("Cancelled.")
    run(["git", "tag", "-a", tag, "-m", f"WishyClip {name}"])
    run(["git", "push", "origin", tag])
    cmd = ["gh", "release", "create", tag, *map(str, assets), "--title", f"WishyClip {name}", "--latest", "--verify-tag"]
    cmd += ["--notes-file", str(notes)] if notes else ["--generate-notes"]
    run(cmd)
    listed = run(["gh", "release", "view", tag, "--json", "assets", "--jq", ".assets[].name"]).stdout.split()
    if sorted(listed) != sorted(a.name for a in assets):
        raise ReleaseError(f"Release assets are {listed}; expected exactly {[a.name for a in assets]}. Check the release page now.")
    info(f"\nPublished {tag}. The app's update check will pick it up on the next start.")


def main() -> None:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--publish", action="store_true", help="tag, push the tag and create the GitHub release after a successful build")
    ap.add_argument("--notes", type=Path, default=None, help="release notes file (markdown)")
    ap.add_argument("--yes", action="store_true", help="do not ask before publishing")
    ap.add_argument("--skip-tests", action="store_true", help="NOT for real releases")
    ap.add_argument("--allow-dirty", action="store_true", help="NOT for real releases")
    a = ap.parse_args()
    try:
        apk = build(a)
        if a.publish:
            publish(apk, a.notes, a.yes)
        else:
            info("\nNot published. To publish: re-run with --publish (or see AGENT_RELEASE_GUIDE.md).")
    except ReleaseError as e:
        fail(str(e))


if __name__ == "__main__":
    main()
