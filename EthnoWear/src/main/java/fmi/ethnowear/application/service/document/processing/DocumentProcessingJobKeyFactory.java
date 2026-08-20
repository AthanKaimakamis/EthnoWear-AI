package fmi.ethnowear.application.service.document.processing;

import fmi.ethnowear.domain.model.document.processing.JobType;
import org.springframework.stereotype.Component;

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


    private void requireJobType(JobType jobType) {
        if(jobType == null)
            throw new IllegalArgumentException("Job type is required");
    }
}
