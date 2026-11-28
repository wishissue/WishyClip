# WishyClip release guide (for AI agents, and for humans)

This file is the contract for building, signing, versioning and publishing WishyClip. If you are an AI agent,
**read all of it before touching `app/build.gradle.kts`, anything in `.github/`, anything in `tools/release/`, or
running any release command.** When this guide and your own idea disagree, this guide wins. When something is not
covered here, stop and ask the human.

---

## 1. The rules (never break these)

1. **The release signing key never leaves the maintainer's computer.** Not to GitHub, not to a CI service, not into
   chat, not into a file inside the repository, not into a log. Keys live in `~/.wishyclip-signing/` (or
   `$WISHYCLIP_SIGNING_DIR`), which is outside the repository.
2. **GitHub builds are test builds only.** They use the public shared debug key and the app id
   `org.wishyclip.app.debug`, and are published as **pre-releases**. GitHub never builds, signs or publishes a real release.
3. **Real releases are built locally**, by `python tools/release/build_release.py`, and nowhere else.
4. **Every real release is signed with the same key forever.** That key's certificate fingerprint is pinned in
   `release/signing-cert.sha256`. Never change that file to make a build pass.
5. **`versionCode` goes up on every release** (never reused, never lowered) and `versionName` is a plain number
   such as `1.2.0` (no `-rc1`, no `-debug`).
6. **Never overwrite, regenerate or "fix" a keystore.** If a key problem appears, stop and tell the human (section 8).
7. **Never publish by hand** (web upload, `gh release create` with other files). Only `build_release.py --publish` uploads,
   and it uploads exactly two files: `WishyClip-<version>.apk` and `WishyClip-<version>.apk.sha256`.
8. **Never lower a safety check** (`--skip-tests`, `--allow-dirty`, `--accept-new-key`, editing the scanner's patterns,
   editing the pinned fingerprint) unless the human explicitly tells you to, in this conversation, for this reason.

If a command refuses to run, the refusal is the system working. Read the message, fix the cause, never bypass it.

---

## 2. Two kinds of build

| | **Test build** (GitHub, or local debug) | **Real release** (local only) |
|---|---|---|
| Built by | `.github/workflows/test-build.yml` or `./gradlew assembleDebug` | `tools/release/build_release.py` |
| Signed with | shared public `tools/signing/debug.keystore` (password `android`) | the private release key in `~/.wishyclip-signing/` |
| App id | `org.wishyclip.app.debug` (installs *next to* the real app) | `org.wishyclip.app` |
| Name on phone | "WishyClip (debug)" | "WishyClip" |
| GitHub release type | pre-release, tag `test-runN` | normal release, tag `vX.Y.Z`, marked latest |
| Offered by the in-app updater | never | yes |
| Secrets used | none | none on GitHub; key stays on the maintainer's machine |

Because the app ids differ, a test build and the real app can never conflict over signatures; Android treats them as two apps.

---

## 3. What is where

| Path | What it is |
|---|---|
| `app/build.gradle.kts` | Signing is read from the signing folder; `onCi` disables release signing and blocks release tasks; debug has its own id. |
| `tools/release/setup_signing.py` | One-time: create the key (or adopt an existing one), pin its public fingerprint. Never overwrites. |
| `tools/release/build_release.py` | Preflight → tests → build → verify → copy to `release-out/` → optional `--publish`. |
| `tools/release/verify_apk.py` | Proves an APK is signed by the pinned key, right package, right version, right file name. |
| `tools/release/check_no_secrets.py` | Fails if a key, password or signing secret is in the repo (run by CI, hooks and the release script). |
| `tools/release/install_hooks.py` | Installs git pre-commit / pre-push hooks that run the secret check. |
| `tools/release/test_release_tools.py` | Tests for the tools. Run before changing any tool. |
| `release/signing-cert.sha256` | **Public** fingerprint of the release certificate (commit it). |
| `tools/signing/debug.keystore` | The shared **public** debug key. The only keystore allowed in the repo. |
| `.github/workflows/test-build.yml` | Debug-only pre-release builds. |
| `.github/workflows/ci.yml` | Lint, tests, debug APK, secret check, tool tests. |
| `app/src/main/java/org/wishyclip/app/update/` | The in-app updater (section 6). |
| `release-out/` | Output of `build_release.py` (git-ignored). |

---

## 4. One-time setup (on the maintainer's computer)

