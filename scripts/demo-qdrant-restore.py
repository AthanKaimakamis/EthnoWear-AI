#!/usr/bin/env python3
import json
import os
import pathlib
import urllib.error
import urllib.request
import uuid

base_url = os.environ.get("ETHNOWEAR_QDRANT_URL", "http://qdrant:6333").rstrip("/")
collection = os.environ.get("ETHNOWEAR_QDRANT_COLLECTION", "ethnowear_chunks_bge_m3_v1")
snapshot = pathlib.Path(os.environ.get("ETHNOWEAR_DEMO_QDRANT_SNAPSHOT", "/demo/qdrant/collection.snapshot"))

if not snapshot.is_file():
    raise SystemExit(f"Demo Qdrant snapshot is missing: {snapshot}")

try:
    with urllib.request.urlopen(f"{base_url}/collections/{collection}", timeout=15):
        print(f"Qdrant collection already exists: {collection}")
        raise SystemExit(0)
except urllib.error.HTTPError as error:
    if error.code != 404:
        raise

boundary = "----ethnowear-" + uuid.uuid4().hex
content = snapshot.read_bytes()
body = (
    f"--{boundary}\r\n"
    f'Content-Disposition: form-data; name="snapshot"; filename="{snapshot.name}"\r\n'
    "Content-Type: application/octet-stream\r\n\r\n"
).encode() + content + f"\r\n--{boundary}--\r\n".encode()
request = urllib.request.Request(
    f"{base_url}/collections/{collection}/snapshots/upload?priority=snapshot",
    data=body,
    headers={"Content-Type": f"multipart/form-data; boundary={boundary}"},
    method="POST",
)
with urllib.request.urlopen(request, timeout=180) as response:
    result = json.loads(response.read())
if result.get("status") != "ok" or result.get("result") is not True:
    raise SystemExit(f"Qdrant snapshot restore failed: {result}")
print(f"Restored Qdrant collection: {collection}")
