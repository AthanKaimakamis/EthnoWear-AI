package fmi.ethnowear.application.model.document.ocr;

import java.math.BigDecimal;

public interface OcrResultPayload {

    String rawText();

    String ocrEngine();

    String ocrEngineVersion();

    String ocrLanguage();

    BigDecimal ocrConfidence();

    String parametersJson();

    String structuredOutputJson();
}
