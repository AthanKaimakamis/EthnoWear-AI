package fmi.ethnowear.application.model.worker;

public record WorkerClaimCredentials(
        String workerId,
        String claimToken
) {

    public WorkerClaimCredentials {
        if(workerId == null || workerId.isBlank())
            throw new IllegalArgumentException("Worker id is required");

        if(claimToken == null || claimToken.isBlank())
            throw new IllegalArgumentException("Claim token is required");
    }
}