Needs: JDK 17+ (`keytool`), Android SDK with build-tools (`apksigner`, `aapt2`; set `ANDROID_HOME`), `git`, `gh` (logged in with `gh auth login`), Python 3.9+.

```bash
python tools/release/install_hooks.py             # blocks commits/pushes containing secrets
python tools/release/setup_signing.py             # creates ~/.wishyclip-signing + release/signing-cert.sha256
git add release/signing-cert.sha256 && git commit -m "Pin release signing certificate"
```

**If the app was already released with an existing key**, adopt that key instead of creating a new one (a new key would stop
updates for everyone already installed):

```bash
python tools/release/setup_signing.py --import-keystore /path/to/old.jks --alias <its alias>
```

Then **back up the whole `~/.wishyclip-signing/` folder to two private places** (encrypted drive, password manager).
Check the state any time (never prints secrets): `python tools/release/setup_signing.py --status`.

An AI agent normally does **not** run setup_signing.py itself: it creates the one secret that cannot be recreated. Ask the human to run it,
or run it only when the human says so.

---

## 5. Making a release (the procedure)

1. **Decide the version.** Edit `app/build.gradle.kts`: raise `versionCode` by at least 1 and set `versionName` (`1.2.0`).
   `build_release.py` rejects a `versionCode` or `versionName` not higher than the previous `v*` tag.
2. **Update `CHANGELOG.md`** and write short release notes (what users should know) into a file, e.g. `/tmp/notes.md`.
3. **Commit everything** (the release must come from a clean tree) and push the branch. Make sure CI is green.
4. **Check for secrets:** `python tools/release/check_no_secrets.py`.
5. **Build and verify locally:**
   ```bash
   python tools/release/build_release.py
   ```
   It runs: key present and matches the pinned fingerprint → no secrets → clean tree, new tag → version higher than last release →
   unit tests → `./gradlew clean assembleRelease` (signed with the local key) → `apksigner` + `aapt2` verification → writes
   `release-out/WishyClip-<version>.apk` and `.apk.sha256`.
6. **Install the APK on a real phone** that has the previous release and confirm: the app updates in place, projects are still listed, drawing works.
   (If this phone has the *debug* build, that is a different app and does not count.)
7. **Publish:**
   ```bash
   python tools/release/build_release.py --publish --notes /tmp/notes.md
   ```
   (builds again, verifies again, asks you to type `publish`, tags `vX.Y.Z`, pushes the tag, creates the release marked *latest*, and checks that the
   release holds exactly the two expected files). Agents: ask the human to confirm before `--publish`.
8. **After publishing:** open the release page and confirm it is *not* marked pre-release and shows exactly two files. On a phone with the previous
   version, start the app: the update dialog should appear within a few seconds.

Rolling back a bad release: do **not** delete the tag and re-upload under the same version. Publish a *new* higher version that fixes it.
Android will not install a lower `versionCode` over a higher one, and phones that already updated cannot go back.

---

## 6. What the in-app updater expects (the release "contract")

The updater (`update/ReleaseParser.kt`, `UpdateChecker.kt`) reads `https://api.github.com/repos/wishissue/WishyClip/releases/latest`. GitHub's
"latest" excludes drafts and pre-releases. The app then insists on **all** of:

* tag is a plain version: `v1.2.0` (anything with a suffix is ignored);
* the version is higher than the installed `versionName`;
* an asset named exactly `WishyClip-<version>.apk` (`^WishyClip-[0-9][0-9A-Za-z.]*\.apk$`, and the name must not contain "debug" or "test");
* optionally `WishyClip-<version>.apk.sha256` (sha256sum format). If present the download must match it;
* download URLs on `github.com` / `*.githubusercontent.com` over https only.

After downloading, before showing the installer, the app verifies (via the Android package manager) that the file is `org.wishyclip.app`,
has a higher `versionCode`, and is signed by the **same certificate** as the installed app. If the key differs it shows the "cannot be installed over the app"
dialog instead of the system's cryptic *App not installed*, and explains that projects are safe in the project folder.

User flow: dialog "Update available" → **Update** (download with progress → checks → Android installer, asking once for "install unknown apps"
permission) or **Later** (nothing saved: asks again on the next app start). Settings → About has a switch for the start-up check and a "Check for updates" button.

Never rename the asset, never add other `.apk` files to a release, never mark a real release as pre-release.

If you change any of these rules, change `tools/release/common.py` (`APK_NAME_RE`) **and** `ReleaseParser.kt` **and** their tests together.

