package fmi.ethnowear.persistence.jpa.entity;

import fmi.ethnowear.domain.model.archive.SourceType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "Sources", schema = "ethnowear")
public class Source extends BaseEntity {

    @Column(name = "Title", nullable = false)
    private String title;

    @Column(name = "Author")
    private String author;

    @Column(name = "Publisher")
    private String publisher;

    @Column(name = "PublicationYear")
    private Integer year;

    @Enumerated(EnumType.STRING)
    @Column(name = "SourceType", nullable = false)
    private SourceType sourceType;

    @Column(name = "Language")
    private String language;

    @Column(name = "FilePath")
    private String filePath;

    @Column(name = "Url")
    private String url;

    @Column(name = "Isbn")
    private String isbn;

    @Lob
    @Column(name = "Notes")
    private String notes;

    @Column(name = "IsTrusted", nullable = false)
    private boolean trusted;

}
