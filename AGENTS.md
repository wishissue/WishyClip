# Notes for AI coding agents

* **Before building, signing, versioning or publishing anything, read [AGENT_RELEASE_GUIDE.md](AGENT_RELEASE_GUIDE.md).** It is binding.
* Never create, copy, print, upload or commit a signing key, keystore password, or `keystore.properties`.
* Never add signing secrets to a GitHub workflow. Real releases are built locally with `python tools/release/build_release.py`.
* Run `python tools/release/check_no_secrets.py` before every commit that touches build, CI or release files.
