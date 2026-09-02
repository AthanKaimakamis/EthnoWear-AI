package fmi.ethnowear.infrastructure.security.conversation;

import fmi.ethnowear.config.PublicAuthProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@RequiredArgsConstructor
public class ConversationGuestCookies {

    public static final String COOKIE_NAME = "ETHNOWEAR_GUEST_SESSION";

    private final PublicAuthProperties properties;

    public String read(@NonNull HttpServletRequest request) {
        if (request.getCookies() == null)
            return null;

        String result = null;

        for (var cookie : request.getCookies()) {
            if (!COOKIE_NAME.equals(cookie.getName()))
                continue;

            if (result != null)
                return null;

            result = cookie.getValue();
        }

        return result;
    }

    public void write(@NonNull HttpServletResponse response, String token, Duration maxAge) {
        response.addHeader(
                HttpHeaders.SET_COOKIE,
                ResponseCookie.from(COOKIE_NAME, token)
                        .httpOnly(true)
                        .secure(properties.secureCookies())
                        .sameSite("Lax")
                        .path("/api/conversations")
                        .maxAge(maxAge)
                        .build()
                        .toString()
        );
    }
}
