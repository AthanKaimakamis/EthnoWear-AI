from ethnowear_worker.api.models import ClaimResponse
from ethnowear_worker.jobs.context import ClaimCredentials

from test_api_client import claim_body


def test_claim_credentials_are_created_from_claim_without_exposing_token() -> None:
    claim = ClaimResponse.model_validate(claim_body())

    credentials = ClaimCredentials.from_claim(claim)

    assert credentials.job_id == 11
    assert credentials.claim_token.get_secret_value() == "opaque-claim-token"
    assert "opaque-claim-token" not in repr(credentials)
