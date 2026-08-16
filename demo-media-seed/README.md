# EthnoWear demo media seed

This directory is the only location intended for versioned demonstration media. Runtime files belong in `media/`, which is ignored by Git and bind-mounted into the backend container.

Every seed binary must be small, redistribution-compatible, and listed in `manifest.tsv` with its SHA-256 checksum, license, media type, target category, and original source. The importer rejects missing files and checksum mismatches.

The included verification PDF was generated specifically for this repository and is dedicated to the public domain under CC0-1.0. It contains no cultural claim and exists only to prove the deterministic import path.

Run `./scripts/import-demo-media.sh` while the backend is running. Set `ETHNOWEAR_ADMIN_USERNAME`, `ETHNOWEAR_ADMIN_PASSWORD`, and optionally `ETHNOWEAR_API_ROOT`.
