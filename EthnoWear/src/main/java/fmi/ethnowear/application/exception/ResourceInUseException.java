package fmi.ethnowear.application.exception;

import lombok.Getter;

@Getter
public class ResourceInUseException extends RuntimeException {

    private final String resourceType;
    private final Long resourceId;

    public ResourceInUseException(String resourceType, Long resourceId) {
        super(resourceType + " is referenced and cannot be deleted: " + resourceId);
        this.resourceType = resourceType;
        this.resourceId = resourceId;
    }

}
