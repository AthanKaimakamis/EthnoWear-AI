# EthnoWear demo deployment guide

This guide deploys the prepared `demo-v1` state on a new machine. The package
contains a sanitized SQL Server database, managed PDFs and images, and a Qdrant
collection snapshot for immediate grounded retrieval.

## Included demo state

- Two document workflows with 259 SQL knowledge chunks.
- One prepared Qdrant collection containing the eligible indexed vectors.
- Archive records, ontology links, source references, OCR results, reviews,
  processing history, figures, and managed media.
- A demonstration administrator: `admin` / `admin`.

Saved conversations, public accounts, Google identities, login challenges, and
sessions are removed from the distributable database. Other management users
are anonymized and disabled while their IDs remain available for audit history.

## Prerequisites

- Git.
- Docker Desktop or Docker Engine with Compose v2.
- Enough disk space for SQL Server, application images, media, and Ollama
  models. The model downloads are substantially larger than the Git checkout.
- On Apple Silicon, enough memory for the `linux/amd64` SQL Server container.

## First deployment

Clone the repository and enter its root directory:

```bash
git clone <repository-url>
cd EthnoWear-AI
```

### macOS

Run with containerized Ollama:

```bash
./scripts/deployment/macos/run
```

Run with an existing native Ollama installation:

```bash
./scripts/deployment/macos/run-local
```

`run` starts containerized Ollama by default. `run-local` requires the three
models in the host Ollama installation on port `11434` and does not start the
Ollama Compose services.

### Windows

Run the equivalent PowerShell entry point with containerized Ollama:

```powershell
.\scripts\deployment\windows\run.ps1
```

Or use host Ollama:

```powershell
.\scripts\deployment\windows\run-local.ps1
```

If the machine's PowerShell execution policy blocks local scripts, start the
entry point for that process with:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\deployment\windows\run.ps1
```

## Deployment lifecycle

On the first run, the main entry point executes these numbered stages:

1. `1_prepare` creates `.env` from `.env.demo.example` when it is missing,
   verifies Docker and checks every packaged SQL, Qdrant, and media checksum.
2. `2_compose` builds and starts the existing `compose.yml` stack.
3. `3_migrate` waits for the one-shot migration and restoration containers and
   requires successful exit codes. It does not run them a second time.
4. `4_cleanup` removes completed migration, restoration, model-initialization,
   and readiness containers without deleting persistent data.

The stages can be run individually on macOS:

```bash
./scripts/deployment/macos/1_prepare
./scripts/deployment/macos/2_compose container
./scripts/deployment/macos/3_migrate container
./scripts/deployment/macos/4_cleanup
```

Windows provides matching `1_prepare.ps1`, `2_compose.ps1`, `3_migrate.ps1`,
and `4_cleanup.ps1` scripts.

Deployment state is recorded in:

```text
.deployment/status.env
.deployment/logs/1_PREPARE.log
.deployment/logs/2_COMPOSE.log
.deployment/logs/3_MIGRATION.log
.deployment/logs/4_CLEANUP.log
```

The directory is excluded from Git. A successful run records every stage and
`DEPLOYMENT_STATUS=complete`. If a stage fails, the next main run retries the
full lifecycle. When the same demo version is already complete, the main run
only composes the persistent services; it does not recreate migration or
bootstrap containers.

To force the complete lifecycle again, use:

```bash
./scripts/deployment/macos/deploy-full
./scripts/deployment/macos/deploy-ollama-local
```

The corresponding Windows commands are `deploy-full.ps1` and
`deploy-ollama-local.ps1`.

The generated `.env` remains local and is never overwritten by later runs.
Review it before exposing the application beyond a local demonstration.

## Ollama models

Containerized Ollama downloads these models into its persistent volume:

- `bge-m3` for embeddings;
- `qwen3:8b` for grounded chat generation;
- `qwen3-vl:8b` for optional vision-assisted OCR review.

The lifecycle waits for the model initializer before declaring success. Its
output is retained in `.deployment/logs/2_COMPOSE.log` and
`.deployment/logs/3_MIGRATION.log`.

```bash
docker compose logs -f ollama-models
```

## What Compose restores

The deployment follows this sequence:

1. SQL Server starts with its persistent volume.
2. On a fresh volume, `database-migration` imports the sanitized demo BACPAC.
3. The current DACPAC is published over the imported database.
4. `demo-media-bootstrap` verifies `media.sha256` and copies managed files into
   the host-mounted media directory.
5. `demo-qdrant-bootstrap` restores the configured collection when it is absent.
6. The API and workers start.
7. `demo-bootstrap` verifies administrator login, reconciles document indexing
   states, and checks grounded-chat availability.

If the `EthnoWear` database or Qdrant collection already exists, the demo
bootstrap does not overwrite it. Use a genuinely clean environment when testing
the distributable state.

## Verify the deployment

Inspect the persistent service state:

```bash
docker compose ps
```

The successful cleanup stage removes one-shot containers, so inspect their
preserved lifecycle logs rather than expecting them in Docker Desktop:

```bash
cat .deployment/status.env
tail -n 50 .deployment/logs/3_MIGRATION.log
tail -n 50 .deployment/logs/4_CLEANUP.log
```

A successful deployment records:

```text
PREPARE_STATUS=complete
COMPOSE_STATUS=complete
MIGRATION_STATUS=complete
CLEANUP_STATUS=complete
DEPLOYMENT_STATUS=complete
```

The clean macOS/native-Ollama verification produced two restored documents,
259 SQL knowledge chunks, 258 Qdrant points, a successful `admin` login, and
available grounded chat.

Open:

- Frontend: <http://localhost:5173>
- Backend: <http://localhost:8080>
- API documentation: <http://localhost:8080/swagger-ui/index.html>
- Qdrant dashboard: <http://localhost:6333/dashboard>

Management credentials:

```text
Username: admin
Password: admin
```

These credentials are intentionally insecure and are suitable only for the
prepared local demonstration.

## Suggested presentation flow

1. Browse the public archive and open an entry with media and source details.
2. Open the completed document and show its pages, OCR, review history, figures,
   chunks, and indexing state.
3. Open the document prepared for a new workflow and start its next processing
   step from the management interface.
4. Ask a Bulgarian question in public chat and show the grounded answer,
   citations, page references, and related cards.
5. Ask a follow-up question to demonstrate retained conversational context.
6. Show the deterministic ontology and archive evidence behind the result.

## Use a native Ollama installation

Before using the local entry point, verify the host installation:

```bash
ollama list
curl -f http://localhost:11434/api/tags
```

Required models are `bge-m3`, `qwen3:8b`, and `qwen3-vl:8b`. Then run:

```bash
./scripts/deployment/macos/run-local
```

The script configures application containers to use
`http://host.docker.internal:11434` and stops an old Compose-managed Ollama
container when switching modes.

