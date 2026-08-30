package fmi.ethnowear.application.service.document.query.mapper;

import com.fasterxml.jackson.databind.ObjectMapper;
import fmi.ethnowear.persistence.jpa.entity.MediaAsset;
import fmi.ethnowear.persistence.jpa.entity.document.*;
import fmi.ethnowear.testutil.EntityTestUtils;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DocumentPageTextSuggestionMapperTest {

    @Test
    void returnsBoundedStructuredVisionOutput() {
        DocumentPage page = identified(new DocumentPage(), 21L);
        DocumentPageMedia pageMedia = identified(new DocumentPageMedia(), 31L);
        pageMedia.setDocumentPage(page);
        pageMedia.setMediaAsset(identified(new MediaAsset(), 41L));
        DocumentPageOcrResult ocrResult = identified(
                new DocumentPageOcrResult(),
                51L
        );
        ocrResult.setRawText("the old text");
        DocumentProcessingJob job = identified(new DocumentProcessingJob(), 61L);
        DocumentPageTextSuggestion suggestion = identified(
                new DocumentPageTextSuggestion(),
                71L
        );
        suggestion.setDocumentPage(page);
        suggestion.setDocumentPageMedia(pageMedia);
        suggestion.setDocumentPageOcrResult(ocrResult);
        suggestion.setProcessingJob(job);
        suggestion.setSuggestedText("Suggested text");
        suggestion.setModelName("vision-model");
        suggestion.setModelVersion("1.0");
        suggestion.setPromptVersion("prompt-v1");
        suggestion.setRequiresReview(true);
        suggestion.setIssuesJson("""
                [{"issueType":"OCR_WORD",
                  "explanationBg":"Възможна OCR корекция",
                  "confidence":0.9000,
                  "originalText":"old","originalContext":"the old text",
                  "suggestedText":"new","suggestedContext":"the new text",
                  "startOffset":4,"endOffset":7,"safelyApplicable":true}]
                """);
        suggestion.setUncertainPassagesJson("""
                [{"excerpt":"uncertain line","reason":"Low contrast","confidence":0.6000}]
                """);

        var details = new DocumentPageTextSuggestionMapper(new ObjectMapper())
                .toDetails(suggestion, false);

        assertTrue(details.requiresReview());
        assertEquals(64, details.editableTextHash().length());
        assertTrue(details.requiresHumanAttention());
        assertEquals(1, details.issues().size());
        assertEquals("0.9000", details.issues().getFirst().confidence().toPlainString());
        assertEquals("OCR_WORD", details.issues().getFirst().issueType());
        assertEquals("Възможна OCR корекция",
                details.issues().getFirst().explanationBg());
        assertEquals(4, details.issues().getFirst().startOffset());
        assertTrue(details.issues().getFirst().safelyApplicable());
        assertEquals(1, details.uncertainPassages().size());
        assertEquals("Low contrast", details.uncertainPassages().getFirst().reason());
    }

    private <T extends fmi.ethnowear.persistence.jpa.entity.AppendOnlyEntity> T identified(
            T entity,
            Long id
    ) {
        EntityTestUtils.setId(entity, id);
        return entity;
    }
}
