package fmi.ethnowear.application.service.document.query.mapper;

import fmi.ethnowear.domain.model.document.DocumentPageRenditionType;
import fmi.ethnowear.persistence.jpa.entity.MediaAsset;
import fmi.ethnowear.persistence.jpa.entity.document.Document;
import fmi.ethnowear.persistence.jpa.entity.document.DocumentPage;
import fmi.ethnowear.persistence.jpa.entity.document.DocumentPageMedia;
import fmi.ethnowear.persistence.jpa.entity.document.DocumentPageQualityAssessment;
import fmi.ethnowear.domain.model.document.quality.QualityStatus;
import java.math.BigDecimal;
import fmi.ethnowear.persistence.jpa.projection.document.DocumentPagePreviewMediaProjection;
import fmi.ethnowear.testutil.EntityTestUtils;
import org.junit.jupiter.api.Test;

import java.util.List;

import static fmi.ethnowear.support.RepositoryTestProxies.proxy;
import static org.junit.jupiter.api.Assertions.assertEquals;

class DocumentPageQueryMapperTest {

    private final DocumentPageQueryMapper mapper = new DocumentPageQueryMapper();

    @Test
    void mapsPageMediaWithoutStorageMetadata() {
        Document document = new Document();
        EntityTestUtils.setId(document, 1L);

        DocumentPage page = new DocumentPage();
        EntityTestUtils.setId(page, 2L);
        page.setDocument(document);
        page.setRawOcrText("raw");
        page.setCorrectedText("corrected");

        MediaAsset asset = new MediaAsset();
        EntityTestUtils.setId(asset, 3L);
        asset.setFileName("page.jpg");
        asset.setMimeType("image/jpeg");
        asset.setFilePath("documents/private/page.jpg");
        asset.setChecksum("internal");

        DocumentPageMedia media = new DocumentPageMedia();
        EntityTestUtils.setId(media, 4L);
        media.setDocumentPage(page);
        media.setMediaAsset(asset);
        media.setRenditionType(DocumentPageRenditionType.THUMBNAIL);
        media.setDisplayOrder(0);

        var result = mapper.toDetails(page, List.of(media));

        assertEquals(3L, result.summary().previewMediaAssetId());
        assertEquals("raw", result.rawOcrText());
        assertEquals("corrected", result.correctedText());
        assertEquals("page.jpg", result.media().getFirst().fileName());
        assertEquals(3L, result.media().getFirst().mediaAssetId());
    }

    @Test
    void choosesThumbnailBeforePreferredOcrInputForPreview() {
        DocumentPagePreviewMediaProjection preferredOcr = candidate(
                2L,
                20L,
                null,
                true,
                0
        );
        DocumentPagePreviewMediaProjection thumbnail = candidate(
                2L,
                30L,
                DocumentPageRenditionType.THUMBNAIL,
                false,
                5
        );

        assertEquals(
                30L,
                mapper.toPreviewMediaIds(
                        List.of(preferredOcr, thumbnail)
                ).get(2L)
        );
    }

    @Test
    void includesCurrentQualitySnapshotInPageSummary() {
        DocumentPage page = new DocumentPage();
        EntityTestUtils.setId(page, 2L);
        DocumentPageQualityAssessment assessment =
                new DocumentPageQualityAssessment();
        EntityTestUtils.setId(assessment, 9L);
        page.setCurrentQualityAssessment(assessment);
        page.setCurrentQualityScore(new BigDecimal("0.8750"));
        page.setCurrentQualityStatus(QualityStatus.MINOR_REVIEW);
        page.setCurrentQualityPassedChecks(7);
        page.setCurrentQualityFailedChecks(2);

        var quality = mapper.toSummary(page, null).quality();

        assertEquals(9L, quality.assessmentId());
        assertEquals(new BigDecimal("87.50"), quality.percentage());
        assertEquals(QualityStatus.MINOR_REVIEW, quality.qualityLevel());
        assertEquals(7, quality.passedChecks());
        assertEquals(2, quality.failedChecks());
    }

    private DocumentPagePreviewMediaProjection candidate(
            Long pageId,
            Long mediaId,
            DocumentPageRenditionType renditionType,
            boolean preferredOcrInput,
            int displayOrder
    ) {
        return proxy(
                DocumentPagePreviewMediaProjection.class,
                (ignored, method, arguments) -> switch (method.getName()) {
                    case "getDocumentPageId" -> pageId;
                    case "getMediaAssetId" -> mediaId;
                    case "getRenditionType" -> renditionType;
                    case "isPreferredOcrInput" -> preferredOcrInput;
                    case "getDisplayOrder" -> displayOrder;
                    default -> throw new AssertionError(method.getName());
                }
        );
    }
}
