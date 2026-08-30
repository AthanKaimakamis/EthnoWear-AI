package fmi.ethnowear.application.service.document.query;

import fmi.ethnowear.application.dto.document.query.processing.ProcessingJobCountsDetails;
import fmi.ethnowear.application.dto.document.query.processing.ProcessingJobQueryDto;
import fmi.ethnowear.application.dto.document.query.processing.ProcessingJobSummaryDetails;
import fmi.ethnowear.application.dto.document.query.processing.ProcessingJobResultDetails;
import fmi.ethnowear.application.service.document.query.mapper.ProcessingJobAdminMapper;
import fmi.ethnowear.domain.model.document.processing.JobStatus;
import fmi.ethnowear.persistence.jpa.projection.document.ProcessingJobStatusCountProjection;
import fmi.ethnowear.persistence.jpa.repository.document.DocumentProcessingJobRepository;
import fmi.ethnowear.persistence.jpa.repository.document.DocumentProcessingJobAttemptRepository;
import fmi.ethnowear.persistence.jpa.repository.document.DocumentPageMediaRepository;
import fmi.ethnowear.persistence.jpa.repository.document.DocumentPageOcrResultRepository;
import fmi.ethnowear.persistence.jpa.repository.document.DocumentPageQualityAssessmentRepository;
import fmi.ethnowear.persistence.jpa.repository.document.DocumentPageQualitySignalRepository;
import fmi.ethnowear.persistence.jpa.entity.document.DocumentProcessingJobAttempt;
import fmi.ethnowear.persistence.jpa.entity.document.DocumentPageQualityAssessment;
import fmi.ethnowear.application.dto.document.query.quality.DocumentPageQualitySummaryDetails;
import fmi.ethnowear.domain.model.document.quality.QualitySignalSeverity;
import fmi.ethnowear.util.TextUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.jspecify.annotations.NonNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import java.util.List;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.stream.Collectors;
import java.math.RoundingMode;

@Service
@Transactional(readOnly = true)
public class ProcessingJobAdminQueryService {

    private static final int MAX_PAGE_SIZE = 100;

    private static final Set<String> ALLOWED_SORTS = Set.of(
            "id",
            "jobType",
            "status",
            "priority",
            "attemptCount",
            "maxAttempts",
            "availableAt",
            "claimedAt",
            "startedAt",
            "finishedAt",
            "timeoutAt",
            "createdAt",
            "updatedAt"
    );

    private static final Set<JobStatus> ACTIVE = Set.of(
            JobStatus.QUEUED,
            JobStatus.CLAIMED,
            JobStatus.RUNNING,
            JobStatus.RETRY_WAIT,
            JobStatus.CANCEL_REQUESTED
    );

    private static final Set<JobStatus> RETRYABLE = Set.of(
            JobStatus.SUCCEEDED,
            JobStatus.FAILED,
            JobStatus.TIMED_OUT,
            JobStatus.DEAD
    );

    private final DocumentProcessingJobRepository jobRepository;
    private final DocumentProcessingJobAttemptRepository attemptRepository;
    private final DocumentPageMediaRepository pageMediaRepository;
    private final DocumentPageOcrResultRepository ocrResultRepository;
    private final DocumentPageQualityAssessmentRepository assessmentRepository;
    private final DocumentPageQualitySignalRepository signalRepository;
    private final ProcessingJobAdminMapper mapper;

    @Autowired
    public ProcessingJobAdminQueryService(
            DocumentProcessingJobRepository jobRepository,
            DocumentProcessingJobAttemptRepository attemptRepository,
            DocumentPageMediaRepository pageMediaRepository,
            DocumentPageOcrResultRepository ocrResultRepository,
            DocumentPageQualityAssessmentRepository assessmentRepository,
            DocumentPageQualitySignalRepository signalRepository,
            ProcessingJobAdminMapper mapper
    ) {
        this.jobRepository = jobRepository;
        this.attemptRepository = attemptRepository;
        this.pageMediaRepository = pageMediaRepository;
        this.ocrResultRepository = ocrResultRepository;
        this.assessmentRepository = assessmentRepository;
        this.signalRepository = signalRepository;
        this.mapper = mapper;
    }

    protected ProcessingJobAdminQueryService(
            DocumentProcessingJobRepository jobRepository,
            ProcessingJobAdminMapper mapper
    ) {
        this(jobRepository, null, null, null, null, null, mapper);
    }

    public Page<ProcessingJobSummaryDetails> findAll(
            ProcessingJobQueryDto query,
            Pageable pageable
    ) {
        ProcessingJobQueryDto safeQuery = query == null
                ? new ProcessingJobQueryDto(null, null, null, null, null)
                : query;
        validateIds(safeQuery);

        Page<fmi.ethnowear.persistence.jpa.entity.document.DocumentProcessingJob> jobs =
                jobRepository.findAdminJobs(
                        searchPattern(safeQuery.searchText()),
                        safeQuery.jobType(),
                        safeQuery.status(),
                        safeQuery.documentId(),
                        safeQuery.documentPageId(),
                        processingPageable(pageable)
                );

        if (jobs.isEmpty())
            return jobs.map(mapper::toDetails);

        if (attemptRepository == null)
            return jobs.map(mapper::toDetails);

        List<Long> jobIds = jobs.stream().map(job -> job.getId()).toList();
        Map<Long, List<DocumentProcessingJobAttempt>> attempts = attemptRepository
                .findByProcessingJob_IdInOrderByProcessingJob_IdAscExecutionNumberDesc(jobIds)
                .stream()
                .collect(Collectors.groupingBy(
                        attempt -> attempt.getProcessingJob().getId(),
                        LinkedHashMap::new,
                        Collectors.toList()
                ));
        Map<Long, ProcessingJobResultDetails> results = results(jobIds);

        return jobs.map(job -> mapper.toDetails(
                job,
                attempts.getOrDefault(job.getId(), List.of()),
                results.getOrDefault(
                        job.getId(),
                        new ProcessingJobResultDetails(List.of(), null, null)
                )
        ));
    }

