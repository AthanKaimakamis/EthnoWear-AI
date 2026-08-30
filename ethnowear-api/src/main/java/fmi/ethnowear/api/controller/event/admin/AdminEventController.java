package fmi.ethnowear.api.controller.event.admin;

import fmi.ethnowear.infrastructure.sse.ManagementEventBroadcaster;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/admin/events")
@RequiredArgsConstructor
public class AdminEventController {

    private final ManagementEventBroadcaster broadcaster;

    @Operation(
            summary = "Subscribe to management events",
            description = """
                    Streams safe document, page, processing-job and media changes.
                    Event data is only a refresh hint; REST query endpoints remain
                    authoritative.
                    """
    )
    @GetMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribe(
            @RequestHeader(
                    name = "Last-Event-ID",
                    required = false
            ) Long lastEventId
    ) {
        return broadcaster.subscribe(lastEventId);
    }

}
