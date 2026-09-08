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
cd FinalProject
```

Create the local demo configuration:

```bash
cp .env.demo.example .env
```

Do not run this command over an existing `.env` without backing it up. The real
`.env` remains local and must never be committed.

Start the application with the demo and containerized-AI profiles:

```bash
docker compose --profile demo --profile container-ai up -d --build
```

On first startup, Ollama downloads these models into its persistent volume:

- `bge-m3` for embeddings;
- `qwen3:8b` for grounded chat generation;
- `qwen3-vl:8b` for optional vision-assisted OCR review.

Monitor the model initializer:

```bash
docker compose logs -f ollama-models
```

After the models are ready, rerun the final readiness service if its first run
reported that chat was unavailable:

```bash
docker compose --profile demo run --rm demo-bootstrap
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

Inspect service state:

```bash
docker compose --profile demo --profile container-ai ps
```

Inspect the important one-shot services:

```bash
docker compose logs database-migration
docker compose logs demo-media-bootstrap
docker compose logs demo-qdrant-bootstrap
docker compose logs demo-bootstrap
```

A successful final check reports:

```text
Demo bootstrap complete: 2 documents reconciled and chat is available
```

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

If all required models already exist in a host Ollama installation, set this in
`.env`:

```dotenv
ETHNOWEAR_OLLAMA_BASE_URL=http://host.docker.internal:11434
```

Then start without the `container-ai` profile:

```bash
docker compose --profile demo up -d --build
docker compose --profile demo run --rm demo-bootstrap
```

## Stop and restart

Stop containers while preserving database, Qdrant, Ollama, and media state:

```bash
docker compose --profile demo --profile container-ai down
```

Restart the same state:

```bash
docker compose --profile demo --profile container-ai up -d
```

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

Check whether model initialization finished:

```bash
docker compose logs ollama-models
docker compose exec ollama ollama list
```

Then rerun:

```bash
docker compose --profile demo run --rm demo-bootstrap
```

### Database contains no demo data

The BACPAC is imported only when the `EthnoWear` database does not exist. Check:

```bash
docker compose logs database-migration
```

Use a separate disposable SQL volume when validating a fresh import. Do not
delete the main development volume just to force demo initialization.

### Qdrant collection was not restored

Check:

```bash
docker compose logs demo-qdrant-bootstrap
```

The bootstrap intentionally skips restoration when the configured collection
already exists. SQL Server remains authoritative for chunk text, approval,
provenance, citations, and indexing state.

### Media checksum failure

Do not bypass the checksum validation. Re-run `./scripts/demo-export` from the
approved live state or restore the changed artifact from Git.
