package fmi.ethnowear.config;

import fmi.ethnowear.api.controller.archive.admin.AdminAuthController;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecurityConfigTest {

    @Test
    void createsConfiguredAdministratorWithEncodedPassword() {
        SecurityConfig config = new SecurityConfig();
        PasswordEncoder encoder = config.passwordEncoder();
        UserDetailsService users = config.adminUsers("admin", "secret", encoder);

        UserDetails administrator = users.loadUserByUsername("admin");

        assertTrue(encoder.matches("secret", administrator.getPassword()));
        assertTrue(administrator.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_ADMIN")));
    }

    @Test
    void verificationEndpointReturnsNoContentAfterSecurityAllowsRequest() {
        assertEquals(
                HttpStatus.NO_CONTENT,
                new AdminAuthController().verify().getStatusCode()
        );
    }
}
