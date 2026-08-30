FROM python:3.13-slim AS runtime

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PIP_DISABLE_PIP_VERSION_CHECK=1

WORKDIR /app

RUN apt-get update \
    && apt-get install -y --no-install-recommends hunspell-bg \
    && test -s /usr/share/hunspell/bg_BG.dic \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system --gid 10001 worker \
    && useradd --system --uid 10001 --gid worker --home-dir /nonexistent worker \
    && mkdir -p /tmp/ethnowear-quality-worker \
    && chown worker:worker /tmp/ethnowear-quality-worker

COPY worker-common /build/worker-common
COPY quality-worker /build/quality-worker

RUN python -m pip install --no-cache-dir \
        /build/worker-common \
        /build/quality-worker \
    && rm -rf /build

USER 10001:10001

HEALTHCHECK --interval=10s --timeout=3s --start-period=10s --retries=3 \
    CMD ["python", "-m", "ethnowear_quality_worker.health"]

CMD ["python", "-m", "ethnowear_quality_worker"]

FROM runtime AS test
USER root
RUN python -m pip install --no-cache-dir "pytest>=8,<10"
COPY quality-worker/tests ./tests
RUN python -m pytest

FROM runtime AS final
