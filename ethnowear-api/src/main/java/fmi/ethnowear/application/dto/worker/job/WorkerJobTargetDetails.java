package fmi.ethnowear.application.dto.worker.job;

public record WorkerJobTargetDetails(
        Long documentId,
        Long documentPageId,
        Integer pdfPageIndex,
        Long knowledgeChunkId,
        boolean inputAvailable
) {
    public WorkerJobTargetDetails(
            Long documentId,
            Long documentPageId,
            Long knowledgeChunkId,
            boolean inputAvailable
    ) {
        this(
                documentId,
                documentPageId,
                null,
                knowledgeChunkId,
                inputAvailable
        );
    }
}
