package fmi.ethnowear.dal.entity;

import fmi.ethnowear.application.enums.SourceType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "sources")
public class Source extends BaseEntity {

    private String title;
    private String author;
    private String publisher;

    @Column(name = "publication_year")
    private Integer year;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false)
    private SourceType sourceType;

    private String language;

    @Column(name = "file_path")
    private String filePath;

    private String url;
    private String isbn;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "is_trusted", nullable = false)
    private boolean trusted;

}
