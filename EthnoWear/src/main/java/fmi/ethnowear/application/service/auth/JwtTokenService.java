package fmi.ethnowear.application.service.auth;

import fmi.ethnowear.application.dto.auth.AdminTokenDetails;
import fmi.ethnowear.config.JwtProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class JwtTokenService {

    private static final String ROLE_PREFIX = "ROLE_";

    private final JwtEncoder encoder;
    private final JwtProperties properties;
    private final Clock clock;

    public AdminTokenDetails issue(Authentication authentication) {
        if(!(authentication.getPrincipal() instanceof EthnoWearUserPrincipal principal))
            throw new IllegalArgumentException("Database user principal is required");

        return issue(principal);
    }

    public AdminTokenDetails issue(EthnoWearUserPrincipal principal) {

        Instant issuedAt = clock.instant();
        Instant expiresAt = issuedAt.plus(properties.ttl());

        List<String> roles = principal.mustChangePassword()
                ? List.of()
                : principal.roles()
                .stream()
                .map(Enum::name)
                .sorted()
                .toList();

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.issuer())
                .subject(principal.username())
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .claim("userId", principal.userId())
                .claim("roles", roles)
                .claim("tokenVersion", principal.tokenVersion())
                .claim(
                        "mustChangePassword",
                        principal.mustChangePassword()
                )
                .build();

        JwsHeader headers = JwsHeader
                .with(MacAlgorithm.HS256)
                .type("JWT")
                .build();

        String token = encoder.encode(
                JwtEncoderParameters.from(headers, claims)
        ).getTokenValue();

        return new AdminTokenDetails(
                token,
                "Bearer",
                properties.ttl().toSeconds(),
                expiresAt,
                principal.mustChangePassword()
        );
    }
}
