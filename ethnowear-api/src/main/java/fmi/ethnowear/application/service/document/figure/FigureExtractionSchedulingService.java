package fmi.ethnowear.application.service.document.figure;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
@RequiredArgsConstructor
public class FigureExtractionSchedulingService {

    private static final Logger LOGGER = LoggerFactory.getLogger(
            FigureExtractionSchedulingService.class
    );

    private final FigureExtractionSchedulingExecutor executor;

    public void scheduleAfterCommit(Long ocrResultId) {
        Runnable scheduling = () -> scheduleSafely(ocrResultId);

        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            scheduling.run();
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        scheduling.run();
                    }
                }
        );
    }

    private void scheduleSafely(Long ocrResultId) {
        try {
            executor.schedule(ocrResultId);
        } catch (RuntimeException ex) {
            LOGGER.warn(
                    "Figure extraction scheduling failed for OCR result {} ({})",
                    ocrResultId,
                    ex.getClass().getSimpleName()
            );
            executor.recordSchedulingFailure(ocrResultId);
        }
    }
}
