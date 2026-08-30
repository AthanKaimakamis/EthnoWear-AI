package fmi.ethnowear.application.service.document.query.mapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import fmi.ethnowear.application.dto.document.query.suggestion.DocumentPageTextSuggestionDetails;
import fmi.ethnowear.application.dto.document.query.suggestion.TextSuggestionIssueDetails;
import fmi.ethnowear.application.dto.document.query.suggestion.TextSuggestionUncertainPassageDetails;
import fmi.ethnowear.persistence.jpa.entity.document.DocumentPageTextSuggestion;
import fmi.ethnowear.util.ContentHashUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class DocumentPageTextSuggestionMapper {

    private final ObjectMapper objectMapper;

    public DocumentPageTextSuggestionDetails toDetails(
            DocumentPageTextSuggestion suggestion,
            boolean applied
    ) {
        return new DocumentPageTextSuggestionDetails(
                suggestion.getId(),
                suggestion.getDocumentPage().getId(),
                suggestion.getDocumentPageMedia().getId(),
                suggestion.getDocumentPageOcrResult().getId(),
                suggestion.getProcessingJob().getId(),
                suggestion.getSuggestedText(),
                suggestion.getModelName(),
                suggestion.getModelVersion(),
                suggestion.getPromptVersion(),
                suggestion.isRequiresReview(),
                editableTextHash(suggestion),
                issues(suggestion.getIssuesJson()),
                uncertainPassages(suggestion.getUncertainPassagesJson()),
                applied,
                suggestion.isRequiresReview() && !applied,
                suggestion.getCreatedAt()
        );
    }

    private String editableTextHash(DocumentPageTextSuggestion suggestion) {
        String correctedText = suggestion.getDocumentPage().getCorrectedText();

        if (correctedText != null && !correctedText.isBlank())
            return ContentHashUtils.sha256(correctedText);

        return ContentHashUtils.sha256(
                suggestion.getDocumentPageOcrResult().getRawText()
        );
    }

    private List<TextSuggestionIssueDetails> issues(String issuesJson) {
        try {
            return objectMapper.readValue(issuesJson, new TypeReference<>() { });
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException(
                    "Stored text-suggestion issues are invalid",
                    ex
            );
        }
    }

    private List<TextSuggestionUncertainPassageDetails> uncertainPassages(
            String uncertainPassagesJson
    ) {
        try {
            return objectMapper.readValue(
                    uncertainPassagesJson,
                    new TypeReference<>() { }
            );
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException(
                    "Stored uncertain passages are invalid",
                    ex
            );
        }
    }
}
