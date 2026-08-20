package fmi.ethnowear.api.controller.user.admin;

import org.jspecify.annotations.NonNull;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;

import fmi.ethnowear.application.dto.user.*;
import fmi.ethnowear.application.service.user.*;
import fmi.ethnowear.domain.model.user.RoleName;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/users")
@PreAuthorize("hasRole('ADMINISTRATOR')")
public class AdminUserController {

    private final UserQueryService queryService;
    private final UserManagementService managementService;

    @GetMapping
    public Page<UserSummaryDetails> findAll(
            @RequestParam(required = false) String search,
            Pageable pageable
    ) {
        return queryService.findAll(search, pageable);
    }

    @GetMapping("/{userId}")
    public UserDetails findById(@PathVariable Long userId) {
        return queryService.findById(userId);
    }

    @PostMapping
    public ResponseEntity<CreatedUserDetails> create(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody UserCreateCommand command
    ) {
        CreatedUserDetails result = managementService.create(command, userId(jwt));

        URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{userId}")
                .buildAndExpand(result.user().id())
                .toUri();

        return ResponseEntity.created(location).body(result);
    }

    @PutMapping("/{userId}/profile")
    public UserDetails updateProfile(
            @PathVariable Long userId,
            @Valid @RequestBody UserProfileCommand command
    ) {
        return managementService.updateProfile(userId, command);
    }

    @PutMapping("/{userId}/roles/{role}")
    public UserDetails assignRole(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long userId,
            @PathVariable RoleName role
    ) {
        return managementService.assignRole(userId, role, userId(jwt));
    }

    @DeleteMapping("/{userId}/roles/{role}")
    public UserDetails removeRole(
            @PathVariable Long userId,
            @PathVariable RoleName role
    ) {
        return managementService.removeRole(userId, role);
    }

    @PostMapping("/{userId}/enable")
    public UserDetails enable(@PathVariable Long userId) {
        return managementService.enable(userId);
    }

    @PostMapping("/{userId}/disable")
    public UserDetails disable(@PathVariable Long userId) {
        return managementService.disable(userId);
    }

    @PostMapping("/{userId}/unlock")
    public UserDetails unlock(@PathVariable Long userId) {
        return managementService.unlock(userId);
    }

    @PostMapping("/{userId}/reset-password")
    public TemporaryPasswordDetails resetPassword(@PathVariable Long userId) {
        return managementService.resetPassword(userId);
    }

    private @NonNull Long userId(@NonNull Jwt jwt) {
        Number userId = jwt.getClaim("userId");

        if (userId == null)
            throw new IllegalArgumentException("Authenticated user id is missing");

        return userId.longValue();
    }
}
