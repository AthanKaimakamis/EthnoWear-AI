import stat
from pathlib import Path

import httpx
import pytest

from ethnowear_worker_common.api.errors import (
    WorkerApiRequestError,
    WorkerInputError,
)
from ethnowear_worker_common.input.image import download_controlled_image


@pytest.mark.asyncio
@pytest.mark.parametrize(
    ("content_type", "content"),
    [
        ("image/png", b"\x89PNG\r\n\x1a\nbody"),
        ("image/jpeg", b"\xff\xd8\xffbody"),
        ("image/tiff", b"II*\x00body"),
        ("image/tiff", b"MM\x00*body"),
    ],
)
async def test_downloads_supported_image_privately(
    tmp_path: Path,
    content_type: str,
    content: bytes,
) -> None:
    async def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(
            200,
            headers={"Content-Type": content_type},
            content=content,
        )

    destination = tmp_path / "input-image"
    async with httpx.AsyncClient(transport=httpx.MockTransport(handler)) as client:
        size = await download_controlled_image(
            http_client=client,
            url="http://api/jobs/1/input",
            headers={"Authorization": "Worker secret"},
            destination=destination,
            maximum_bytes=1024,
        )

    assert size == len(content)
    assert destination.read_bytes() == content
    assert stat.S_IMODE(destination.stat().st_mode) == 0o600


@pytest.mark.asyncio
async def test_redirect_does_not_forward_credentials(tmp_path: Path) -> None:
    requests: list[httpx.Request] = []

    async def handler(request: httpx.Request) -> httpx.Response:
        requests.append(request)
        if request.url.host == "api":
            return httpx.Response(
                302,
                headers={"Location": "http://media/page.png"},
            )
        return httpx.Response(
            200,
            headers={"Content-Type": "image/png"},
            content=b"\x89PNG\r\n\x1a\nbody",
        )

    async with httpx.AsyncClient(transport=httpx.MockTransport(handler)) as client:
        await download_controlled_image(
            http_client=client,
            url="http://api/jobs/1/input",
            headers={
                "Authorization": "Worker secret",
                "X-Worker-Claim-Token": "claim-secret",
            },
            destination=tmp_path / "input.png",
            maximum_bytes=1024,
        )

    assert len(requests) == 2
    assert requests[0].headers["Authorization"] == "Worker secret"
    assert "Authorization" not in requests[1].headers
    assert "X-Worker-Claim-Token" not in requests[1].headers


@pytest.mark.asyncio
async def test_removes_partial_file_when_size_limit_is_exceeded(
    tmp_path: Path,
) -> None:
    async def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(
            200,
            headers={"Content-Type": "image/png"},
            content=b"\x89PNG\r\n\x1a\n" + b"x" * 20,
        )

    destination = tmp_path / "input.png"
    async with httpx.AsyncClient(transport=httpx.MockTransport(handler)) as client:
        with pytest.raises(WorkerInputError, match="maximum size"):
            await download_controlled_image(
                http_client=client,
                url="http://api/jobs/1/input",
                headers={},
                destination=destination,
                maximum_bytes=10,
            )

    assert not destination.exists()


@pytest.mark.asyncio
async def test_rejects_mime_signature_mismatch(tmp_path: Path) -> None:
    async def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(
            200,
            headers={"Content-Type": "image/png"},
            content=b"not-a-png",
        )

    destination = tmp_path / "input.png"
    async with httpx.AsyncClient(transport=httpx.MockTransport(handler)) as client:
        with pytest.raises(WorkerInputError, match="does not match"):
            await download_controlled_image(
                http_client=client,
                url="http://api/jobs/1/input",
                headers={},
                destination=destination,
                maximum_bytes=1024,
            )

    assert not destination.exists()


@pytest.mark.asyncio
async def test_maps_api_error_without_writing_file(tmp_path: Path) -> None:
    async def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(
            409,
            json={"code": "STALE_CLAIM", "message": "Claim is stale"},
        )

    destination = tmp_path / "input.png"
    async with httpx.AsyncClient(transport=httpx.MockTransport(handler)) as client:
        with pytest.raises(WorkerApiRequestError) as captured:
            await download_controlled_image(
                http_client=client,
                url="http://api/jobs/1/input",
                headers={},
                destination=destination,
                maximum_bytes=1024,
            )

    assert captured.value.code == "STALE_CLAIM"
    assert not destination.exists()


@pytest.mark.asyncio
async def test_rejects_redirect_without_location(tmp_path: Path) -> None:
    async def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(302)

    async with httpx.AsyncClient(transport=httpx.MockTransport(handler)) as client:
        with pytest.raises(WorkerInputError, match="location"):
            await download_controlled_image(
                http_client=client,
                url="http://api/jobs/1/input",
                headers={},
                destination=tmp_path / "input.png",
                maximum_bytes=1024,
            )
