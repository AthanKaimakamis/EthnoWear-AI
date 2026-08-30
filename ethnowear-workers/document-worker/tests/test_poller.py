import asyncio

import httpx

from ethnowear_document_worker.api.models import ClaimResponse
from ethnowear_worker_common.jobs.poller import JobPoller

from test_api_client import claim_body


class FakeWorkerApiClient:
    def __init__(self, responses: list[ClaimResponse | None]) -> None:
        self._responses = iter(responses)
        self.claim_count = 0

    async def claim(self) -> ClaimResponse | None:
        self.claim_count += 1
        response = next(self._responses)
        if isinstance(response, Exception):
            raise response
        return response


def recording_waiter(delays: list[float]):
    async def wait(stop_event: asyncio.Event, delay: float) -> bool:
        delays.append(delay)
        return False

    return wait


def test_poller_backs_off_until_a_job_is_available() -> None:
    claimed_job = ClaimResponse.model_validate(claim_body())
    api_client = FakeWorkerApiClient([None, None, None, claimed_job])
    delays: list[float] = []
    poller = JobPoller(
        api_client=api_client,
        minimum_delay=1,
        maximum_delay=3,
        wait_for_stop=recording_waiter(delays),
        jitter=lambda delay: delay,
    )

    result = asyncio.run(poller.wait_for_job(asyncio.Event()))

    assert result is claimed_job
    assert api_client.claim_count == 4
    assert delays == [1, 2, 3]


def test_poller_does_not_claim_when_shutdown_is_already_requested() -> None:
    api_client = FakeWorkerApiClient([])
    stop_event = asyncio.Event()
    stop_event.set()
    poller = JobPoller(
        api_client=api_client,
        minimum_delay=1,
        maximum_delay=30,
    )

    result = asyncio.run(poller.wait_for_job(stop_event))

    assert result is None
    assert api_client.claim_count == 0


def test_poller_stops_when_backoff_wait_is_interrupted() -> None:
    api_client = FakeWorkerApiClient([None])
    waits: list[float] = []

    async def interrupted(stop_event: asyncio.Event, delay: float) -> bool:
        waits.append(delay)
        return True

    poller = JobPoller(
        api_client=api_client,
        minimum_delay=1,
        maximum_delay=30,
        wait_for_stop=interrupted,
        jitter=lambda delay: delay,
    )

    result = asyncio.run(poller.wait_for_job(asyncio.Event()))

    assert result is None
    assert api_client.claim_count == 1
    assert waits == [1]


def test_poller_adds_bounded_jitter_and_survives_transport_outage() -> None:
    claimed_job = ClaimResponse.model_validate(claim_body())
    api_client = FakeWorkerApiClient([
        httpx.ConnectError("backend unavailable"),
        None,
        claimed_job,
    ])
    waits: list[float] = []
    poller = JobPoller(
        api_client=api_client,
        minimum_delay=1,
        maximum_delay=4,
        wait_for_stop=recording_waiter(waits),
        jitter=lambda delay: delay * 0.9,
    )

    result = asyncio.run(poller.wait_for_job(asyncio.Event()))

    assert result is claimed_job
    assert api_client.claim_count == 3
    assert waits == [0.9, 1.8]


def test_poller_validates_delay_bounds() -> None:
    api_client = FakeWorkerApiClient([])

    for minimum, maximum in [(0, 30), (5, 4)]:
        try:
            JobPoller(api_client, minimum, maximum)
        except ValueError:
            pass
        else:
            raise AssertionError("Invalid polling bounds were accepted")
