package fmi.ethnowear.api.controller.conversation;

import fmi.ethnowear.application.dto.conversation.*;
import fmi.ethnowear.application.exception.ConversationException;
import fmi.ethnowear.application.model.conversation.ConversationOwner;
import fmi.ethnowear.application.model.publicauth.PublicUserPrincipal;
import fmi.ethnowear.application.service.conversation.*;
import fmi.ethnowear.application.service.conversation.access.ConversationOwnerResolver;
import fmi.ethnowear.infrastructure.security.conversation.ConversationGuestCookies;
import fmi.ethnowear.infrastructure.sse.ConversationEventBroadcaster;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/conversations")
@RequiredArgsConstructor
public class ConversationController {

    private final ConversationService conversations;
    private final ConversationOwnerResolver owners;
    private final ConversationGuestCookies guestCookies;
    private final ConversationEventBroadcaster conversationEvents;
    private final ConversationAvailabilityService availability;

    @GetMapping("/availability")
    public ConversationAvailabilityDetails availability() {
        return availability.check();
    }

    @GetMapping
    public Page<ConversationDetails> list(@AuthenticationPrincipal PublicUserPrincipal principal,
                                          HttpServletRequest request,
                                          @PageableDefault(size = 20) Pageable pageable
    ) {
        return conversations.list(
                requireOwner(principal, request),
                pageable
        );
    }

    @GetMapping("/{conversationId}")
    public ConversationDetails get(@AuthenticationPrincipal PublicUserPrincipal principal,
                                   HttpServletRequest request,
                                   @PathVariable UUID conversationId
    ) {
        return conversations.get(
                requireOwner(principal, request),
                conversationId
        );
    }

    @PatchMapping("/{conversationId}")
    public ConversationDetails rename(@AuthenticationPrincipal PublicUserPrincipal principal,
                                      HttpServletRequest request,
                                      @PathVariable UUID conversationId,
                                      @Valid @RequestBody ConversationRenameCommand command
    ) {
        return conversations.rename(
                requireOwner(principal, request),
                conversationId,
                command
        );
    }

    @DeleteMapping("/{conversationId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal PublicUserPrincipal principal,
                       HttpServletRequest request,
                       @PathVariable UUID conversationId
    ) {
        conversations.delete(
                requireOwner(principal, request),
                conversationId
        );
    }

    @GetMapping("/{conversationId}/turns")
    public Page<ConversationTurnDetails> listTurns(@AuthenticationPrincipal PublicUserPrincipal principal,
                                                   HttpServletRequest request,
                                                   @PathVariable UUID conversationId,
                                                   @PageableDefault(size = 20) Pageable pageable
    ) {
        return conversations.listTurns(
                requireOwner(principal, request),
                conversationId,
                pageable
        );
    }

    @GetMapping("/{conversationId}/turns/{turnId}")
    public ConversationTurnDetails getTurn(@AuthenticationPrincipal PublicUserPrincipal principal,
                                           HttpServletRequest request,
                                           @PathVariable UUID conversationId,
                                           @PathVariable UUID turnId
    ) {
        return conversations.getTurn(
                requireOwner(principal, request),
                conversationId,
                turnId
        );
    }

    @PostMapping
    public ResponseEntity<ConversationDetails> create(@AuthenticationPrincipal PublicUserPrincipal principal,
                                                      HttpServletRequest request,
                                                      @Valid @RequestBody ConversationCreateCommand command
    ) {
        var owner = requireOwner(principal, request);

        var result = conversations.create(owner, command);

        return ResponseEntity
                .created(URI.create("/api/conversations/" + result.conversationId()))
                .body(result);
    }

    @PostMapping("/{conversationId}/turns")
    public ResponseEntity<ConversationTurnAcceptedDetails> submit(@AuthenticationPrincipal PublicUserPrincipal principal,
                                                                  HttpServletRequest request,
                                                                  @PathVariable UUID conversationId,
                                                                  @Valid @RequestBody ConversationMessageCommand command
    ) {
        var accepted = conversations.submit(
                requireOwner(principal, request),
                conversationId,
                command
        );

        return ResponseEntity.accepted()
                .location(URI.create(
                        "/api/conversations/" + accepted.conversationId()
                                + "/turns/" + accepted.turnId()
                ))
                .body(accepted);
    }

    @GetMapping("/{conversationId}/turns/{turnId}/events")
    public List<ConversationProgressDetails> progress(@AuthenticationPrincipal PublicUserPrincipal principal,
                                                      HttpServletRequest request,
                                                      @PathVariable UUID conversationId,
                                                      @PathVariable UUID turnId,
                                                      @RequestParam(defaultValue = "0") long afterEventId
    ) {
        return conversations.progress(
                requireOwner(principal, request),
                conversationId,
                turnId,
                afterEventId
        );
    }

    @GetMapping(
            value = "/{conversationId}/turns/{turnId}/stream",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE
    )
    public SseEmitter stream(@AuthenticationPrincipal PublicUserPrincipal principal,
                             HttpServletRequest request,
                             @PathVariable UUID conversationId,
                             @PathVariable UUID turnId,
                             @RequestHeader(
                                     name = "Last-Event-ID",
                                     required = false,
                                     defaultValue = "0"
                             ) long afterEventId
    ) {
        var target = conversations.progressTarget(
                requireOwner(principal, request),
                conversationId,
                turnId
        );

        return conversationEvents.subscribe(target, afterEventId);
    }

    @PostMapping("/{conversationId}/turns/{turnId}/cancel")
    public ConversationTurnDetails cancel(@AuthenticationPrincipal PublicUserPrincipal principal,
                                          HttpServletRequest request,
                                          @PathVariable UUID conversationId,
                                          @PathVariable UUID turnId
    ) {
        return conversations.cancel(
                requireOwner(principal, request),
                conversationId,
                turnId
        );
    }

    private @NonNull ConversationOwner requireOwner(PublicUserPrincipal principal, HttpServletRequest request) {
        return owners.resolve(principal, guestCookies.read(request))
                .orElseThrow(() -> new ConversationException(
                        HttpStatus.UNAUTHORIZED,
                        "CONVERSATION_OWNER_REQUIRED",
                        "Public login or guest session is required"
                ));
    }
}
