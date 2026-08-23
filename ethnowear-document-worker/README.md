# EthnoWear Document Worker

Containerized Python worker for Spring-managed document processing. The current
scope is `PAGE_EXTRACTION`: claim a job, stream its PDF through the internal API,
inspect it, submit a page manifest, render required pages individually, upload
renditions, and report completion, cancellation, or sanitized failure.

Spring is the only SQL writer and owns permanent storage paths, page identities,
leases, retries, and lifecycle transitions. This worker never connects to SQL
Server and writes only per-job temporary files.

## Local development

Requires Python 3.13.

```shell
python3.13 -m venv .venv
.venv/bin/python -m pip install -e '.[dev]'
.venv/bin/python -m pytest -q
```

Copy `.env.example` values into the run configuration. Never commit a real API
token. Run with:

```shell
.venv/bin/python -m ethnowear_worker
```

The worker runs one job at a time. `SIGINT` and `SIGTERM` stop further claims;
an active claimed job retains heartbeat/cancellation handling until its terminal
operation or enforced backend timeout.

## Configuration

| Variable | Default | Purpose |
|---|---:|---|
| `ETHNOWEAR_WORKER_BASE_URL` | required | Spring backend URL |
| `ETHNOWEAR_WORKER_ID` | required | Stable worker instance identifier |
| `ETHNOWEAR_WORKER_API_TOKEN` | required | Shared worker credential, minimum 32 bytes |
| `ETHNOWEAR_WORKER_POLL_MIN_SECONDS` | `1` | Initial empty-queue backoff |
| `ETHNOWEAR_WORKER_POLL_MAX_SECONDS` | `30` | Maximum empty-queue backoff |
| `ETHNOWEAR_WORKER_TEMPORARY_ROOT` | `/tmp/ethnowear-worker` | Absolute temporary workspace root |
| `ETHNOWEAR_WORKER_HTTP_CONNECT_TIMEOUT_SECONDS` | `10` | HTTP connection timeout |
| `ETHNOWEAR_WORKER_HTTP_READ_TIMEOUT_SECONDS` | `60` | HTTP read/stream timeout |

## Health

`python -m ethnowear_worker.health` exits successfully only while runtime
initialization is complete and the readiness marker has secure permissions.
The marker is removed during graceful shutdown. Logs are sanitized JSON and do
not include tokens, temporary paths, response bodies, or stack traces.

OCR, Qdrant, Ollama, embeddings, chunking, and RAG are intentionally out of
scope for this worker version.
