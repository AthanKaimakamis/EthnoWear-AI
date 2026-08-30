from ethnowear_worker_common.api.errors import WorkerApiContractError


class VisionContractError(WorkerApiContractError):
    def __init__(self, stage: str, message: str, *, diagnostics=None) -> None:
        super().__init__(message)
        self.stage = stage
        self.diagnostics = diagnostics
