package fmi.ethnowear.application.service.archive.query;

import fmi.ethnowear.application.dto.archive.query.EntityContentDetails;
import fmi.ethnowear.domain.model.ontology.FeatureType;
import fmi.ethnowear.domain.model.archive.KnowledgeChunkType;
import fmi.ethnowear.domain.model.archive.MediaFeatureAnnotationType;
import fmi.ethnowear.domain.model.archive.MediaRole;
import fmi.ethnowear.domain.model.archive.MediaType;
import fmi.ethnowear.domain.model.archive.SourceType;
import fmi.ethnowear.persistence.jpa.entity.ArchiveItem;
import fmi.ethnowear.persistence.jpa.entity.ArchiveItemFeature;
import fmi.ethnowear.persistence.jpa.entity.ArchiveItemMedia;
import fmi.ethnowear.persistence.jpa.entity.KnowledgeChunk;
import fmi.ethnowear.persistence.jpa.entity.MediaAsset;
import fmi.ethnowear.persistence.jpa.entity.MediaFeatureAnnotation;
import fmi.ethnowear.persistence.jpa.entity.Source;
import fmi.ethnowear.persistence.jpa.entity.SourceReference;
import fmi.ethnowear.persistence.jpa.repository.ArchiveItemFeatureRepository;
import fmi.ethnowear.persistence.jpa.repository.KnowledgeChunkRepository;
import fmi.ethnowear.persistence.jpa.repository.MediaFeatureAnnotationRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static fmi.ethnowear.support.RepositoryTestProxies.proxy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EntityContentServiceTest {

    private static final String TECHNIQUE_IRI = "http://example.org/ontology#ChainStitch";

    @Test
    void combinesContentAndReturnsSortedDeduplicatedSources() {
        SourceReference firstReference = reference(10L, "First source");
        SourceReference secondReference = reference(20L, "Second source");
        KnowledgeChunk chunk = chunk(secondReference);
        ArchiveItemFeature feature = feature(firstReference);
        ArchiveItemFeature duplicateReferenceFeature = feature(secondReference);
        MediaFeatureAnnotation annotation = annotation(feature);

        KnowledgeChunkRepository chunkRepository = proxy(
                KnowledgeChunkRepository.class,
                (ignored, method, arguments) -> {
                    assertEquals("findByOntologyIriAndLanguageOrderByIdAsc", method.getName());
                    assertEquals(TECHNIQUE_IRI, arguments[0]);
                    assertEquals("bg", arguments[1]);
                    return List.of(chunk);
                }
        );
        ArchiveItemFeatureRepository featureRepository = proxy(
                ArchiveItemFeatureRepository.class,
                (ignored, method, arguments) -> {
                    assertEquals(FeatureType.TECHNIQUE, arguments[0]);
                    assertEquals(TECHNIQUE_IRI, arguments[1]);
                    return List.of(feature, duplicateReferenceFeature);
                }
        );
        MediaFeatureAnnotationRepository annotationRepository = proxy(
                MediaFeatureAnnotationRepository.class,
                (ignored, method, arguments) -> {
                    assertEquals(FeatureType.TECHNIQUE, arguments[0]);
                    assertEquals(TECHNIQUE_IRI, arguments[1]);
                    return List.of(annotation);
                }
        );
        EntityContentService service = new EntityContentService(
                chunkRepository,
                featureRepository,
                annotationRepository,
                new EntityContentMapper(new EntitySourceCitationMapper())
        );

        EntityContentDetails details = service.findByOntologyEntity(
                FeatureType.TECHNIQUE,
                TECHNIQUE_IRI,
                "bg"
        );

        assertEquals(1, details.knowledgeChunks().size());
        assertEquals(1, details.mediaAnnotations().size());
        assertEquals(2, details.sources().size());
        assertEquals(10L, details.sources().get(0).sourceReferenceId());
        assertEquals(20L, details.sources().get(1).sourceReferenceId());
        assertEquals(30L, details.mediaAnnotations().getFirst().archiveItemId());
        assertEquals(20L, details.knowledgeChunks().getFirst().sourceReferenceId());
    }

    @Test
    void rejectsMissingQueryParameters() {
        EntityContentService service = new EntityContentService(null, null, null, null);

        IllegalArgumentException missingType = assertThrows(
                IllegalArgumentException.class,
                () -> service.findByOntologyEntity(null, TECHNIQUE_IRI, "bg")
        );
        IllegalArgumentException missingIri = assertThrows(
                IllegalArgumentException.class,
                () -> service.findByOntologyEntity(FeatureType.TECHNIQUE, " ", "bg")
        );
        IllegalArgumentException missingLanguage = assertThrows(
                IllegalArgumentException.class,
                () -> service.findByOntologyEntity(FeatureType.TECHNIQUE, TECHNIQUE_IRI, " ")
        );

        assertEquals("Ontology entity type is required", missingType.getMessage());
        assertEquals("Ontology IRI is required", missingIri.getMessage());
        assertEquals("Language is required", missingLanguage.getMessage());
    }

    private SourceReference reference(Long id, String title) {
        Source source = new Source();
        source.setId(id + 100);
        source.setTitle(title);
        source.setSourceType(SourceType.BOOK);
        source.setTrusted(true);

        SourceReference reference = new SourceReference();
        reference.setId(id);
        reference.setSource(source);
        reference.setPageFrom(10);
        reference.setPageTo(12);
        return reference;
    }

    private KnowledgeChunk chunk(SourceReference reference) {
        KnowledgeChunk chunk = new KnowledgeChunk();
        chunk.setId(25L);
        chunk.setChunkType(KnowledgeChunkType.TECHNIQUE);
        chunk.setOntologyIri(TECHNIQUE_IRI);
        chunk.setOntologyLocalName("ChainStitch");
        chunk.setLanguage("bg");
        chunk.setContent("Technique content");
        chunk.setSourceReference(reference);
        return chunk;
    }

    private ArchiveItemFeature feature(SourceReference reference) {
        ArchiveItem item = new ArchiveItem();
        item.setId(30L);

        ArchiveItemFeature feature = new ArchiveItemFeature();
        feature.setId(reference.getId() + 30);
        feature.setArchiveItem(item);
        feature.setFeatureType(FeatureType.TECHNIQUE);
        feature.setOntologyIri(TECHNIQUE_IRI);
        feature.setOntologyLocalName("ChainStitch");
        feature.setValidated(true);
        feature.setSourceReference(reference);
        return feature;
    }

    private MediaFeatureAnnotation annotation(ArchiveItemFeature feature) {
        MediaAsset asset = new MediaAsset();
        asset.setId(50L);
        asset.setFileName("chain-stitch.jpg");
        asset.setStorageUrl("/media/chain-stitch.jpg");
        asset.setMimeType("image/jpeg");
        asset.setMediaType(MediaType.IMAGE);

        ArchiveItemMedia itemMedia = new ArchiveItemMedia();
        itemMedia.setId(60L);
        itemMedia.setArchiveItem(feature.getArchiveItem());
        itemMedia.setMediaAsset(asset);
        itemMedia.setRole(MediaRole.DETAIL);

        MediaFeatureAnnotation annotation = new MediaFeatureAnnotation();
        annotation.setId(70L);
        annotation.setArchiveItemFeature(feature);
        annotation.setArchiveItemMedia(itemMedia);
        annotation.setAnnotationType(MediaFeatureAnnotationType.VISIBLE_IN_IMAGE);
        return annotation;
    }
}
