"""Tests for the release tools. Run:  python -m unittest discover -s tools/release -p "test_*.py"

Uses the real `keytool` (any JDK) for the key tests and a throw-away git repository for the secret scan.
Never touches the real signing folder or the real pinned fingerprint.
"""
from __future__ import annotations

import os
import shutil
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import check_no_secrets  # noqa: E402
import common  # noqa: E402
import setup_signing  # noqa: E402

HAS_KEYTOOL = shutil.which("keytool") is not None
REAL_DEBUG_KEYSTORE = common.REPO / "tools" / "signing" / "debug.keystore"


def git(cwd: Path, *args: str) -> None:
    subprocess.run(["git", *args], cwd=cwd, check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)


class VersionTests(unittest.TestCase):
    def test_parse_version(self):
        text = 'defaultConfig {\n  versionCode = 12\n  versionName = "1.4.2"\n}'
        self.assertEqual(common.parse_version(text), ("1.4.2", 12))

    def test_parse_version_missing(self):
        with self.assertRaises(common.ReleaseError):
            common.parse_version("nothing here")

    def test_version_order(self):
        self.assertLess(common.version_tuple("v1.9.0"), common.version_tuple("v1.10.0"))
        self.assertEqual(common.version_tuple("1.2.0-debug"), (1, 2, 0))

    def test_fingerprint_normalisation(self):
        raw = ":".join(["AB"] * 32)
        self.assertEqual(common.normalize_fingerprint(raw), "ab" * 32)
        self.assertEqual(common.normalize_fingerprint("SHA256: " + raw), "ab" * 32)
        with self.assertRaises(common.ReleaseError):
            common.normalize_fingerprint("abcd")

    def test_apk_name_rule_matches_the_app_updater(self):
        self.assertTrue(common.APK_NAME_RE.match("WishyClip-1.2.0.apk"))
        self.assertFalse(common.APK_NAME_RE.match("app-release.apk"))
        self.assertFalse(common.APK_NAME_RE.match("WishyClip-1.2.0-debug.apk"))

    def test_real_gradle_file_is_readable(self):
        name, code = common.current_version()
        self.assertRegex(name, r"^\d+(\.\d+)*$")
        self.assertGreater(code, 0)


