package fmi.ethnowear.persistence.jpa.entity.document;

import jakarta.persistence.Column;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class DocumentPageFigureMappingTest {

    @Test
    void mapsFigureAndCandidateToExactSqlContract() throws Exception {
        Table figureTable = DocumentPageFigure.class.getAnnotation(Table.class);
        Table candidateTable = DocumentPageFigureCandidate.class.getAnnotation(Table.class);

        assertEquals("ethnowear", figureTable.schema());
        assertEquals("DocumentPageFigures", figureTable.name());
        assertEquals("DocumentPageFigureCandidates", candidateTable.name());

        JoinColumn media = DocumentPageFigure.class
                .getDeclaredField("mediaAsset")
                .getAnnotation(JoinColumn.class);
        assertEquals("MediaAssetId", media.name());
        assertFalse(media.updatable());

        Column rawCaption = DocumentPageFigure.class
                .getDeclaredField("rawCaptionText")
                .getAnnotation(Column.class);
        assertEquals(2000, rawCaption.length());
        assertFalse(rawCaption.updatable());
    }
}
