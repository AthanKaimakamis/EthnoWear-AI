package fmi.ethnowear.application.service.archive.query;

import fmi.ethnowear.api.dto.archive.query.ArchiveEvidenceDetails;
import fmi.ethnowear.api.dto.archive.query.RegionalEmbroideryArchiveOverviewDetails;
import fmi.ethnowear.api.dto.archive.query.RegionalEmbroideryArchiveSectionDetails;
import fmi.ethnowear.api.dto.catalogue.EntityOntologyDetails;
import fmi.ethnowear.application.enums.FeatureType;
import fmi.ethnowear.application.service.catalogue.EntityCardMapper;
import fmi.ethnowear.application.service.catalogue.OntologyEntityDetailReader;
import fmi.ethnowear.ontology.enums.OntologyLanguage;
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
                .list(FeatureType.REGIONAL_EMBROIDERY, language)
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
                cardMapper.toDetails(entity),
                evidence.getTotalElements(),
                evidence.getContent()
        );
    }

    private void validatePreviewSize(int previewSize) {
        if(previewSize < 1 || previewSize > MAX_PREVIEW_SIZE)
            throw new IllegalArgumentException("Preview size must be between 1 and " + MAX_PREVIEW_SIZE);
    }
}
