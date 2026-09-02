package fmi.ethnowear.infrastructure.ontology.jena;

import fmi.ethnowear.application.model.ontology.OntologyChangeMetadata;
import fmi.ethnowear.application.port.ontology.admin.OntologyChangeMetadataProvider;
import fmi.ethnowear.application.service.auth.EthnoWearUserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Component
public class RequestOntologyChangeMetadataProvider implements OntologyChangeMetadataProvider {

    public static final String CHANGE_REASON_HEADER = "X-Ontology-Change-Reason";
    private static final int MAXIMUM_REASON_LENGTH = 500;

    @Override
    public OntologyChangeMetadata current() {
        HttpServletRequest request = currentRequest();
        String suppliedReason = request == null ? null : request.getHeader(CHANGE_REASON_HEADER);
        String reason = suppliedReason == null || suppliedReason.isBlank()
                ? defaultReason(request)
                : suppliedReason.trim();

        if (reason.length() > MAXIMUM_REASON_LENGTH)
            reason = reason.substring(0, MAXIMUM_REASON_LENGTH);

        return new OntologyChangeMetadata(currentUserId(), reason);
    }

    private Long currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null)
            return null;

        Object principal = authentication.getPrincipal();
        if (principal instanceof Jwt jwt) {
            Number userId = jwt.getClaim("userId");
            return userId == null ? null : userId.longValue();
        }
        if (principal instanceof EthnoWearUserPrincipal user)
            return user.userId();

        return null;
    }

    private HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)
            return attributes.getRequest();
        return null;
    }

    private String defaultReason(HttpServletRequest request) {
        if (request == null)
            return "Ontology system operation";
        return request.getMethod() + " " + request.getRequestURI();
    }
}
