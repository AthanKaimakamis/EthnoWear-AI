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
@Table(name = "archive_item_media")
public class ArchiveItemMedia extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "archive_item_id", nullable = false)
    private ArchiveItem archiveItem;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "media_asset_id", nullable = false)
    private MediaAsset mediaAsset;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MediaRole role;

    @Column(name = "caption_bg", columnDefinition = "TEXT")
    private String captionBg;

    @Column(name = "caption_en", columnDefinition = "TEXT")
    private String captionEn;
}