class SecretScanTests(unittest.TestCase):
    def setUp(self):
        self.tmp = Path(tempfile.mkdtemp())
        git(self.tmp, "init", "-q")
        git(self.tmp, "config", "user.email", "t@example.com")
        git(self.tmp, "config", "user.name", "t")

    def tearDown(self):
        shutil.rmtree(self.tmp, ignore_errors=True)

    def write(self, rel: str, text: str = "x") -> None:
        p = self.tmp / rel
        p.parent.mkdir(parents=True, exist_ok=True)
        p.write_text(text)

    def test_clean_repo_passes(self):
        self.write("app/Main.kt", "fun main() {}")
        self.write("app/build.gradle.kts", 'storePassword = keystoreProperties.getProperty("storePassword")\nstorePassword = "android"')
        self.assertEqual(check_no_secrets.scan(self.tmp), [])

    def test_key_files_are_flagged(self):
        for name in ("release.jks", "my.keystore", "a/b/key.p12", "keystore.properties", "cert.pem"):
            self.write(name)
        found = " ".join(check_no_secrets.scan(self.tmp))
        for name in ("release.jks", "my.keystore", "key.p12", "keystore.properties", "cert.pem"):
            self.assertIn(name, found)

    def test_ci_secret_use_is_flagged(self):
        self.write(".github/workflows/r.yml", "env:\n  P: ${{ secrets.KEYSTORE_PASSWORD }}\n")
        self.assertTrue(any("CI secret" in p for p in check_no_secrets.scan(self.tmp)))

    def test_injected_signing_is_flagged(self):
        self.write(".github/workflows/r.yml", "run: ./gradlew assembleRelease -Pandroid.injected.signing.store.file=x")
        self.assertTrue(check_no_secrets.scan(self.tmp))

    def test_password_in_properties_is_flagged(self):
        self.write("local/signing.properties", "storePassword=hunter2hunter2\n")
        self.write("gradle.properties", "storePassword=hunter2hunter2\n")
        self.assertTrue(any("gradle.properties" in p for p in check_no_secrets.scan(self.tmp)))

    def test_private_key_block_is_flagged(self):
        self.write("notes.kt", "val k = \"\"\"-----BEGIN PRIVATE KEY-----\nabc\n\"\"\"")
        self.assertTrue(any("private key" in p for p in check_no_secrets.scan(self.tmp)))

    def test_gitignored_files_are_not_scanned(self):
        self.write(".gitignore", "*.jks\n")
        self.write("local.jks")
        self.assertEqual(check_no_secrets.scan(self.tmp), [])

    @unittest.skipUnless(HAS_KEYTOOL, "needs keytool")
    def test_public_debug_keystore_is_allowed_but_a_private_one_at_that_path_is_not(self):
        self.write(check_no_secrets.DEBUG_KEYSTORE)
        (self.tmp / check_no_secrets.DEBUG_KEYSTORE).unlink()
        shutil.copy(REAL_DEBUG_KEYSTORE, self.tmp / check_no_secrets.DEBUG_KEYSTORE)
        self.assertEqual(check_no_secrets.scan(self.tmp), [])
        (self.tmp / check_no_secrets.DEBUG_KEYSTORE).unlink()
        subprocess.run(["keytool", "-genkeypair", "-keystore", str(self.tmp / check_no_secrets.DEBUG_KEYSTORE),
                        "-storepass", "not-the-public-one", "-keypass", "not-the-public-one", "-alias", "k",
                        "-keyalg", "RSA", "-keysize", "2048", "-validity", "10", "-dname", "CN=x"],
                       check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        self.assertTrue(any("public debug password" in p for p in check_no_secrets.scan(self.tmp)))

    def test_the_real_repository_is_clean(self):
        self.assertEqual(check_no_secrets.scan(common.REPO), [])


@unittest.skipUnless(HAS_KEYTOOL, "needs keytool")
class SigningSetupTests(unittest.TestCase):
    def setUp(self):
        self.tmp = Path(tempfile.mkdtemp())
        self.pinned = self.tmp / "repo" / "release" / "signing-cert.sha256"
        self._old = (common.PINNED_FILE, setup_signing.PINNED_FILE)
        common.PINNED_FILE = setup_signing.PINNED_FILE = self.pinned
        # setup_signing prints relative to PINNED_FILE.parents[1]; make that exist
        self.pinned.parent.mkdir(parents=True)

    def tearDown(self):
        common.PINNED_FILE, setup_signing.PINNED_FILE = self._old
        shutil.rmtree(self.tmp, ignore_errors=True)

    def test_create_pins_the_key_and_refuses_to_overwrite(self):
        folder = self.tmp / "sign"
        setup_signing.create(folder, "wishyclip", "CN=Test", accept_new=False)
        props = common.read_properties(folder / "keystore.properties")
        self.assertEqual(props["keyAlias"], "wishyclip")
        self.assertGreaterEqual(len(props["storePassword"]), 24)
        fp = common.keystore_fingerprint(folder / props["storeFile"], "wishyclip", props["storePassword"])
        self.assertEqual(common.read_pinned(), fp)
        if os.name == "posix":
            self.assertEqual((folder / "keystore.properties").stat().st_mode & 0o077, 0)
        with self.assertRaises(common.ReleaseError):
            setup_signing.create(folder, "wishyclip", "CN=Test", accept_new=False)

    def test_a_different_key_is_refused_unless_explicitly_accepted(self):
        setup_signing.create(self.tmp / "one", "wishyclip", "CN=One", accept_new=False)
        first = common.read_pinned()
        with self.assertRaises(common.ReleaseError):
            setup_signing.create(self.tmp / "two", "wishyclip", "CN=Two", accept_new=False)
        self.assertEqual(common.read_pinned(), first)
        setup_signing.create(self.tmp / "three", "wishyclip", "CN=Three", accept_new=True)
        self.assertNotEqual(common.read_pinned(), first)

    def test_wrong_password_is_reported(self):
        setup_signing.create(self.tmp / "k", "wishyclip", "CN=Test", accept_new=False)
        ks = self.tmp / "k" / setup_signing.KEYSTORE_NAME
        with self.assertRaises(common.ReleaseError):
            common.keystore_fingerprint(ks, "wishyclip", "wrong")


if __name__ == "__main__":
    unittest.main()
