package fmi.ethnowear.application.service.archive.query;

import fmi.ethnowear.api.dto.archive.query.EntitySourceCitationDetails;
import fmi.ethnowear.dal.entity.Source;
import fmi.ethnowear.dal.entity.SourceReference;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

@Component
public class EntitySourceCitationMapper {

    public EntitySourceCitationDetails toDetails(@NotNull SourceReference reference) {
        Source source = reference.getSource();

        return new EntitySourceCitationDetails(
                reference.getId(),
                source.getId(),
                source.getTitle(),
                source.getAuthor(),
                source.getPublisher(),
                source.getYear(),
                source.getSourceType(),
                source.getLanguage(),
                source.getFilePath(),
                source.getUrl(),
                source.getIsbn(),
                source.isTrusted(),
                reference.getChapter(),
                reference.getPageFrom(),
                reference.getPageTo(),
                reference.getFigureNumber(),
                reference.getSectionTitle(),
                reference.getCatalogNumber(),
                reference.getReferenceUrl(),
                reference.getAccessedDate(),
                reference.getLocator(),
                reference.getNote()
        );
    }
}
