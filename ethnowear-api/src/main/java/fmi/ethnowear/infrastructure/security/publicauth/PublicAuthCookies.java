package fmi.ethnowear.infrastructure.security.publicauth;

import fmi.ethnowear.config.PublicAuthProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@RequiredArgsConstructor
public class PublicAuthCookies {
    public static final String SESSION = "ETHNOWEAR_PUBLIC_SESSION";
    public static final String NONCE = "ETHNOWEAR_GOOGLE_NONCE";
    private final PublicAuthProperties properties;

    public static String read(HttpServletRequest request, String name) {
        String value = null;
        if (request.getCookies() != null) {
            for (var cookie : request.getCookies()) {
                if (name.equals(cookie.getName())) {
                    if (value != null)
                        return null;
                    value = cookie.getValue();
                }
            }
        }
        return value;
    }

    public void write(HttpServletResponse response, String name, String value, Duration maxAge) {
        response.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from(name, value)
                .httpOnly(true).secure(properties.secureCookies()).sameSite("Lax")
                .path("/api").maxAge(maxAge).build().toString());
    }

    public void clear(HttpServletResponse response, String name) {
        write(response, name, "", Duration.ZERO);
    }
}
