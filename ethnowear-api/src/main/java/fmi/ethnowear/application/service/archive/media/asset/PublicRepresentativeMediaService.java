package fmi.ethnowear.application.service.archive.media.asset;

import fmi.ethnowear.domain.model.archive.MediaRole;
import fmi.ethnowear.domain.model.archive.MediaType;
import fmi.ethnowear.domain.model.document.figure.FigureReviewState;
import fmi.ethnowear.persistence.jpa.entity.ArchiveItemMedia;
import fmi.ethnowear.persistence.jpa.repository.ArchiveItemMediaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PublicRepresentativeMediaService {

    private static final List<MediaRole> REPRESENTATIVE_ROLES = List.of(
            MediaRole.THUMBNAIL,
            MediaRole.PRIMARY,
            MediaRole.DETAIL
    );

    private final ArchiveItemMediaRepository repository;

    public Map<Long, Long> findByArchiveItemIds(Collection<Long> archiveItemIds) {
        if (archiveItemIds == null || archiveItemIds.isEmpty())
            return Map.of();

        Map<Long, Long> result = repository.findPublicByArchiveItemIdsRolesAndMediaType(
                        archiveItemIds.stream().distinct().toList(),
                        REPRESENTATIVE_ROLES,
                        MediaType.IMAGE,
                        FigureReviewState.APPROVED
                )
                .stream()
                .sorted(representativeComparator())
                .collect(Collectors.toMap(
                        media -> media.getArchiveItem().getId(),
                        media -> media.getMediaAsset().getId(),
                        (first, ignored) -> first,
                        LinkedHashMap::new
                ));
        return Collections.unmodifiableMap(result);
    }

    private Comparator<ArchiveItemMedia> representativeComparator() {
        return Comparator
                .comparingInt((ArchiveItemMedia media) ->
                        REPRESENTATIVE_ROLES.indexOf(media.getRole()))
                .thenComparing(ArchiveItemMedia::getId);
    }
}
