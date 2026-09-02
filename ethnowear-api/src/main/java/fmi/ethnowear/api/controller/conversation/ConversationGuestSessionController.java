package fmi.ethnowear.api.controller.conversation;

import fmi.ethnowear.application.model.publicauth.PublicUserPrincipal;
import fmi.ethnowear.application.service.conversation.access.ConversationGuestSessionService;
import fmi.ethnowear.infrastructure.security.conversation.ConversationGuestCookies;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.Duration;

@RestController
@RequestMapping("/api/conversations")
@RequiredArgsConstructor
public class ConversationGuestSessionController {

    private final ConversationGuestSessionService guestSessions;
    private final ConversationGuestCookies cookies;
    private final Clock clock;

    @PostMapping("/guest-session")
    public ResponseEntity<Void> obtain(@AuthenticationPrincipal PublicUserPrincipal principal,
                                       HttpServletRequest request,
                                       HttpServletResponse response) {
        if (principal != null)
            return ResponseEntity.noContent().build();

        var issued = guestSessions.obtain(cookies.read(request));
        cookies.write(response, issued.token(), Duration.between(clock.instant(), issued.expiresAt()));

        return ResponseEntity.noContent().build();
    }

}