    private Map<Long, ProcessingJobResultDetails> results(
            Collection<Long> jobIds
    ) {
        Map<Long, List<Long>> media = pageMediaRepository
                .findByProducingJob_IdInOrderByProducingJob_IdAscIdAsc(jobIds)
                .stream()
                .collect(Collectors.groupingBy(
                        item -> item.getProducingJob().getId(),
                        LinkedHashMap::new,
                        Collectors.mapping(
                                item -> item.getMediaAsset().getId(),
                                Collectors.toList()
                        )
                ));
        Map<Long, Long> ocr = ocrResultRepository.findByProcessingJob_IdIn(jobIds)
                .stream()
                .collect(Collectors.toMap(
                        item -> item.getProcessingJob().getId(),
                        item -> item.getId()
                ));
        List<DocumentPageQualityAssessment> assessments =
                assessmentRepository.findByProcessingJob_IdIn(jobIds);
        Map<Long, DocumentPageQualitySummaryDetails> quality = quality(assessments);
        Map<Long, ProcessingJobResultDetails> result = new LinkedHashMap<>();

        jobIds.forEach(jobId -> result.put(
                jobId,
                new ProcessingJobResultDetails(
                        media.getOrDefault(jobId, List.of()),
                        ocr.get(jobId),
                        quality.get(jobId)
                )
        ));
        return result;
    }

    private Map<Long, DocumentPageQualitySummaryDetails> quality(
            List<DocumentPageQualityAssessment> assessments
    ) {
        if (assessments.isEmpty())
            return Map.of();

        List<Long> ids = assessments.stream().map(item -> item.getId()).toList();
        Map<Long, List<fmi.ethnowear.persistence.jpa.projection.document.DocumentPageQualitySignalProjection>>
                signals = signalRepository.findSafeSignals(ids).stream()
                .collect(Collectors.groupingBy(
                        item -> item.getAssessmentId(),
                        LinkedHashMap::new,
                        Collectors.toList()
                ));
        Map<Long, DocumentPageQualitySummaryDetails> result = new LinkedHashMap<>();

        assessments.forEach(assessment -> {
            var checks = signals.getOrDefault(assessment.getId(), List.of());
            result.put(
                    assessment.getProcessingJob().getId(),
                    new DocumentPageQualitySummaryDetails(
                            assessment.getId(),
                            assessment.getOverallScore(),
                            assessment.getOverallScore() == null
                                    ? null
                                    : assessment.getOverallScore()
                                            .movePointRight(2)
                                            .setScale(2, RoundingMode.HALF_UP),
                            assessment.getQualityStatus(),
                            (int) checks.stream()
                                    .filter(check -> check.getSeverity()
                                            == QualitySignalSeverity.INFO)
                                    .count(),
                            (int) checks.stream()
                                    .filter(check -> check.getSeverity()
                                            != QualitySignalSeverity.INFO)
                                    .count(),
                            assessment.getCreatedAt()
                    )
            );
        });
        return result;
    }

    public ProcessingJobCountsDetails counts(ProcessingJobQueryDto query) {
        ProcessingJobQueryDto safeQuery = query == null
                ? new ProcessingJobQueryDto(null, null, null, null, null)
                : query;
        validateIds(safeQuery);

        Map<JobStatus, Long> counts = new EnumMap<>(JobStatus.class);
        for (JobStatus status : JobStatus.values())
            counts.put(status, 0L);

        for (ProcessingJobStatusCountProjection count : jobRepository.countAdminJobsByStatus(
                searchPattern(safeQuery.searchText()),
                safeQuery.jobType(),
                safeQuery.documentId(),
                safeQuery.documentPageId()
        ))
            counts.put(count.getStatus(), count.getTotal());

        return new ProcessingJobCountsDetails(
                total(counts, Set.of(JobStatus.values())),
                total(counts, ACTIVE),
                total(counts, RETRYABLE),
                counts
        );
    }

    private long total(Map<JobStatus, Long> counts, Set<JobStatus> statuses) {
        return statuses.stream()
                .mapToLong(status -> counts.getOrDefault(status, 0L))
                .sum();
    }

    private Pageable processingPageable(Pageable pageable) {
        if (pageable == null || pageable.isUnpaged())
            throw new IllegalArgumentException("Processing pageable is required");

        if (pageable.getPageSize() > MAX_PAGE_SIZE)
            throw new IllegalArgumentException(
                    "Processing page size cannot exceed " + MAX_PAGE_SIZE
            );

        for (Sort.Order order : pageable.getSort()) {
            if (!ALLOWED_SORTS.contains(order.getProperty()))
                throw new IllegalArgumentException(
                        "Unsupported processing sort: " + order.getProperty()
                );
        }

        return PageRequest.of(
                pageable.getPageNumber(),
                pageable.getPageSize(),
                pageable.getSort()
        );
    }

    private String searchPattern(String searchText) {
        String normalized = TextUtils.normalize(searchText);
        return normalized.isEmpty() ? null : "%" + normalized + "%";
    }

    private void validateIds(@NonNull ProcessingJobQueryDto query) {
        if (query.documentId() != null && query.documentId() <= 0)
            throw new IllegalArgumentException("Document id must be positive");

        if (query.documentPageId() != null && query.documentPageId() <= 0)
            throw new IllegalArgumentException("Document page id must be positive");
    }
}
