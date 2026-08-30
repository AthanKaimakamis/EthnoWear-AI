package fmi.ethnowear.application.service.auth;

import fmi.ethnowear.persistence.jpa.entity.user.User;
import fmi.ethnowear.persistence.jpa.repository.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Component
@RequiredArgsConstructor
public class JwtUserStateValidator implements OAuth2TokenValidator<Jwt> {

    private static final OAuth2Error INVALID_TOKEN = new OAuth2Error(
            OAuth2ErrorCodes.INVALID_TOKEN,
            "The token is no longer valid",
            null
    );

    private final UserRepository userRepository;
    private final Clock clock;

    @Override
    public OAuth2TokenValidatorResult validate(@NonNull Jwt jwt) {
        Number userId = jwt.getClaim("userId");
        Number tokenVersion = jwt.getClaim("tokenVersion");
        Boolean mustChangePassword = jwt.getClaim("mustChangePassword");

        if (userId == null || tokenVersion == null || mustChangePassword == null)
            return failure();

        User user = userRepository.findById(userId.longValue())
                .orElse(null);

        if (user == null
                || !user.isEnabled()
                || !user.getUsername().equals(jwt.getSubject())
                || user.getTokenVersion() != tokenVersion.intValue()
                || user.isMustChangePassword() != mustChangePassword)
            return failure();

        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);

        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(now))
            return failure();

        if (user.isMustChangePassword() && (
                user.getTemporaryPasswordExpiresAt() == null
                        || !user.getTemporaryPasswordExpiresAt().isAfter(now)
        ))
            return failure();

        return OAuth2TokenValidatorResult.success();
    }

    private OAuth2TokenValidatorResult failure() {
        return OAuth2TokenValidatorResult.failure(INVALID_TOKEN);
    }
}
