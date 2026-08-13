package fmi.ethnowear.application.service.archive.source;

import fmi.ethnowear.api.dto.archive.source.SourceReferenceDetails;
import fmi.ethnowear.api.dto.archive.source.SourceReferenceWriteDto;
import fmi.ethnowear.dal.entity.Source;
import fmi.ethnowear.dal.entity.SourceReference;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;

@Component
public class SourceReferenceMapper {

    public void apply(@NonNull SourceReference reference, @NonNull SourceReferenceWriteDto input, Source source) {
        reference.setSource(source);
        reference.setChapter(input.chapter());
        reference.setPageFrom(input.pageFrom());
        reference.setPageTo(input.pageTo());
        reference.setFigureNumber(input.figureNumber());
        reference.setSectionTitle(input.sectionTitle());
        reference.setCatalogNumber(input.catalogNumber());
        reference.setReferenceUrl(input.referenceUrl());
        reference.setAccessedDate(input.accessedDate());
        reference.setLocator(input.locator());
        reference.setNote(input.note());
    }

    public SourceReferenceDetails toDetails(@NonNull SourceReference reference) {
        return new SourceReferenceDetails(
                reference.getId(),
                reference.getSource().getId(),
                reference.getChapter(),
                reference.getPageFrom(),
                reference.getPageTo(),
                reference.getFigureNumber(),
                reference.getSectionTitle(),
                reference.getCatalogNumber(),
                reference.getReferenceUrl(),
                reference.getAccessedDate(),
                reference.getLocator(),
                reference.getNote(),
                reference.getCreatedAt(),
                reference.getUpdatedAt()
        );
    }
}
