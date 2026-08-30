package fmi.ethnowear.application.service.document.metadata;

import fmi.ethnowear.application.dto.document.command.metadata.DocumentPageMetadataUpdateCommand;
import fmi.ethnowear.application.exception.ResourceNotFoundException;
import fmi.ethnowear.application.model.event.ManagementEvent;
import fmi.ethnowear.application.service.document.review.DocumentPageChunkInvalidator;
import fmi.ethnowear.application.service.event.ManagementEventPublisher;
import fmi.ethnowear.persistence.jpa.entity.document.DocumentPage;
import fmi.ethnowear.persistence.jpa.repository.document.DocumentPageRepository;
import fmi.ethnowear.util.RowVersionUtils;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Service
@RequiredArgsConstructor
public class DocumentPageMetadataService {

    private final DocumentPageRepository pageRepository;
    private final DocumentPageChunkInvalidator chunkInvalidator;
    private final ManagementEventPublisher managementEvents;
    private final Validator validator;

    @Transactional
    public void update(
            Long documentId,
            Long pageId,
            String versionToken,
            DocumentPageMetadataUpdateCommand command
    ) {
        if(documentId == null)
            throw new IllegalArgumentException("Document id is required");

        if(pageId == null)
            throw new IllegalArgumentException("Document page id is required");

        validate(command);

        DocumentPage page = pageRepository
                .findByIdAndDocument_Id(pageId, documentId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Document page",
                        pageId
                ));
        RowVersionUtils.requireMatch(page.getRowVersion(), versionToken);

        String printedPageNumber = trimToNull(command.printedPageNumber());
        String pageLabel = trimToNull(command.pageLabel());

        if(Objects.equals(page.getPrintedPageNumber(), printedPageNumber)
                && Objects.equals(page.getPrintedPageSort(), command.printedPageSort())
                && Objects.equals(page.getPageLabel(), pageLabel))
            return;

        page.setPrintedPageNumber(printedPageNumber);
        page.setPrintedPageSort(command.printedPageSort());
        page.setPageLabel(pageLabel);

        chunkInvalidator.invalidate(page);
        pageRepository.save(page);
        managementEvents.page(page, ManagementEvent.Action.UPDATED);
    }

    private void validate(DocumentPageMetadataUpdateCommand command) {
        if(command == null)
            throw new IllegalArgumentException("Document page metadata command is required");

        var violations = validator.validate(command);

        if(!violations.isEmpty())
            throw new ConstraintViolationException(violations);
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank()
                ? null
                : value.trim();
    }
}
