from pathlib import Path
from urllib.parse import urljoin, urlparse

import httpx

from ethnowear_worker_common.api.client import WorkerApiTransport
from ethnowear_worker_common.api.errors import WorkerInputError

_IMAGE_SIGNATURES = {
    "image/png": (b"\x89PNG\r\n\x1a\n",),
    "image/jpeg": (b"\xff\xd8\xff",),
    "image/tiff": (b"II*\x00", b"MM\x00*"),
}


async def download_controlled_image(
    *,
    http_client: httpx.AsyncClient,
    url: str,
    headers: dict[str, str],
    destination: Path,
    maximum_bytes: int,
) -> int:
    if maximum_bytes <= 0:
        raise ValueError("Maximum input size must be positive")

    async with http_client.stream(
        "GET",
        url,
        headers=headers,
        follow_redirects=False,
    ) as response:
        if response.status_code != 302:
            return await _write_image(response, destination, maximum_bytes)

        redirect_url = _redirect_url(response)

    # Claim credentials are deliberately not forwarded to media storage.
    async with http_client.stream(
        "GET",
        redirect_url,
        follow_redirects=False,
    ) as response:
        return await _write_image(response, destination, maximum_bytes)


def _redirect_url(response: httpx.Response) -> str:
    location = response.headers.get("Location")
    if not location:
        raise WorkerInputError("Input redirect did not provide a location")

    redirect_url = urljoin(str(response.request.url), location)
    parsed = urlparse(redirect_url)
    if parsed.scheme not in {"http", "https"} or not parsed.netloc:
        raise WorkerInputError("Input redirect location is invalid")

    return redirect_url


async def _write_image(
    response: httpx.Response,
    destination: Path,
    maximum_bytes: int,
) -> int:
    if response.status_code != 200:
        await response.aread()
        raise WorkerApiTransport._api_error(response)

    content_type = (
        response.headers.get("Content-Type", "")
        .partition(";")[0]
        .strip()
        .lower()
    )
    expected_signatures = _IMAGE_SIGNATURES.get(content_type)
    if expected_signatures is None:
        raise WorkerInputError(
            "Input response does not have a supported image content type"
        )

    content_length = response.headers.get("Content-Length")
    if content_length is not None:
        try:
            declared_size = int(content_length)
        except ValueError as error:
            raise WorkerInputError(
                "Input response has invalid content length"
            ) from error

        if declared_size <= 0 or declared_size > maximum_bytes:
            raise WorkerInputError("Input exceeds the maximum size")

    total_bytes = 0
    try:
        with destination.open("wb") as output:
            destination.chmod(0o600)
            async for chunk in response.aiter_bytes():
                total_bytes += len(chunk)
                if total_bytes > maximum_bytes:
                    raise WorkerInputError("Input exceeds the maximum size")
                output.write(chunk)

        if total_bytes == 0:
            raise WorkerInputError("Input image is empty")

        signature_length = max(len(value) for value in expected_signatures)
        with destination.open("rb") as image:
            header = image.read(signature_length)

        if not any(header.startswith(value) for value in expected_signatures):
            raise WorkerInputError("Input content does not match its image type")

        return total_bytes
    except BaseException:
        destination.unlink(missing_ok=True)
        raise
