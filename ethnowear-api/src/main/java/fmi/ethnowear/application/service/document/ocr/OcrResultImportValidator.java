package fmi.ethnowear.application.service.document.ocr;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import fmi.ethnowear.application.model.document.ocr.OcrResultPayload;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OcrResultImportValidator {

    private final Validator validator;
    private final ObjectMapper objectMapper;

    public void validate(OcrResultPayload command) {
        if (command == null)
            throw new IllegalArgumentException("OCR result import command is required");

        var violations = validator.validate(command);

        if (!violations.isEmpty())
            throw new ConstraintViolationException(violations);

        validateJsonObject(command.parametersJson(), "OCR parameters");
        validateJson(command.structuredOutputJson(), "OCR structured output");
    }

    private void validateJsonObject(String value, String fieldName) {
        JsonNode json = parseJson(value, fieldName);

        if (json != null && !json.isObject())
            throw new IllegalArgumentException(fieldName + " must be a JSON object");
    }

    private void validateJson(String value, String fieldName) {
        parseJson(value, fieldName);
    }

    private JsonNode parseJson(String value, String fieldName) {
        if (value == null)
            return null;

        if (value.isBlank())
            throw new IllegalArgumentException(fieldName + " cannot be blank");

        try {
            return objectMapper.readTree(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException(fieldName + " must contain valid JSON", ex);
        }
    }
}
