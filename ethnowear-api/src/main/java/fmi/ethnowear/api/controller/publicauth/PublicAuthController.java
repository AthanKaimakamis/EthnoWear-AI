package fmi.ethnowear.api.controller.publicauth;

import fmi.ethnowear.application.dto.publicauth.*;
import fmi.ethnowear.application.model.publicauth.PublicUserPrincipal;
import fmi.ethnowear.application.service.publicauth.PublicAuthenticationService;
import fmi.ethnowear.config.PublicAuthProperties;
import fmi.ethnowear.infrastructure.security.publicauth.PublicAuthCookies;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/public/auth")
@Tag(name = "Public authentication", description = "Optional Google login; isolated from management accounts")
@RequiredArgsConstructor
public class PublicAuthController {
    private final PublicAuthenticationService service;
    private final PublicAuthProperties properties;
    private final PublicAuthCookies cookies;

    @GetMapping("/config")
    public ResponseEntity<PublicAuthConfigDetails> config() {
        return noStore(new PublicAuthConfigDetails(properties.enabled(), properties.enabled() ? properties.googleClientId() : null));
    }

    @GetMapping("/csrf")
    @Operation(summary = "Get CSRF token for public cookie-authenticated requests")
    public ResponseEntity<PublicCsrfDetails> csrf(CsrfToken token) {
        return noStore(new PublicCsrfDetails(token.getHeaderName(), token.getToken()));
    }

    @PostMapping("/google/challenge")
    @Operation(summary = "Create a one-use Google nonce", security = @SecurityRequirement(name = "publicCsrf"), description = "Requires X-PUBLIC-CSRF. Pass nonce to Google Identity Services; expires in five minutes.")
    public ResponseEntity<PublicLoginChallengeDetails> challenge(HttpServletRequest request, HttpServletResponse response) {
        var challenge = service.challenge(PublicAuthCookies.read(request, PublicAuthCookies.NONCE));
        cookies.write(response, PublicAuthCookies.NONCE, challenge.nonce(), PublicAuthenticationService.CHALLENGE_TTL);
        return noStore(challenge);
    }

    @PostMapping("/google")
    @Operation(summary = "Exchange a Google ID token for a public session", security = @SecurityRequirement(name = "publicCsrf"), description = "Requires challenge cookie and X-PUBLIC-CSRF. Never creates management roles. Session is returned only in an HttpOnly cookie.")
    public ResponseEntity<PublicUserDetails> login(
            @Valid @RequestBody PublicGoogleLoginCommand command,
            HttpServletRequest request, HttpServletResponse response
    ) {
        var result = service.login(command.credential(), PublicAuthCookies.read(request, PublicAuthCookies.NONCE),
                PublicAuthCookies.read(request, PublicAuthCookies.SESSION));
        cookies.write(response, PublicAuthCookies.SESSION, result.token(), properties.sessionTtl());
        cookies.clear(response, PublicAuthCookies.NONCE);
        return noStore(result.user());
    }

    @GetMapping("/me")
    @Operation(security = @SecurityRequirement(name = "publicSession"))
    public ResponseEntity<PublicUserDetails> me(@AuthenticationPrincipal PublicUserPrincipal principal) {
        return noStore(service.current(principal));
    }

    @PostMapping("/logout")
    @Operation(summary = "Revoke the current public session", security = @SecurityRequirement(name = "publicCsrf"), description = "Idempotent. Requires X-PUBLIC-CSRF; does not affect management login or other devices.")
    public ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        service.logout(PublicAuthCookies.read(request, PublicAuthCookies.SESSION));
        cookies.clear(response, PublicAuthCookies.SESSION);
        cookies.clear(response, PublicAuthCookies.NONCE);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    private <T> ResponseEntity<T> noStore(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }
}
