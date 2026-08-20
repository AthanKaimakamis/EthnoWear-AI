package fmi.ethnowear.application.service.auth;

import fmi.ethnowear.application.dto.auth.AdminLoginRequest;
import fmi.ethnowear.application.dto.auth.AdminTokenDetails;
import fmi.ethnowear.application.exception.InvalidAdminCredentialsException;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AdminAuthenticationService {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenService tokenService;
    private final LoginAttemptService loginAttemptService;

    public AdminTokenDetails login(@NonNull AdminLoginRequest request) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken
                            .unauthenticated(
                                    request.username(),
                                    request.password()
                            )
            );

            EthnoWearUserPrincipal principal = requirePrincipal(authentication);

            loginAttemptService.recordSuccess(principal.userId());

            return tokenService.issue(authentication);
        } catch (AuthenticationException ex) {
            loginAttemptService.recordFailure(request.username());
            throw new InvalidAdminCredentialsException();
        }
    }

    private EthnoWearUserPrincipal requirePrincipal(@NonNull Authentication authentication) {
        if (authentication.getPrincipal() instanceof EthnoWearUserPrincipal principal)
            return principal;

        throw new AuthenticationServiceException("Database user principal is required");
    }
}
