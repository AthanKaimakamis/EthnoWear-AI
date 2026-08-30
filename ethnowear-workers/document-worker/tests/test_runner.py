import asyncio

import httpx

from ethnowear_worker_common.jobs.runner import WorkerRunner


class FakePoller:
    def __init__(self, claims) -> None:
        self.claims = list(claims)
        self.calls = 0

    async def wait_for_job(self, stop_event):
        self.calls += 1
        return self.claims.pop(0) if self.claims else None


class FakeProcessor:
    def __init__(self, stop_event=None, stop_after=None) -> None:
        self.processed = []
        self.stop_event = stop_event
        self.stop_after = stop_after

    async def process(self, claim) -> None:
        self.processed.append(claim)
        if self.stop_after == len(self.processed):
            self.stop_event.set()


def test_runner_processes_claims_sequentially_until_no_job() -> None:
    poller = FakePoller(["job-1", "job-2", None])
    processor = FakeProcessor()

    asyncio.run(WorkerRunner(poller, processor).run(asyncio.Event()))

    assert processor.processed == ["job-1", "job-2"]
    assert poller.calls == 3


def test_runner_does_not_claim_another_job_after_shutdown() -> None:
    stop_event = asyncio.Event()
    poller = FakePoller(["job-1", "must-not-be-claimed"])
    processor = FakeProcessor(stop_event=stop_event, stop_after=1)

    asyncio.run(WorkerRunner(poller, processor).run(stop_event))

    assert processor.processed == ["job-1"]
    assert poller.calls == 1


def test_runner_does_not_poll_when_already_stopped() -> None:
    stop_event = asyncio.Event()
    stop_event.set()
    poller = FakePoller(["must-not-be-claimed"])
    processor = FakeProcessor()

    asyncio.run(WorkerRunner(poller, processor).run(stop_event))

    assert processor.processed == []
    assert poller.calls == 0


def test_runner_survives_processor_transport_failure() -> None:
    stop_event = asyncio.Event()
    poller = FakePoller(["failed-job", "next-job"])

    class RecoveringProcessor:
        def __init__(self) -> None:
            self.processed = []

        async def process(self, claim) -> None:
            self.processed.append(claim)
            if claim == "failed-job":
                raise httpx.ConnectError("backend unavailable")
            stop_event.set()

    processor = RecoveringProcessor()

    asyncio.run(WorkerRunner(poller, processor).run(stop_event))

    assert processor.processed == ["failed-job", "next-job"]
    assert poller.calls == 2
