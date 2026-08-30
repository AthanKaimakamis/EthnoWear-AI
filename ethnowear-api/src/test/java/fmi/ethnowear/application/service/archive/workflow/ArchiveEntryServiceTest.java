package fmi.ethnowear.application.service.archive.workflow;

import fmi.ethnowear.application.dto.archive.item.ArchiveItemDetails;
import fmi.ethnowear.application.dto.archive.item.ArchiveItemFeatureDetails;
import fmi.ethnowear.application.dto.archive.item.ArchiveItemFeatureWriteDto;
import fmi.ethnowear.application.dto.archive.item.ArchiveItemWriteDto;
import fmi.ethnowear.application.dto.archive.media.ArchiveItemMediaDetails;
import fmi.ethnowear.application.dto.archive.media.ArchiveItemMediaWriteDto;
import fmi.ethnowear.application.dto.archive.workflow.ArchiveEntryDetails;
import fmi.ethnowear.application.dto.archive.workflow.ArchiveEntryFeatureWriteDto;
import fmi.ethnowear.application.dto.archive.workflow.ArchiveEntryMediaWriteDto;
import fmi.ethnowear.application.dto.archive.workflow.ArchiveEntryWriteDto;
import fmi.ethnowear.application.service.archive.item.ArchiveItemFeatureMapper;
import fmi.ethnowear.application.service.archive.item.ArchiveItemFeatureService;
import fmi.ethnowear.application.service.archive.item.ArchiveItemService;
import fmi.ethnowear.application.service.archive.media.attachment.ArchiveItemMediaMapper;
import fmi.ethnowear.application.service.archive.media.attachment.ArchiveItemMediaService;
import fmi.ethnowear.domain.model.archive.ArchiveType;
import fmi.ethnowear.domain.model.archive.MediaRole;
import fmi.ethnowear.domain.model.archive.PublicationStatus;
import fmi.ethnowear.domain.model.archive.TrustedLevel;
import fmi.ethnowear.domain.model.ontology.FeatureType;
import fmi.ethnowear.persistence.jpa.entity.ArchiveItemFeature;
import fmi.ethnowear.persistence.jpa.entity.ArchiveItemMedia;
import fmi.ethnowear.persistence.jpa.repository.ArchiveItemFeatureRepository;
import fmi.ethnowear.persistence.jpa.repository.ArchiveItemMediaRepository;
import fmi.ethnowear.testutil.EntityTestUtils;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static fmi.ethnowear.support.RepositoryTestProxies.proxy;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ArchiveEntryServiceTest {

    @Test
    void createsCompleteEntryUsingGeneratedArchiveItemId() {
        RecordingItemService itemService = new RecordingItemService();
        RecordingFeatureService featureService = new RecordingFeatureService();
        RecordingMediaService mediaService = new RecordingMediaService();
        ArchiveEntryService service = service(itemService, featureService, mediaService, List.of(), List.of());

        ArchiveEntryDetails result = service.create(input(null, null));

        assertEquals(10L, featureService.created.getFirst().archiveItemId());
        assertEquals(10L, mediaService.created.getFirst().archiveItemId());
        assertEquals(1, result.features().size());
        assertEquals(1, result.media().size());
    }

    @Test
    void updatesRetainedChildrenAndDeletesOmittedChildren() {
        RecordingItemService itemService = new RecordingItemService();
        RecordingFeatureService featureService = new RecordingFeatureService();
        RecordingMediaService mediaService = new RecordingMediaService();
        ArchiveEntryService service = service(
                itemService,
                featureService,
                mediaService,
                List.of(feature(20L), feature(21L)),
                List.of(media(30L), media(31L))
        );
        ArchiveEntryWriteDto input = new ArchiveEntryWriteDto(
                itemInput(),
                List.of(featureInput(20L), featureInput(null)),
                List.of(mediaInput(30L))
        );

        ArchiveEntryDetails result = service.update(10L, input);

        assertEquals(List.of(21L), featureService.deleted);
        assertEquals(List.of(31L), mediaService.deleted);
        assertEquals(List.of(20L), featureService.updated);
        assertEquals(List.of(30L), mediaService.updated);
        assertEquals(1, featureService.created.size());
        assertEquals(2, result.features().size());
        assertEquals(1, result.media().size());
    }

    private ArchiveEntryService service(
            ArchiveItemService itemService,
            ArchiveItemFeatureService featureService,
            ArchiveItemMediaService mediaService,
            List<ArchiveItemFeature> features,
            List<ArchiveItemMedia> media
    ) {
        ArchiveItemFeatureRepository featureRepository = proxy(
                ArchiveItemFeatureRepository.class,
                (ignored, method, arguments) -> {
                    if(method.getName().equals("findByArchiveItem_Id"))
                        return features;

                    throw new AssertionError("Unexpected feature repository call: " + method.getName());
                }
        );
        ArchiveItemMediaRepository mediaRepository = proxy(
                ArchiveItemMediaRepository.class,
                (ignored, method, arguments) -> {
                    if(method.getName().equals("findByArchiveItemId"))
                        return media;

                    throw new AssertionError("Unexpected media repository call: " + method.getName());
                }
        );

        return new ArchiveEntryService(
                itemService,
                featureService,
                mediaService,
                featureRepository,
                mediaRepository,
                new ArchiveItemFeatureMapper(),
                new ArchiveItemMediaMapper()
        );
    }

    private ArchiveEntryWriteDto input(Long featureId, Long mediaId) {
        return new ArchiveEntryWriteDto(
                itemInput(),
                List.of(featureInput(featureId)),
                List.of(mediaInput(mediaId))
        );
    }

    private ArchiveItemWriteDto itemInput() {
        return new ArchiveItemWriteDto(
                1L, null, null, null, "Archive item", null, null,
                ArchiveType.EMBROIDERY_SAMPLE, null, null, null,
                TrustedLevel.VERIFIED, null, null, null, null
        );
    }

    private ArchiveEntryFeatureWriteDto featureInput(Long id) {
        return new ArchiveEntryFeatureWriteDto(
                id,
                FeatureType.TECHNIQUE,
                "urn:test#CrossStitch",
                "CrossStitch",
                null,
                true,
                null,
                null
        );
    }

    private ArchiveEntryMediaWriteDto mediaInput(Long id) {
        return new ArchiveEntryMediaWriteDto(id, 100L, MediaRole.PRIMARY, null, null);
    }

    private ArchiveItemFeature feature(Long id) {
        ArchiveItemFeature feature = new ArchiveItemFeature();
        EntityTestUtils.setId(feature, id);
        return feature;
    }

    private ArchiveItemMedia media(Long id) {
        ArchiveItemMedia media = new ArchiveItemMedia();
        EntityTestUtils.setId(media, id);
        return media;
    }

    private ArchiveItemDetails itemDetails() {
        return new ArchiveItemDetails(
                10L, 1L, null, null, null, "Archive item", null, null,
                ArchiveType.EMBROIDERY_SAMPLE, null, null, null,
                TrustedLevel.VERIFIED, PublicationStatus.DRAFT,
                null, null, null, null,
                null, null, null, null, null
        );
    }

    private final class RecordingItemService extends ArchiveItemService {

        private RecordingItemService() {
            super(null, null, null, null, null, null);
        }

        @Override
        public ArchiveItemDetails create(ArchiveItemWriteDto input) {
            return itemDetails();
        }

        @Override
        public ArchiveItemDetails update(Long id, ArchiveItemWriteDto input) {
            return itemDetails();
        }
    }

    private static final class RecordingFeatureService extends ArchiveItemFeatureService {

        private final List<ArchiveItemFeatureWriteDto> created = new ArrayList<>();
        private final List<Long> updated = new ArrayList<>();
        private final List<Long> deleted = new ArrayList<>();

        private RecordingFeatureService() {
            super(null, null, null, null, null, null);
        }

        @Override
        public ArchiveItemFeatureDetails create(ArchiveItemFeatureWriteDto input) {
            created.add(input);
            return details(null, input);
        }

        @Override
        public ArchiveItemFeatureDetails update(Long id, ArchiveItemFeatureWriteDto input) {
            updated.add(id);
            return details(id, input);
        }

        @Override
        public void delete(Long id) {
            deleted.add(id);
        }

        private ArchiveItemFeatureDetails details(Long id, ArchiveItemFeatureWriteDto input) {
            return new ArchiveItemFeatureDetails(
                    id, input.archiveItemId(), input.featureType(), input.ontologyIri(),
                    input.ontologyLocalName(), input.confidence(), input.validated(), input.notes(),
                    input.sourceReferenceId(), null, null
            );
        }
    }

    private static final class RecordingMediaService extends ArchiveItemMediaService {

        private final List<ArchiveItemMediaWriteDto> created = new ArrayList<>();
        private final List<Long> updated = new ArrayList<>();
        private final List<Long> deleted = new ArrayList<>();

        private RecordingMediaService() {
            super(null, null, null, null, null, null);
        }

        @Override
        public ArchiveItemMediaDetails create(ArchiveItemMediaWriteDto input) {
            created.add(input);
            return details(null, input);
        }

        @Override
        public ArchiveItemMediaDetails update(Long id, ArchiveItemMediaWriteDto input) {
            updated.add(id);
            return details(id, input);
        }

        @Override
        public void delete(Long id) {
            deleted.add(id);
        }

        private ArchiveItemMediaDetails details(Long id, ArchiveItemMediaWriteDto input) {
            return new ArchiveItemMediaDetails(
                    id, input.archiveItemId(), input.mediaAssetId(), input.role(),
                    input.captionBg(), input.captionEn(), null, null
            );
        }
    }
}
