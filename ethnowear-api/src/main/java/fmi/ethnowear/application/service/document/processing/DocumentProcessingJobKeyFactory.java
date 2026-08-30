package fmi.ethnowear.application.service.document.processing;

import fmi.ethnowear.domain.model.document.processing.JobType;
import org.springframework.stereotype.Component;

import java.util.UUID;

import static fmi.ethnowear.util.IdentifierUtils.requireId;

@Component
public class DocumentProcessingJobKeyFactory {

    public String forDocument(JobType jobType, Long documentId) {
        requireJobType(jobType);
        requireId(documentId, "Document");

        return jobType.name() + ":DOCUMENT:" + documentId;
    }

    public String forPage(JobType jobType, Long pageId) {
        requireJobType(jobType);
        requireId(pageId, "Document page");

        return jobType.name() + ":PAGE:" + pageId;
    }

    public String forChunk(JobType jobType, Long chunkId) {
        requireJobType(jobType);
        requireId(chunkId, "Knowledge chunk");

        return jobType.name() + ":CHUNK:" + chunkId;
    }

    public String forRecreatedPageJob(
            JobType jobType,
            Long pageId,
            UUID jobIdentity
    ) {
        if (jobIdentity == null)
            throw new IllegalArgumentException("Job identity is required");

        return forPage(jobType, pageId) + ":JOB:" + jobIdentity;
    }

    public String forOcrResult(Long ocrResultId) {
        requireId(ocrResultId, "OCR result");

        return JobType.OCR_QUALITY_ASSESSMENT.name()
                + ":OCR_RESULT:"
                + ocrResultId;
    }

    public String forVisionEvidence(
            Long ocrResultId,
            Long deterministicAssessmentId
    ) {
        requireId(ocrResultId, "OCR result");
        requireId(deterministicAssessmentId, "Deterministic assessment");

        return JobType.VISION_OCR_ASSESSMENT.name()
                + ":OCR_RESULT:"
                + ocrResultId
                + ":QUALITY_ASSESSMENT:"
                + deterministicAssessmentId;
    }

    public String forActiveVisionReview(Long pageId, Long ocrResultId) {
        requireId(pageId, "Document page");
        requireId(ocrResultId, "OCR result");

        return JobType.VISION_OCR_ASSESSMENT.name()
                + ":PAGE:"
                + pageId
                + ":OCR_RESULT:"
                + ocrResultId;
    }

    public String forFigureExtraction(Long pageId, Long ocrResultId) {
        requireId(pageId, "Document page");
        requireId(ocrResultId, "OCR result");

        return JobType.EXTRACT_PAGE_FIGURES.name()
                + ":PAGE:"
                + pageId
                + ":OCR_RESULT:"
                + ocrResultId;
    }

    public Long figureOcrResultId(String jobKey) {
        String marker = ":OCR_RESULT:";

        if (jobKey == null
                || !jobKey.startsWith(JobType.EXTRACT_PAGE_FIGURES.name() + ":PAGE:")
                || !jobKey.contains(marker))
            throw new IllegalArgumentException(
                    "Figure-extraction job has no OCR-result target"
            );

        try {
            String target = jobKey.substring(jobKey.indexOf(marker) + marker.length());
            int separator = target.indexOf(':');
            return Long.valueOf(separator < 0 ? target : target.substring(0, separator));
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(
                    "Figure-extraction job has an invalid OCR-result target",
                    ex
            );
        }
    }

    public Long ocrResultId(String jobKey) {
        return resultId(JobType.OCR_QUALITY_ASSESSMENT, jobKey);
    }

    public Long visionOcrResultId(String jobKey) {
        return resultId(JobType.VISION_OCR_ASSESSMENT, jobKey);
    }

    public Long visionDeterministicAssessmentId(String jobKey) {
        String marker = ":QUALITY_ASSESSMENT:";

        if (jobKey == null || !jobKey.contains(marker))
            throw new IllegalArgumentException(
                    "Vision OCR-assessment job has no deterministic-assessment target"
            );

        try {
            String target = jobKey.substring(jobKey.indexOf(marker) + marker.length());
            int separator = target.indexOf(':');
            return Long.valueOf(separator < 0 ? target : target.substring(0, separator));
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(
                    "Vision OCR-assessment job has an invalid deterministic-assessment target",
                    ex
            );
        }
    }

    private Long resultId(JobType jobType, String jobKey) {
        String prefix = jobType.name() + ":OCR_RESULT:";

        if (jobKey == null || !jobKey.startsWith(prefix))
            throw new IllegalArgumentException(
                    jobType + " job has no OCR-result target"
            );

        try {
            String target = jobKey.substring(prefix.length());
            int separator = target.indexOf(':');

            return Long.valueOf(
                    separator < 0
                            ? target
                            : target.substring(0, separator)
            );
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(
                    jobType + " job has an invalid OCR-result target",
                    ex
            );
        }
    }

    public String chunkGenerationInputHash(String jobKey) {
        String marker = ":INPUT:";

        if(jobKey == null || !jobKey.contains(marker))
            throw new IllegalArgumentException(
                    "Chunk-generation job has no input hash"
            );

        String hash = jobKey.substring(
                jobKey.lastIndexOf(marker) + marker.length()
        );

        if(!hash.matches("[0-9a-f]{64}"))
            throw new IllegalArgumentException(
                    "Chunk-generation job has an invalid input hash"
            );

        return hash;
    }


    private void requireJobType(JobType jobType) {
        if(jobType == null)
            throw new IllegalArgumentException("Job type is required");
    }
}
