package fmi.ethnowear.application.service.catalogue;

import fmi.ethnowear.application.dto.catalogue.ConceptEvidenceSummaryDetails;
import fmi.ethnowear.application.service.archive.media.asset.PublicRepresentativeMediaService;
import fmi.ethnowear.domain.model.archive.MediaRole;
import fmi.ethnowear.domain.model.archive.MediaType;
import fmi.ethnowear.domain.model.ontology.FeatureType;
import fmi.ethnowear.persistence.jpa.entity.ArchiveItem;
import fmi.ethnowear.persistence.jpa.entity.ArchiveItemMedia;
import fmi.ethnowear.persistence.jpa.entity.MediaAsset;
import fmi.ethnowear.persistence.jpa.projection.OntologyEvidenceLinkProjection;
import fmi.ethnowear.persistence.jpa.repository.ArchiveItemFeatureRepository;
import fmi.ethnowear.persistence.jpa.repository.ArchiveItemMediaRepository;
import fmi.ethnowear.persistence.jpa.repository.ArchiveItemRepository;
import fmi.ethnowear.testutil.EntityTestUtils;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static fmi.ethnowear.support.RepositoryTestProxies.proxy;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ConceptCardEvidenceServiceTest {

    @Test
    void summarizesDistinctPublishedEvidenceAndPrefersThumbnailImage() {
        String ontologyIri = "urn:test#CrossStitch";
        ArchiveItem firstItem = archiveItem(1L);
        ArchiveItem secondItem = archiveItem(2L);
        ArchiveItemMedia primary = media(10L, firstItem, 100L, MediaRole.PRIMARY);
        ArchiveItemMedia thumbnail = media(11L, secondItem, 101L, MediaRole.THUMBNAIL);

        ArchiveItemRepository itemRepository = proxy(
                ArchiveItemRepository.class,
                (ignored, method, arguments) -> {
                    throw new AssertionError("Unexpected item repository call: " + method.getName());
                }
        );
        ArchiveItemFeatureRepository featureRepository = proxy(
                ArchiveItemFeatureRepository.class,
                (ignored, method, arguments) -> {
                    if(method.getName().equals("findPublishedEvidenceLinks"))
                        return List.of(
                                link(ontologyIri, 1L),
                                link(ontologyIri, 1L),
                                link(ontologyIri, 2L)
                        );

                    throw new AssertionError("Unexpected feature repository call: " + method.getName());
                }
        );
        ArchiveItemMediaRepository mediaRepository = proxy(
                ArchiveItemMediaRepository.class,
                (ignored, method, arguments) -> {
                    if(method.getName().equals(
                            "findPublicByArchiveItemIdsRolesAndMediaType"
                    ))
                        return List.of(primary, thumbnail);

                    throw new AssertionError("Unexpected media repository call: " + method.getName());
                }
        );
        ConceptCardEvidenceService service = new ConceptCardEvidenceService(
                itemRepository,
                featureRepository,
                new PublicRepresentativeMediaService(mediaRepository)
        );

        Map<String, ConceptEvidenceSummaryDetails> result = service.summarize(
                FeatureType.TECHNIQUE,
                List.of(ontologyIri)
        );

        assertEquals(2, result.get(ontologyIri).evidenceCount());
        assertEquals(101L, result.get(ontologyIri).representativeMediaAssetId());
    }

    private OntologyEvidenceLinkProjection link(String ontologyIri, Long archiveItemId) {
        return new OntologyEvidenceLinkProjection() {
            @Override
            public String getOntologyIri() {
                return ontologyIri;
            }

            @Override
            public Long getArchiveItemId() {
                return archiveItemId;
            }
        };
    }

    private ArchiveItem archiveItem(Long id) {
        ArchiveItem item = new ArchiveItem();
        EntityTestUtils.setId(item, id);
        return item;
    }

    private ArchiveItemMedia media(
            Long id,
            ArchiveItem item,
            Long mediaAssetId,
            MediaRole role
    ) {
        MediaAsset asset = new MediaAsset();
        EntityTestUtils.setId(asset, mediaAssetId);
        asset.setMediaType(MediaType.IMAGE);

        ArchiveItemMedia media = new ArchiveItemMedia();
        EntityTestUtils.setId(media, id);
        media.setArchiveItem(item);
        media.setMediaAsset(asset);
        media.setRole(role);
        return media;
    }
}
