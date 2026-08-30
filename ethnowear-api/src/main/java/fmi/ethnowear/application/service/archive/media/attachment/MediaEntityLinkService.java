package fmi.ethnowear.application.service.archive.media.attachment;

import fmi.ethnowear.application.dto.archive.media.*;
import fmi.ethnowear.application.exception.ResourceNotFoundException;
import fmi.ethnowear.application.service.CrudService;
import fmi.ethnowear.domain.model.ontology.OntologyIdentity;
import fmi.ethnowear.persistence.jpa.entity.MediaAsset;
import fmi.ethnowear.persistence.jpa.entity.MediaEntityLink;
import fmi.ethnowear.persistence.jpa.repository.MediaAssetRepository;
import fmi.ethnowear.persistence.jpa.repository.MediaEntityLinkRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;

import static fmi.ethnowear.util.IdentifierUtils.requireId;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MediaEntityLinkService implements CrudService<MediaEntityLinkWriteDto, MediaEntityLinkDetails> {
    private final MediaEntityLinkRepository repository;
    private final MediaAssetRepository assetRepository;

    public Page<MediaEntityLinkDetails> findAll(Pageable pageable) {
        return repository.findAll(pageable).map(this::details);
    }

    public MediaEntityLinkDetails findById(Long id) {
        return details(require(id));
    }

    @Transactional
    public MediaEntityLinkDetails create(MediaEntityLinkWriteDto input) {
        return details(repository.save(apply(new MediaEntityLink(), input)));
    }

    @Transactional
    public MediaEntityLinkDetails update(Long id, MediaEntityLinkWriteDto input) {
        return details(repository.save(apply(require(id), input)));
    }

    @Transactional
    public void delete(Long id) {
        repository.delete(require(id));
    }

    private MediaEntityLink apply(MediaEntityLink link, MediaEntityLinkWriteDto input) {
        if (input == null)
            throw new IllegalArgumentException("Media entity link input is required");

        requireId(input.mediaAssetId(), "Media asset");

        OntologyIdentity identity = new OntologyIdentity(
                input.ontologyIri(),
                input.ontologyLocalName()
        );

        if (!identity.isComplete())
            throw new IllegalArgumentException("Ontology IRI and local name are required");

        MediaAsset asset = assetRepository.findById(input.mediaAssetId())
                .orElseThrow(() -> new ResourceNotFoundException("Media asset", input.mediaAssetId()));
        URI iri;
        try {
            iri = URI.create(identity.iri().trim());
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException("Invalid ontology IRI", ex);
        }

        if (!iri.isAbsolute())
            throw new IllegalArgumentException("Ontology IRI must be absolute");

        link.setMediaAsset(asset);
        link.setEntityType(input.entityType());
        link.setOntologyIri(iri.toString());
        link.setOntologyLocalName(identity.localName().trim());
        link.setDescription(input.description());
        return link;
    }

    private MediaEntityLink require(Long id) {
        requireId(id, "Media entity link");

        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Media entity link", id));
    }

    private MediaEntityLinkDetails details(MediaEntityLink link) {
        return new MediaEntityLinkDetails(
                link.getId(),
                link.getMediaAsset().getId(),
                link.getEntityType(),
                link.getOntologyIri(),
                link.getOntologyLocalName(),
                link.getDescription(),
                link.getCreatedAt(),
                link.getUpdatedAt()
        );
    }
}
