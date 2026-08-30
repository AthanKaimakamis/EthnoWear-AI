package fmi.ethnowear.application.service.document.retention;

import fmi.ethnowear.domain.model.document.DocumentPageRenditionType;
import fmi.ethnowear.persistence.jpa.entity.document.DocumentPageMedia;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class GeneratedDocumentMediaPolicy {

    private static final Set<DocumentPageRenditionType> GENERATED_RENDITIONS =
            Set.of(
                    DocumentPageRenditionType.PDF_PAGE_RENDER,
                    DocumentPageRenditionType.PREPROCESSED_OCR_INPUT,
                    DocumentPageRenditionType.CROPPED,
                    DocumentPageRenditionType.DESKEWED,
                    DocumentPageRenditionType.BINARIZED,
                    DocumentPageRenditionType.SEARCHABLE_PDF_PAGE,
                    DocumentPageRenditionType.THUMBNAIL
            );

    public Set<DocumentPageRenditionType> renditionTypes() {
        return GENERATED_RENDITIONS;
    }

    public boolean isGenerated(DocumentPageMedia media) {
        return media != null
                && !media.isOriginal()
                && GENERATED_RENDITIONS.contains(media.getRenditionType());
    }
}