## Stop and restart

Stop containers while preserving database, Qdrant, Ollama, and media state:

```bash
docker compose --profile demo --profile container-ai down
```

Restart the same state through the lifecycle-aware entry point:

```bash
./scripts/deployment/macos/run
```

Use `run-local` instead when the deployment should continue with host Ollama.

Do not use `docker compose down --volumes` against a development environment.
That command removes persistent database, Qdrant, and Ollama volumes. A complete
reset should only be performed in an explicitly disposable demo installation.

## Capture a newer demo release

After curating and validating a newer live state, run:

```bash
./scripts/demo-export
```

The exporter does not sanitize the live database directly. It:

1. Creates an isolated temporary database copy.
2. Removes conversations and public authentication data from that copy.
3. Anonymizes non-admin management accounts.
4. Resets the exported administrator to `admin` / `admin`.
5. Writes `EthnoWearDB/Demo/EthnoWear-demo-v1.bacpac`.
6. Copies managed media and regenerates `demo-state/media.sha256`.
7. Captures the configured Qdrant collection snapshot.
8. Updates the release manifest and artifact checksums.
9. Removes the temporary database copy.

Review the resulting changes before committing:

```bash
git status --short
git diff --check
shasum -a 256 -c demo-state/artifacts.sha256
(cd demo-state && shasum -a 256 -c media.sha256)
```

## Troubleshooting

### Chat is unavailable

Inspect the recorded migration/readiness output:

```bash
tail -n 100 .deployment/logs/3_MIGRATION.log
```

For host Ollama, verify the models with `ollama list`. For containerized Ollama,
force the lifecycle to recreate and verify its initializer:

```bash
./scripts/deployment/macos/deploy-full
```

### Database contains no demo data

The BACPAC is imported only when the `EthnoWear` database does not exist. Check
the preserved Compose and migration logs:

```bash
rg -n "Importing EthnoWear demo database|database-migration" \
  .deployment/logs/2_COMPOSE.log .deployment/logs/3_MIGRATION.log
```

Use a separate disposable SQL volume when validating a fresh import. Do not
delete the main development volume just to force demo initialization.

### Qdrant collection was not restored

Check the preserved Compose log:

```bash
rg -n "Restored Qdrant collection|demo-qdrant-bootstrap" \
  .deployment/logs/2_COMPOSE.log
```

The bootstrap intentionally skips restoration when the configured collection
already exists. SQL Server remains authoritative for chunk text, approval,
provenance, citations, and indexing state.

### Media checksum failure

Do not bypass the checksum validation. Re-run `./scripts/demo-export` from the
approved live state or restore the changed artifact from Git.
