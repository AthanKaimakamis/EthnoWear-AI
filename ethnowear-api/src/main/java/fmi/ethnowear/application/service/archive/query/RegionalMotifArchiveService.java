package fmi.ethnowear.application.service.archive.query;

import fmi.ethnowear.application.dto.archive.query.ArchiveEvidenceDetails;
import fmi.ethnowear.application.dto.archive.query.RegionalMotifArchiveOverviewDetails;
import fmi.ethnowear.application.dto.archive.query.RegionalMotifArchiveSectionDetails;
import fmi.ethnowear.application.dto.catalogue.ConceptEvidenceSummaryDetails;
import fmi.ethnowear.application.dto.catalogue.EntityOntologyDetails;
import fmi.ethnowear.application.service.catalogue.OntologyEntityDetailReader;
import fmi.ethnowear.application.service.catalogue.mapper.EntityCardMapper;
import fmi.ethnowear.domain.model.ontology.FeatureType;
import fmi.ethnowear.domain.model.ontology.OntologyLanguage;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

import static fmi.ethnowear.util.TextUtils.normalize;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RegionalMotifArchiveService {

    private static final int MAX_PREVIEW_SIZE = 12;

    private final OntologyEntityDetailReader ontologyReader;
    private final EntityCardMapper cardMapper;
    private final ArchiveEvidenceService evidenceService;

    public RegionalMotifArchiveOverviewDetails findOverview(String languageTag, int previewSize) {
        if(previewSize < 1 || previewSize > MAX_PREVIEW_SIZE)
            throw new IllegalArgumentException(
                    "Preview size must be between 1 and " + MAX_PREVIEW_SIZE
            );

        String language = OntologyLanguage.fromTag(languageTag).tag();
        Pageable previewPageable = PageRequest.of(
                0,
                previewSize,
                Sort.by(Sort.Direction.DESC, "id")
        );
        List<RegionalMotifArchiveSectionDetails> sections = ontologyReader
                .listSummaries(FeatureType.REGIONAL_MOTIF, language)
                .stream()
                .sorted(Comparator.comparing(entity -> normalize(entity.label())))
                .map(entity -> toSection(entity, previewPageable))
                .toList();

        return new RegionalMotifArchiveOverviewDetails(language, sections);
    }

    private RegionalMotifArchiveSectionDetails toSection(
            EntityOntologyDetails entity,
            Pageable previewPageable
    ) {
        Page<ArchiveEvidenceDetails> evidence = evidenceService.findByOntologyEntity(
                FeatureType.REGIONAL_MOTIF,
                entity.iri(),
                previewPageable
        );
        Long representativeMediaId = evidence.getContent().stream()
                .map(ArchiveEvidenceDetails::previewMedia)
                .filter(media -> media != null)
                .map(media -> media.mediaAssetId())
                .findFirst()
                .orElse(null);

        return new RegionalMotifArchiveSectionDetails(
                cardMapper.toDetails(
                        entity,
                        new ConceptEvidenceSummaryDetails(
                                evidence.getTotalElements(),
                                representativeMediaId
                        )
                ),
                evidence.getTotalElements(),
                evidence.getContent()
        );
    }
}
