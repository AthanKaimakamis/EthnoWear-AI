package fmi.ethnowear.application.exception;

import fmi.ethnowear.domain.model.document.processing.JobType;
import lombok.Getter;

@Getter
public class ActiveDocumentJobExistsException extends RuntimeException {

    private final JobType jobType;

    public ActiveDocumentJobExistsException(JobType jobType) {
        super("An active " + jobType + " job already exists");
        this.jobType = jobType;
    }
}
