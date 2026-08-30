package fmi.ethnowear.persistence.jpa.repository.document;

import fmi.ethnowear.persistence.jpa.entity.document.DocumentProcessingJobAttempt;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface DocumentProcessingJobAttemptRepository
        extends JpaRepository<DocumentProcessingJobAttempt, Long> {

    @EntityGraph(attributePaths = "processingJob")
    List<DocumentProcessingJobAttempt>
    findByProcessingJob_IdInOrderByProcessingJob_IdAscExecutionNumberDesc(
            Collection<Long> processingJobIds
    );
}
