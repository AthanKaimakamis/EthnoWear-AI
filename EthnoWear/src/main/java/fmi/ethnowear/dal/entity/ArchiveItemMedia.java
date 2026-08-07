package fmi.ethnowear.dal.entity;

import fmi.ethnowear.application.enums.MediaRole;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "ArchiveItemMedia", schema = "ethnowear")
public class ArchiveItemMedia extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ArchiveItemId", nullable = false)
    private ArchiveItem archiveItem;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "MediaAssetId", nullable = false)
    private MediaAsset mediaAsset;

    @Enumerated(EnumType.STRING)
    @Column(name = "Role", nullable = false)
    private MediaRole role;

    @Lob
    @Column(name = "CaptionBg")
    private String captionBg;

    @Lob
    @Column(name = "CaptionEn")
    private String captionEn;
}
