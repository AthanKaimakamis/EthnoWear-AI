class WorkerApiRequestError(RuntimeError):
    def __init__(self, status_code: int, code: str, safe_message: str) -> None:
        self.status_code = status_code
        self.code = code
        super().__init__(
            f"Worker API request failed: "
            f"status={status_code}, code={code}, safe_message={safe_message}"
        )


class WorkerApiContractError(RuntimeError):
    pass


class WorkerInputError(RuntimeError):
    pass
