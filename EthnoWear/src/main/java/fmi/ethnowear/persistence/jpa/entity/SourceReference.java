package fmi.ethnowear.persistence.jpa.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "SourceReference", schema = "ethnowear")
public class SourceReference extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "SourceId", nullable = false)
    private Source source;

    @Column(name = "Chapter")
    private String chapter;

    @Column(name = "PageFrom")
    private Integer pageFrom;

    @Column(name = "PageTo")
    private Integer pageTo;

    @Column(name = "FigureNumber")
    private String figureNumber;

    @Column(name = "SectionTitle")
    private String sectionTitle;

    @Column(name = "CatalogNumber")
    private String catalogNumber;

    @Column(name = "ReferenceUrl")
    private String referenceUrl;

    @Column(name = "AccessedDate")
    private LocalDate accessedDate;

    @Column(name = "Locator")
    private String locator;

    @Lob
    @Column(name = "Note")
    private String note;
}
