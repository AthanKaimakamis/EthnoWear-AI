# EthnoWear demo state

This directory contains the reproducible `demo-v1` release state.

- `../EthnoWearDB/Demo/EthnoWear-demo-v1.bacpac` is a sanitized export of SQL Server.
- `media/` mirrors the managed media paths referenced by the database.
- `media.sha256` verifies every packaged media file.
- `artifacts.sha256` verifies the BACPAC and Qdrant snapshot.
- `qdrant/collection.snapshot` is a rebuildable startup cache for the configured collection.
- `manifest.env` records the dataset and vector-index contracts.

The export removes conversations, guest sessions, public identities, login
challenges, and public sessions. Non-admin management identities are anonymized
and disabled while their IDs remain available for audit relationships. The demo
administrator is reset to `admin` / `admin` only in the isolated export copy.
The live database is never modified.

Qdrant volume storage is not included. A collection snapshot makes first startup
immediate; SQL remains authoritative and the collection can be discarded and
rebuilt from approved knowledge chunks.

Capture the current live state:

```bash
./scripts/demo-export
```

Start a clean demo environment:

```bash
cp .env.demo.example .env
docker compose --profile demo --profile container-ai up -d --build
```

The first model download can take significant time. To use an existing native
Ollama installation, omit `--profile container-ai` and configure
`ETHNOWEAR_OLLAMA_BASE_URL`.
