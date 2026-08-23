package fmi.ethnowear.application.service.auth;

import fmi.ethnowear.application.dto.auth.AdminTokenDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TokenRefreshService {

    private final UserDetailsService userDetailsService;
    private final JwtTokenService tokenService;

    public AdminTokenDetails refresh(Long userId, String username) {
        var details = userDetailsService.loadUserByUsername(username);
        if (!(details instanceof EthnoWearUserPrincipal principal)
                || !principal.userId().equals(userId)
                || !principal.isEnabled()
                || !principal.isAccountNonLocked()
                || !principal.isCredentialsNonExpired())
            throw new BadCredentialsException("The session cannot be refreshed");

        return tokenService.issue(principal);
    }
}
