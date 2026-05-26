package fmi.ethnowear.dal.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "source_reference")
public class SourceReference extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_id", nullable = false)
    private Source source;

    private String chapter;

    @Column(name = "page_from")
    private Integer pageFrom;

    @Column(name = "page_to")
    private Integer pageTo;

    @Column(name = "figure_number")
    private String figureNumber;

    @Column(name = "section_title")
    private String sectionTitle;

    @Column(columnDefinition = "TEXT")
    private String note;
}
