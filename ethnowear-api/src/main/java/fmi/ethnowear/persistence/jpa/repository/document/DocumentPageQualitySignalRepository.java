package fmi.ethnowear.persistence.jpa.repository.document;

import fmi.ethnowear.persistence.jpa.entity.document.DocumentPageQualitySignal;
import fmi.ethnowear.persistence.jpa.projection.document.DocumentPageQualitySignalProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface DocumentPageQualitySignalRepository extends JpaRepository<DocumentPageQualitySignal, Long> {

    List<DocumentPageQualitySignal> findByAssessment_IdOrderBySignalOrdinalAscIdAsc(Long assessmentId);

    boolean existsByAssessment_Id(Long assessmentId);

    @Query("""
            SELECT
                signal.id AS id,
                signal.assessment.id AS assessmentId,
                signal.signalType AS signalType,
                signal.signalOrdinal AS signalOrdinal,
                signal.signalValueDecimal AS signalValueDecimal,
                signal.signalValueText AS signalValueText,
                signal.severity AS severity,
                signal.weight AS weight,
                signal.message AS message,
                signal.createdAt AS createdAt,
                signal.updatedAt AS updatedAt
            FROM DocumentPageQualitySignal signal
            WHERE signal.assessment.id IN :assessmentIds
            ORDER BY
                signal.assessment.id,
                signal.signalOrdinal,
                signal.id
            """)
    List<DocumentPageQualitySignalProjection> findSafeSignals(
            @Param("assessmentIds")
            Collection<Long> assessmentIds
    );
}