---

## 7. What CI may and may not do

May: lint, unit tests, secret check, `assembleDebug`, publish **pre-release** test builds (`test-build.yml`, uses only the automatic `github.token`).

Must never: use `secrets.*` for signing, decode a keystore, pass `-Pandroid.injected.signing.*`, run `assembleRelease` / `bundleRelease`,
or publish a non-pre-release. `build.gradle.kts` throws if release tasks are requested while `CI=true`, and `check_no_secrets.py` fails on all of
those patterns. If you "need" CI to make a release, the answer is no: build locally.

---

## 8. Problem playbook

| Symptom | Cause | What to do |
|---|---|---|
| `build_release.py`: "key ... NOT the pinned release key" | wrong/new key in the signing folder | Restore the original key from backup. Do **not** edit the pinned file. |
| "No signing key in ..." | signing folder missing on this machine | Ask the human to restore it from backup, or (only if no release was ever published) run `setup_signing.py`. |
| "versionCode must be greater than ..." | forgot to bump | Raise `versionCode`/`versionName` in `app/build.gradle.kts`, commit, retry. |
| "Tag vX already exists" | version already released | Use a new version. Never reuse or move a tag. |
| "Secrets found in the repository" | key/password/secret in a tracked file | Remove it, `git rm --cached` it, make sure `.gitignore` covers it. If it was committed, see "key leaked" below. |
| "CI is set" | running the release script in a CI-like shell | Unset `CI`; releases are local. |
| `apksigner not found` | no Android build-tools | Install them, set `ANDROID_HOME`. |
| Phone says "App not installed" | different signing key, lower `versionCode`, or a debug build on top of release (or vice versa) | Compare `apksigner verify --print-certs`; debug and release are different apps by design, so this should only happen with a wrong key. |
| App never shows the update dialog | release is draft/pre-release, tag has a suffix, wrong asset name, app is a debug build, or the start-up check is switched off in Settings | Check the release page against section 6. Debug builds never self-update. |
| Update dialog says "cannot be installed over the app" | the APK is signed with a different key than the installed app | The user keeps all projects (they are in their project folder): install the new version and choose the same folder. Fix the cause in the build if it was a mistake. |

**Key lost** (no backup anywhere): nothing can restore it. Tell the human plainly: Android will not update existing installs in place with a new key.
Steps, with the human's explicit approval: `python tools/release/setup_signing.py --accept-new-key` (new key, new pinned fingerprint), release as
usual, and say in the release notes that this version must be installed fresh and that projects come back by choosing the same project folder. Installed
apps will show the "cannot be installed over the app" dialog with exactly that explanation.

**Key leaked** (it appeared in a commit, a log, a chat): treat it as public. Remove it from the repo and from git history if the human wants that, stop publishing with
it, tell the human. A new key has the same consequence as "key lost". Do not quietly continue.

Check history for past leaks: `python tools/release/check_no_secrets.py --history`.

---

## 9. Checklist before saying "released"

- [ ] `python tools/release/check_no_secrets.py` passes
- [ ] `python -m unittest discover -s tools/release -p "test_*.py"` passes
- [ ] `build_release.py` finished without errors and printed `OK: ... signed by the pinned release key`
- [ ] the APK was installed over the previous release on a real phone and projects were still there
- [ ] the GitHub release is not a pre-release, is marked latest, and holds exactly `WishyClip-<v>.apk` and `.apk.sha256`
- [ ] nothing from `~/.wishyclip-signing/` was printed, copied or committed
- [ ] the previous version, started on a phone, shows the update dialog

Be honest in your report about which of these you actually did. Parts that need a phone or the maintainer's key cannot be verified from a sandbox.

---

## 10. Prompt to give an AI agent for release work

> You are preparing a release of WishyClip. First read `AGENT_RELEASE_GUIDE.md` completely and follow it exactly. The signing key lives only on this
> computer in `~/.wishyclip-signing/` and must never be printed, copied, committed, uploaded or placed in any CI secret. GitHub only builds debug test
> builds as pre-releases; real releases come only from `python tools/release/build_release.py`. Bump `versionCode` and `versionName`, update
> `CHANGELOG.md`, run the secret check and the tool tests, build and verify with `build_release.py`, and then stop and show me the result. Publish with
> `--publish` only after I confirm. If any check refuses to pass, report the exact message and the cause; never bypass, weaken or edit a check, and never
> create or replace a key without my explicit instruction.
