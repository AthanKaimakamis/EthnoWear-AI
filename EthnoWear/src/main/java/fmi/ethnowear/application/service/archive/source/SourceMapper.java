package fmi.ethnowear.application.service.archive.source;

import fmi.ethnowear.application.dto.archive.source.SourceDetails;
import fmi.ethnowear.application.dto.archive.source.SourceWriteDto;
import fmi.ethnowear.persistence.jpa.entity.Source;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;

@Component
public class SourceMapper {

    public void apply(@NonNull Source source, @NonNull SourceWriteDto input) {
        source.setTitle(input.title().trim());
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
    }

    public SourceDetails toDetails(@NonNull Source source) {
        return new SourceDetails(
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
                source.getNotes(),
                source.isTrusted(),
                source.getCreatedAt(),
                source.getUpdatedAt()
        );
    }
}
