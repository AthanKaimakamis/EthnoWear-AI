package fmi.ethnowear.application.service.worker.security;

import fmi.ethnowear.application.model.worker.WorkerClaimToken;
import fmi.ethnowear.util.ContentHashUtils;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.Base64;

@Service
public class WorkerClaimTokenService {

    private static final int TOKEN_BYTES = 32;

    private final SecureRandom secureRandom;

    public WorkerClaimTokenService() {
        this(new SecureRandom());
    }

    WorkerClaimTokenService(SecureRandom secureRandom) {
        this.secureRandom = secureRandom;
    }

    public WorkerClaimToken generate() {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);

        String value = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(bytes);

        return new WorkerClaimToken(value, ContentHashUtils.sha256(value)
        );
    }
}
