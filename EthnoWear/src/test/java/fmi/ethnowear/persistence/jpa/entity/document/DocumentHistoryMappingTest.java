package fmi.ethnowear.persistence.jpa.entity.document;

import fmi.ethnowear.persistence.jpa.entity.AppendOnlyEntity;
import fmi.ethnowear.persistence.jpa.entity.UpdatableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentHistoryMappingTest {

    @Test
    void usesTheCorrectAuditBaseForEachHistoryType() {
        assertThat(DocumentPageReview.class.getSuperclass()).isEqualTo(AppendOnlyEntity.class);
        assertThat(DocumentPageProvenanceEvent.class.getSuperclass()).isEqualTo(AppendOnlyEntity.class);
        assertThat(DocumentPageOcrResult.class.getSuperclass()).isEqualTo(UpdatableEntity.class);
    }

    @Test
    void keepsOcrPayloadImmutableWhileAllowingCurrentSelectionToChange() throws Exception {
        assertColumnIsNotUpdatable(DocumentPageOcrResult.class, "rawText");
        assertColumnIsNotUpdatable(DocumentPageOcrResult.class, "ocrEngine");
        assertColumnIsNotUpdatable(DocumentPageOcrResult.class, "parametersJson");
        assertColumnIsNotUpdatable(DocumentPageOcrResult.class, "structuredOutputJson");

        Column current = DocumentPageOcrResult.class
                .getDeclaredField("current")
                .getAnnotation(Column.class);

        assertThat(current.updatable()).isTrue();
    }

    @Test
    void mapsHistoryRelationshipsAsLazyAndImmutable() throws Exception {
        assertLazyImmutableRelationship(DocumentPageReview.class, "documentPage");
        assertLazyImmutableRelationship(DocumentPageProvenanceEvent.class, "documentPage");
        assertLazyImmutableRelationship(DocumentPageProvenanceEvent.class, "newSourceReference");
        assertLazyImmutableRelationship(DocumentPageOcrResult.class, "documentPage");
    }

    @Test
    void mapsQualityAssessmentToTheExactOcrResult() throws Exception {
        Field field = DocumentPageQualityAssessment.class.getDeclaredField("documentPageOcrResult");
        ManyToOne relationship = field.getAnnotation(ManyToOne.class);
        JoinColumn joinColumn = field.getAnnotation(JoinColumn.class);

        assertThat(relationship.fetch()).isEqualTo(FetchType.LAZY);
        assertThat(joinColumn.name()).isEqualTo("DocumentPageOcrResultId");
    }

    @Test
    void activeJobKeyHasNoGeneratedSetter() {
        assertThat(Arrays.stream(DocumentProcessingJob.class.getDeclaredMethods())
                .map(method -> method.getName()))
                .doesNotContain("setActiveJobKey")
                .contains("assignActiveJobKey", "clearActiveJobKey");
    }

    private void assertColumnIsNotUpdatable(Class<?> entityType, String fieldName) throws Exception {
        Column column = entityType.getDeclaredField(fieldName).getAnnotation(Column.class);
        assertThat(column.updatable()).isFalse();
    }

    private void assertLazyImmutableRelationship(Class<?> entityType, String fieldName) throws Exception {
        Field field = entityType.getDeclaredField(fieldName);
        ManyToOne relationship = field.getAnnotation(ManyToOne.class);
        JoinColumn joinColumn = field.getAnnotation(JoinColumn.class);

        assertThat(relationship.fetch()).isEqualTo(FetchType.LAZY);
        assertThat(joinColumn.updatable()).isFalse();
    }
}
