package fmi.ethnowear.api.controller.auth;

import fmi.ethnowear.application.dto.auth.*;
import fmi.ethnowear.application.service.auth.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class AdminAuthController {

    private final AdminAuthenticationService authenticationService;
    private final PasswordChangeService passwordChangeService;
    private final CurrentUserService currentUserService;
    private final TokenRefreshService tokenRefreshService;

    @PostMapping({
            "/api/auth/login",
            "/api/auth/admin/login"
    })
    public AdminTokenDetails login(@Valid @RequestBody AdminLoginRequest request) {
        return authenticationService.login(request);
    }

    @GetMapping("/api/auth/me")
    @Operation(security = @SecurityRequirement(name = "bearerAuth"))
    public CurrentUserDetails currentUser(@AuthenticationPrincipal Jwt jwt) {
        return currentUserService.getCurrentUser(userId(jwt));
    }

    @PostMapping("/api/auth/refresh")
    @Operation(security = @SecurityRequirement(name = "bearerAuth"))
    public AdminTokenDetails refresh(@AuthenticationPrincipal Jwt jwt) {
        return tokenRefreshService.refresh(userId(jwt), jwt.getSubject());
    }

    @PostMapping("/api/auth/password/change")
    @Operation(security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<Void> changePassword(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody PasswordChangeCommand command
    ) {
        passwordChangeService.changePassword(userId(jwt), command);
        return ResponseEntity.noContent().build();
    }

    private Long userId(Jwt jwt) {
        Number userId = jwt.getClaim("userId");

        if (userId == null)
            throw new IllegalArgumentException("Authenticated user id is missing");

        return userId.longValue();
    }
}
