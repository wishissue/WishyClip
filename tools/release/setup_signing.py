#!/usr/bin/env python3
"""Create (or adopt) the release signing key, ONCE, outside the repository.

  python tools/release/setup_signing.py            create a new key in ~/.wishyclip-signing
  python tools/release/setup_signing.py --status   show what exists (never prints secrets)
  python tools/release/setup_signing.py --import-keystore /path/old.jks --alias myalias
                                                   adopt the key your existing installs were signed with

The private key and its passwords stay in the signing folder. Only the PUBLIC certificate fingerprint is
written into the repo (release/signing-cert.sha256) so every later build can prove it uses the same key.
"""
from __future__ import annotations

import argparse
import getpass
import os
import secrets
import shutil
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from common import (PINNED_FILE, ReleaseError, fail, info, keystore_fingerprint, normalize_fingerprint,  # noqa: E402
                    read_pinned, read_properties, run, signing_dir)

KEYSTORE_NAME = "wishyclip-release.p12"


def _write_private(path: Path, text: str) -> None:
    fd = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o600)
    with os.fdopen(fd, "w", encoding="utf-8") as f:
        f.write(text)
    try:
        os.chmod(path, 0o600)
    except OSError:
        pass


def _write_properties(folder: Path, store_file: str, alias: str, password: str) -> None:
    _write_private(folder / "keystore.properties",
                   f"storeFile={store_file}\nstorePassword={password}\nkeyAlias={alias}\nkeyPassword={password}\n")


def _pin(fp: str, accept_new: bool) -> None:
    pinned = read_pinned()
    if pinned is None or accept_new:
        PINNED_FILE.parent.mkdir(parents=True, exist_ok=True)
        PINNED_FILE.write_text(fp + "\n", encoding="utf-8")
        info(f"Pinned certificate fingerprint written to {PINNED_FILE.relative_to(PINNED_FILE.parents[1])} (public, commit it).")
    elif pinned != fp:
        raise ReleaseError(
            "This key is DIFFERENT from the one pinned in release/signing-cert.sha256.\n"
            "Installed copies of the app were signed with the pinned key; a release signed with another key cannot\n"
            "update them in place (users would have to uninstall and re-import projects from their folder).\n"
            "If the old key is truly lost and you accept that, re-run with --accept-new-key.")


def status(folder: Path) -> int:
    props_file = folder / "keystore.properties"
    info(f"Signing folder : {folder} ({'exists' if folder.is_dir() else 'MISSING'})")
    if not props_file.exists():
        info("keystore.properties : MISSING (run setup_signing.py)")
        return 1
    props = read_properties(props_file)
    ks = Path(props.get("storeFile", ""))
    ks = ks if ks.is_absolute() else folder / ks
    info(f"Keystore        : {ks.name} ({'exists' if ks.exists() else 'MISSING'})")
    if not ks.exists():
        return 1
    fp = keystore_fingerprint(ks, props["keyAlias"], props["storePassword"])
    pinned = read_pinned()
    info(f"Certificate     : sha256 {fp}")
    info(f"Pinned in repo  : {'(none yet)' if pinned is None else ('MATCHES' if pinned == fp else 'DIFFERENT - do not release')}")
    return 0 if pinned in (None, fp) else 2


def create(folder: Path, alias: str, dname: str, accept_new: bool) -> None:
    folder.mkdir(parents=True, exist_ok=True)
    try:
        os.chmod(folder, 0o700)
    except OSError:
        pass
    keystore = folder / KEYSTORE_NAME
    if keystore.exists() or (folder / "keystore.properties").exists():
        raise ReleaseError(f"A key already exists in {folder}. This tool never overwrites a signing key.\n"
                           "Use --status to inspect it. (To start over on purpose, move the folder away yourself.)")
    password = secrets.token_urlsafe(24).replace("-", "A").replace("_", "B")
    run(["keytool", "-genkeypair", "-storetype", "PKCS12", "-keystore", str(keystore), "-alias", alias,
         "-keyalg", "RSA", "-keysize", "4096", "-validity", "10950", "-dname", dname,
         "-storepass:env", "WISHY_STOREPASS", "-keypass:env", "WISHY_STOREPASS"],
        env={"WISHY_STOREPASS": password})
    try:
        os.chmod(keystore, 0o600)
    except OSError:
        pass
    _write_properties(folder, KEYSTORE_NAME, alias, password)
    fp = keystore_fingerprint(keystore, alias, password)
    _pin(fp, accept_new)
    _print_backup_notice(folder)


def adopt(folder: Path, source: Path, alias: str, accept_new: bool) -> None:
    if not source.is_file():
        raise ReleaseError(f"No such keystore file: {source}")
    folder.mkdir(parents=True, exist_ok=True)
    try:
        os.chmod(folder, 0o700)
    except OSError:
        pass
    if (folder / "keystore.properties").exists():
        raise ReleaseError(f"{folder}/keystore.properties already exists; not overwriting.")
    password = os.environ.get("WISHYCLIP_IMPORT_PASSWORD") or getpass.getpass("Keystore password (also used for the key): ")
    fp = keystore_fingerprint(source, alias, password)  # proves the password and alias are right
    dest = folder / source.name
    shutil.copy2(source, dest)
    try:
        os.chmod(dest, 0o600)
    except OSError:
        pass
    _write_properties(folder, dest.name, alias, password)
    _pin(fp, accept_new)
    _print_backup_notice(folder)


def _print_backup_notice(folder: Path) -> None:
    info(f"""
Key ready in {folder}

BACK IT UP NOW (this is the one thing that cannot be recreated):
  1. Copy the WHOLE folder to at least two private places (encrypted USB drive, password manager attachment).
  2. Never upload it to GitHub, never paste it in chat, never email it, never give it to a CI service.
  3. If it is lost, future releases are signed with a new key and Android will refuse to update existing
     installs in place. Projects stay safe in the project folder and can be imported again after reinstalling.
Commit release/signing-cert.sha256 (public).""")


def main() -> None:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--dir", type=Path, default=None, help="signing folder (default ~/.wishyclip-signing or $WISHYCLIP_SIGNING_DIR)")
    ap.add_argument("--status", action="store_true")
    ap.add_argument("--alias", default="wishyclip")
    ap.add_argument("--dname", default="CN=WishyClip, O=WishyClip")
    ap.add_argument("--import-keystore", type=Path, default=None)
    ap.add_argument("--accept-new-key", action="store_true", help="replace the pinned fingerprint (breaks in-place updates; human decision)")
    a = ap.parse_args()
    folder = (a.dir or signing_dir()).expanduser()
    try:
        if a.status:
            raise SystemExit(status(folder))
        if a.import_keystore:
            adopt(folder, a.import_keystore.expanduser(), a.alias, a.accept_new_key)
        else:
            create(folder, a.alias, a.dname, a.accept_new_key)
    except ReleaseError as e:
        fail(str(e))


if __name__ == "__main__":
    main()
