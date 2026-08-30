FROM python:3.13-slim AS runtime

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PIP_DISABLE_PIP_VERSION_CHECK=1

WORKDIR /app

RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        tesseract-ocr \
        tesseract-ocr-bul \
        tesseract-ocr-eng \
    && tesseract --version \
    && tesseract --list-langs | grep -qx bul \
    && tesseract --list-langs | grep -qx eng \
    && tesseract --list-langs | grep -qx osd \
    && rm -rf /var/lib/apt/lists/*

RUN groupadd --system --gid 10001 worker \
    && useradd --system --uid 10001 --gid worker --home-dir /nonexistent worker \
    && mkdir -p /tmp/ethnowear-document-worker \
    && chown worker:worker /tmp/ethnowear-document-worker

COPY worker-common /build/worker-common
COPY document-worker /build/document-worker

RUN python -m pip install --no-cache-dir \
        /build/worker-common \
        /build/document-worker \
    && rm -rf /build

USER 10001:10001

HEALTHCHECK --interval=10s --timeout=3s --start-period=10s --retries=3 \
    CMD ["python", "-m", "ethnowear_document_worker.health"]

CMD ["python", "-m", "ethnowear_document_worker"]

FROM runtime AS test

USER root

RUN python -m pip install --no-cache-dir "pytest>=8,<10"

COPY document-worker/tests ./tests
COPY document-worker/pyproject.toml ./pyproject.toml

ENV ETHNOWEAR_RUN_OCR_INTEGRATION=1

RUN python -m pytest

FROM runtime AS final
