package fmi.ethnowear.application.service.archive.query;

import fmi.ethnowear.application.dto.archive.query.ArchiveEvidenceDetails;
import fmi.ethnowear.application.dto.archive.query.RegionalEmbroideryArchiveOverviewDetails;
import fmi.ethnowear.application.dto.archive.query.RegionalEmbroideryArchiveSectionDetails;
import fmi.ethnowear.application.dto.catalogue.EntityOntologyDetails;
import fmi.ethnowear.application.dto.catalogue.ConceptEvidenceSummaryDetails;
import fmi.ethnowear.domain.model.ontology.FeatureType;
import fmi.ethnowear.application.service.catalogue.mapper.EntityCardMapper;
import fmi.ethnowear.application.service.catalogue.OntologyEntityDetailReader;
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
public class RegionalEmbroideryArchiveService {

    private static final int MAX_PREVIEW_SIZE = 12;

    private final OntologyEntityDetailReader ontologyReader;
    private final EntityCardMapper cardMapper;
    private final ArchiveEvidenceService evidenceService;

    public RegionalEmbroideryArchiveOverviewDetails findOverview(String languageTag, int previewSize) {
        validatePreviewSize(previewSize);

        String language = OntologyLanguage.fromTag(languageTag).tag();
        Pageable previewPageable = PageRequest.of(0, previewSize, Sort.by(Sort.Direction.DESC, "id"));

        List<RegionalEmbroideryArchiveSectionDetails> sections = ontologyReader
                .listSummaries(FeatureType.REGIONAL_EMBROIDERY, language)
                .stream()
                .sorted(Comparator.comparing(entity -> normalize(entity.label())))
                .map(entity -> toSection(entity, previewPageable))
                .toList();

        return new RegionalEmbroideryArchiveOverviewDetails(language, sections);
    }

    private RegionalEmbroideryArchiveSectionDetails toSection(
            EntityOntologyDetails entity,
            Pageable previewPageable
    ) {
        Page<ArchiveEvidenceDetails> evidence = evidenceService.findByOntologyEntity(
                FeatureType.REGIONAL_EMBROIDERY,
                entity.iri(),
                previewPageable
        );

        return new RegionalEmbroideryArchiveSectionDetails(
                cardMapper.toDetails(
                        entity,
                        new ConceptEvidenceSummaryDetails(
                                evidence.getTotalElements(),
                                evidence.getContent()
                                        .stream()
                                        .map(ArchiveEvidenceDetails::previewMedia)
                                        .filter(media -> media != null)
                                        .map(media -> media.mediaAssetId())
                                        .findFirst()
                                        .orElse(null)
                        )
                ),
                evidence.getTotalElements(),
                evidence.getContent()
        );
    }

    private void validatePreviewSize(int previewSize) {
        if(previewSize < 1 || previewSize > MAX_PREVIEW_SIZE)
            throw new IllegalArgumentException("Preview size must be between 1 and " + MAX_PREVIEW_SIZE);
    }
}
