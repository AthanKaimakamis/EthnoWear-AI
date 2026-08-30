FROM python:3.13-slim AS runtime

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PIP_DISABLE_PIP_VERSION_CHECK=1

WORKDIR /app

RUN groupadd --system --gid 10001 worker \
    && useradd --system --uid 10001 --gid worker --home-dir /nonexistent worker \
    && mkdir -p /tmp/ethnowear-indexing-worker \
    && chown worker:worker /tmp/ethnowear-indexing-worker

COPY worker-common /build/worker-common
COPY indexing-worker /build/indexing-worker

RUN python -m pip install --no-cache-dir \
        /build/worker-common \
        /build/indexing-worker \
    && rm -rf /build

USER 10001:10001

HEALTHCHECK --interval=10s --timeout=3s --start-period=10s --retries=3 \
    CMD ["python", "-m", "ethnowear_indexer.health"]

CMD ["python", "-m", "ethnowear_indexer"]

FROM runtime AS test

USER root

RUN python -m pip install --no-cache-dir \
    "pytest>=8,<10" \
    "pytest-asyncio>=1,<2"

COPY indexing-worker/tests ./tests

RUN python -m pytest

FROM runtime AS final
