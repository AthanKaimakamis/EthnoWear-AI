package fmi.ethnowear.application.service.archive;

import fmi.ethnowear.api.dto.archive.*;
import fmi.ethnowear.dal.entity.*;
import fmi.ethnowear.dal.repository.*;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ArchiveAdminService {

    private final SourceRepository sourceRepository;
    private final SourceReferenceRepository sourceReferenceRepository;
    private final ArchiveItemRepository archiveItemRepository;
    private final ArchiveItemFeatureRepository archiveItemFeatureRepository;
    private final MediaAssetRepository mediaAssetRepository;
    private final ArchiveItemMediaRepository archiveItemMediaRepository;
    private final KnowledgeChunkRepository knowledgeChunkRepository;

    @Transactional
    public CreateArchiveItemResponse createFullArchiveItem(CreateArchiveItemFullRequest request) {
        Source source = resolveSource(request.source());
        SourceReference sourceReference = createSourceReference(request.sourceRefence(), source);
        ArchiveItem archiveItem = createArchiveItem(request.archiveItem(), sourceReference);

        int createdFeaturesCount = createFeatures(request.features(), archiveItem, sourceReference);
        int createdMediaCount = createMedia(request.media(), archiveItem, sourceReference);
        int createdKnowledgeChunksCount = createKnowledgeChunk(request.knowledgeChunks(), sourceReference);

        return new CreateArchiveItemResponse(
                archiveItem.getId(),
                source.getId(),
                sourceReference.getId(),
                createdFeaturesCount,
                createdMediaCount,
                createdKnowledgeChunksCount
        );
    }

    private Source resolveSource(SourceInputDto input) {
        if (input.existingSourceId() != null)
            return sourceRepository.findById(input.existingSourceId())
                    .orElseThrow(() -> new EntityNotFoundException(
                            "Source not found: " + input.existingSourceId()));

        Source source = new Source();
        source.setTitle(input.title());
        source.setAuthor(input.author());
        source.setPublisher(input.publisher());
        source.setYear(input.year());
        source.setSourceType(input.sourceType());
        source.setLanguage(input.language());
        source.setFilePath(input.filePath());
        source.setUrl(input.url());
        source.setIsbn(input.isbn());
        source.setNotes(input.notes());
        source.setTrusted(input.trusted());

        return sourceRepository.save(source);
    }

    private SourceReference createSourceReference(SourceReferenceInputDto input, Source source) {
        SourceReference reference = new SourceReference();
        reference.setSource(source);
        reference.setChapter(input.chapter());
        reference.setPageFrom(input.pageFrom());
        reference.setPageTo(input.pageTo());
        reference.setFigureNumber(input.figureNumber());
        reference.setSectionTitle(input.sectionTitle());
        reference.setNote(input.note());

        return sourceReferenceRepository.save(reference);
    }

    private ArchiveItem createArchiveItem(ArchiveItemInputDto input, SourceReference sourceReference) {
        ArchiveItem item = new ArchiveItem();
        item.setSourceReference(sourceReference);
        item.setCollectionId(input.collectionId());
        item.setInventoryNumber(input.inventoryNumber());
        item.setTitleBg(input.titleBg());
        item.setTitleEn(input.titleEn());
        item.setDescriptionBg(input.descriptionBg());
        item.setDescriptionEn(input.descriptionEn());
        item.setArchiveType(input.archiveType());
        item.setPeriodText(input.periodText());
        item.setOriginText(input.originText());
        item.setCurrentLocation(input.currentLocation());
        item.setTrustedLevel(input.trustedLevel());
        item.setOntologyRegionIri(input.ontologyRegionIri());
        item.setOntologyRegionLocalName(input.ontologyRegionLocalName());
        item.setOntologyRegionalEmbroideryIri(input.ontologyRegionalEmbroideryIri());
        item.setOntologyRegionalEmbroideryLocalName(input.ontologyRegionalEmbroideryLocalName());

        return archiveItemRepository.save(item);
    }

    private int createFeatures(List<ArchiveFeatureInputDto> inputs, ArchiveItem archiveItem, SourceReference sourceReference) {
        if(inputs == null || inputs.isEmpty())
            return 0;

        List<ArchiveItemFeature> features = inputs.stream()
                .map(input -> {
                    ArchiveItemFeature feature = new ArchiveItemFeature();
                    feature.setArchiveItem(archiveItem);
                    feature.setFeatureType(input.featureType());
                    feature.setOntologyIri(input.ontologyIri());
                    feature.setOntologyLocalName(input.ontologyLocalName());
                    feature.setConfidence(input.confidence());
                    feature.setValidated(input.validated());
                    feature.setNotes(input.notes());
                    feature.setSourceReference(sourceReference);
                    return feature;
                })
                .toList();

        archiveItemFeatureRepository.saveAll(features);
        return features.size();
    }

    private int createMedia(List<MediaAssetInputDto> inputs, ArchiveItem archiveItem, SourceReference sourceReference) {
        if(inputs == null || inputs.isEmpty())
            return 0;

        int count = 0;

        for(MediaAssetInputDto input : inputs){
            MediaAsset mediaAsset = new MediaAsset();
            mediaAsset.setFileName(input.fileName());
            mediaAsset.setFilePath(input.filePath());
            mediaAsset.setStorageUrl(input.storageUrl());
            mediaAsset.setMimeType(input.mimeType());
            mediaAsset.setMediaType(input.mediaType());
            mediaAsset.setWidth(input.width());
            mediaAsset.setHeight(input.height());
            mediaAsset.setSizeBytes(input.sizeBytes());
            mediaAsset.setChecksum(input.checksum());
            mediaAsset.setSourceReference(sourceReference);

            MediaAsset savedMediaAsset = mediaAssetRepository.save(mediaAsset);

            ArchiveItemMedia link = new ArchiveItemMedia();
            link.setArchiveItem(archiveItem);
            link.setMediaAsset(savedMediaAsset);
            link.setRole(input.role());
            link.setCaptionBg(input.captionBg());
            link.setCaptionEn(input.captionEn());

            archiveItemMediaRepository.save(link);
            count++;
        }

        return count;
    }

    private int createKnowledgeChunk(List<KnowledgeChunkInputDto> inputs, SourceReference sourceReference) {
        if(inputs == null || inputs.isEmpty())
            return 0;

        List<KnowledgeChunk> chunks = inputs.stream()
                .map(input -> {
                    KnowledgeChunk chunk = new KnowledgeChunk();
                    chunk.setChunkType(input.chunkType());
                    chunk.setOntologyIri(input.ontologyIri());
                    chunk.setOntologyLocalName(input.ontologyLocalName());
                    chunk.setLanguage(input.language());
                    chunk.setContent(input.content());
                    chunk.setSourceReference(sourceReference);
                    chunk.setEmbeddingModel(input.embeddingModel());
                    chunk.setEmbeddingId(input.embeddingId());
                    return chunk;
                })
                .toList();

        knowledgeChunkRepository.saveAll(chunks);
        return chunks.size();
    }
}
