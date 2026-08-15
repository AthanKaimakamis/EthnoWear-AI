package fmi.ethnowear.api.util;

import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

public final class ResponseUtil {

    private ResponseUtil() { }

    public static <T> ResponseEntity<T> created(T body, Object identifier) {
        URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{identifier}")
                .buildAndExpand(identifier)
                .encode()
                .toUri();

        return ResponseEntity.created(location).body(body);
    }

}
