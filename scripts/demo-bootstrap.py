#!/usr/bin/env python3
import json
import os
import sys
import time
import urllib.error
import urllib.request

API = os.environ.get("ETHNOWEAR_DEMO_API_URL", "http://api:8080").rstrip("/")
USERNAME = os.environ.get("ETHNOWEAR_DEMO_ADMIN_USERNAME", "admin")
PASSWORD = os.environ.get("ETHNOWEAR_DEMO_ADMIN_PASSWORD", "admin")
TIMEOUT = int(os.environ.get("ETHNOWEAR_DEMO_BOOTSTRAP_TIMEOUT_SECONDS", "900"))


def request(path, method="GET", body=None, token=None):
    data = None if body is None else json.dumps(body).encode("utf-8")
    headers = {"Accept": "application/json"}
    if data is not None:
        headers["Content-Type"] = "application/json"
    if token:
        headers["Authorization"] = f"Bearer {token}"
    req = urllib.request.Request(API + path, data=data, headers=headers, method=method)
    with urllib.request.urlopen(req, timeout=30) as response:
        raw = response.read()
        return json.loads(raw) if raw else None


deadline = time.time() + TIMEOUT
while True:
    try:
        login = request("/api/auth/admin/login", "POST", {"username": USERNAME, "password": PASSWORD})
        token = login["accessToken"]
        break
    except (OSError, urllib.error.HTTPError, KeyError):
        if time.time() >= deadline:
            raise
        time.sleep(3)

documents = request("/api/admin/documents?size=100", token=token)["content"]
for document in documents:
    request(f"/api/admin/documents/{document['id']}/indexing-state/reconcile", "POST", token=token)

availability = request("/api/conversations/availability")
if not availability.get("available"):
    print("Demo data imported, but chat is not ready: " + ", ".join(availability.get("unavailableCodes", [])), file=sys.stderr)
    sys.exit(2)

print(f"Demo bootstrap complete: {len(documents)} documents reconciled and chat is available")
