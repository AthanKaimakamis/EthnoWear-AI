package fmi.ethnowear.application.service.document.upload;

import fmi.ethnowear.domain.model.document.DocumentPageRenditionType;
import fmi.ethnowear.persistence.jpa.entity.MediaAsset;
import fmi.ethnowear.persistence.jpa.entity.document.DocumentPage;
import fmi.ethnowear.persistence.jpa.entity.document.DocumentPageMedia;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;

@Component
public class DocumentPageMediaFactory {

    public @NonNull DocumentPageMedia createOriginal(
            @NonNull DocumentPage page,
            @NonNull MediaAsset mediaAsset,
            String notes
    ) {
        return create(
                page,
                mediaAsset,
                DocumentPageRenditionType.ORIGINAL_UPLOAD,
                true,
                true,
                0,
                notes
        );
    }

    public @NonNull DocumentPageMedia createReplacement(
            @NonNull DocumentPage page,
            @NonNull MediaAsset mediaAsset,
            boolean preferredOcrInput,
            int displayOrder,
            String notes
    ) {
        return create(
                page,
                mediaAsset,
                DocumentPageRenditionType.REPLACEMENT_SCAN,
                false,
                preferredOcrInput,
                displayOrder,
                notes
        );
    }

    private @NonNull DocumentPageMedia create(
            DocumentPage page,
            MediaAsset mediaAsset,
            DocumentPageRenditionType renditionType,
            boolean original,
            boolean preferredOcrInput,
            int displayOrder,
            String notes
    ) {
        DocumentPageMedia rendition = new DocumentPageMedia();

        rendition.setDocumentPage(page);
        rendition.setMediaAsset(mediaAsset);
        rendition.setRenditionType(renditionType);
        rendition.setOriginal(original);
        rendition.setPreferredOcrInput(preferredOcrInput);
        rendition.setDisplayOrder(displayOrder);
        rendition.setWidth(mediaAsset.getWidth());
        rendition.setHeight(mediaAsset.getHeight());
        rendition.setRenditionHash(mediaAsset.getChecksum());
        rendition.setNotes(notes);

        return rendition;
    }
}
