CREATE TABLE [ethnowear].[ArchiveItemMedia]
(
    [Id] BIGINT IDENTITY(1,1) NOT NULL,
    [ArchiveItemId] BIGINT NOT NULL,
    [MediaAssetId] BIGINT NOT NULL,

    [Role] NVARCHAR(50) NOT NULL,
    [CaptionBg] NVARCHAR(MAX) NULL,
    [CaptionEn] NVARCHAR(MAX) NULL,
    [DisplayOrder] INT NOT NULL CONSTRAINT [DF_ArchiveItemMedia_DisplayOrder] DEFAULT 0,

    [CreatedAt] DATETIME2(7) NOT NULL CONSTRAINT [DF_ArchiveItemMedia_CreatedAt] DEFAULT SYSUTCDATETIME(),
    [UpdatedAt] DATETIME2(7) NOT NULL CONSTRAINT [DF_ArchiveItemMedia_UpdatedAt] DEFAULT SYSUTCDATETIME(),

    CONSTRAINT [PK_ArchiveItemMedia] PRIMARY KEY CLUSTERED ([Id]),

    CONSTRAINT [FK_ArchiveItemMedia_ArchiveItems]
        FOREIGN KEY ([ArchiveItemId])
        REFERENCES [ethnowear].[ArchiveItems] ([Id]),

    CONSTRAINT [FK_ArchiveItemMedia_MediaAssets]
        FOREIGN KEY ([MediaAssetId])
        REFERENCES [ethnowear].[MediaAssets] ([Id]),

    CONSTRAINT [CK_ArchiveItemMedia_Role]
        CHECK ([Role] IN (
            N'PRIMARY',
            N'DETAIL',
            N'SOURCE_SCAN',
            N'THUMBNAIL',
            N'OTHER'
        ))
);

GO

CREATE INDEX [IX_ArchiveItemMedia_ArchiveItemId]
ON [ethnowear].[ArchiveItemMedia] ([ArchiveItemId]);

GO

CREATE INDEX [IX_ArchiveItemMedia_MediaAssetId]
ON [ethnowear].[ArchiveItemMedia] ([MediaAssetId]);
