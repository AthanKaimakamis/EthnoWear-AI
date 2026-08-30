from dataclasses import dataclass

from pydantic import SecretStr

from ethnowear_worker_common.api.models import ClaimResponse


@dataclass(frozen=True, slots=True)
class ClaimCredentials:
    job_id: int
    claim_token: SecretStr

    def __post_init__(self) -> None:
        if self.job_id <= 0:
            raise ValueError("Job ID must be positive")

    @classmethod
    def from_claim(cls, claim: ClaimResponse) -> "ClaimCredentials":
        return cls(job_id=claim.job_id, claim_token=claim.claim_token)
