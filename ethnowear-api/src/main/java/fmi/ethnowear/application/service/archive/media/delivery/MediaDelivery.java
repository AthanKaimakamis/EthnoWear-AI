package fmi.ethnowear.application.service.archive.media.delivery;

import org.springframework.core.io.Resource;

import java.net.URI;

public sealed interface MediaDelivery {

    record Local(
            Resource resource,
            String mimeType,
            String fileName,
            long contentLength
    ) implements MediaDelivery {
    }

    record Redirect(
            URI location
    ) implements MediaDelivery {
    }
}
