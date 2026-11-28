`signing-cert.sha256` is the **public** SHA-256 fingerprint of the certificate the real releases are signed with.
It is not a secret. The release tools compare every build against it, so a build signed with the wrong key can
never be published (it would stop in-place updates for everyone). Created by `tools/release/setup_signing.py`.
