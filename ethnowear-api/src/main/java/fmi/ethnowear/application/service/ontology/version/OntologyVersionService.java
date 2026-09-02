package fmi.ethnowear.application.service.ontology.version;

import fmi.ethnowear.application.dto.ontology.admin.OntologyVersionContent;
import fmi.ethnowear.application.dto.ontology.admin.OntologyVersionDetails;
import fmi.ethnowear.application.dto.ontology.admin.OntologyVersionRestoreCommand;
import fmi.ethnowear.application.exception.OntologyVersionNotFoundException;
import fmi.ethnowear.application.exception.OntologyVersionRestoreException;
import fmi.ethnowear.application.model.ontology.OntologyChangeMetadata;
import fmi.ethnowear.application.model.ontology.OntologySnapshot;
import fmi.ethnowear.domain.model.ontology.OntologyVersionStatus;
import fmi.ethnowear.infrastructure.ontology.jena.JenaOntologyStore;
import fmi.ethnowear.persistence.jpa.entity.ontology.OntologyVersion;
import fmi.ethnowear.persistence.jpa.repository.ontology.OntologyVersionRepository;
import fmi.ethnowear.util.PageableUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OntologyVersionService {

    private static final int MAXIMUM_PAGE_SIZE = 100;

    private final OntologyVersionRepository repository;
    private final OntologyVersionMapper mapper;
    private final JenaOntologyStore store;

    @Transactional(readOnly = true)
    public Page<OntologyVersionDetails> list(Pageable pageable) {
        Pageable bounded = PageableUtils.boundedUnsorted(
                pageable,
                MAXIMUM_PAGE_SIZE,
                "Ontology version"
        );
        Pageable ordered = PageRequest.of(
                bounded.getPageNumber(),
                bounded.getPageSize(),
                Sort.by(Sort.Direction.DESC, "versionNumber")
        );
        return repository.findAllSummaries(ordered).map(mapper::toDetails);
    }

    @Transactional(readOnly = true)
    public OntologyVersionDetails get(Long versionId) {
        return mapper.toDetails(repository.findSummaryById(versionId)
                .orElseThrow(() -> new OntologyVersionNotFoundException(versionId)));
    }

    @Transactional(readOnly = true)
    public OntologyVersionDetails getLatest() {
        return mapper.toDetails(repository.findSummaryByStatus(OntologyVersionStatus.ACTIVE)
                .orElseThrow(OntologyVersionNotFoundException::new));
    }

    @Transactional(readOnly = true)
    public OntologyVersionContent getContent(Long versionId) {
        return toContent(repository.findById(versionId)
                .orElseThrow(() -> new OntologyVersionNotFoundException(versionId)));
    }

    @Transactional(readOnly = true)
    public OntologyVersionContent getLatestContent() {
        return toContent(repository.findByStatus(OntologyVersionStatus.ACTIVE)
                .orElseThrow(OntologyVersionNotFoundException::new));
    }

    public OntologyVersionDetails restore(
            Long versionId,
            OntologyVersionRestoreCommand command,
            Long userId
    ) {
        OntologyVersion selected = repository.findById(versionId)
                .orElseThrow(() -> new OntologyVersionNotFoundException(versionId));

        if (!selected.isValid())
            throw new OntologyVersionRestoreException("Invalid ontology versions cannot be restored", null);

        OntologySnapshot snapshot = new OntologySnapshot(
                selected.getOntologyContent(),
                selected.getContentHash(),
                selected.getFileName(),
                selected.getOntologyNamespace()
        );

        try {
            Long restoredId = store.restore(
                    snapshot,
                    selected.getId(),
                    new OntologyChangeMetadata(userId, command.reason())
            );
            return get(restoredId);
        } catch (OntologyVersionRestoreException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new OntologyVersionRestoreException("Ontology version could not be restored", ex);
        }
    }

    private OntologyVersionContent toContent(OntologyVersion version) {
        return new OntologyVersionContent(
                version.getId(),
                version.getVersionNumber(),
                version.getFileName(),
                version.getOntologyContent()
        );
    }

}
