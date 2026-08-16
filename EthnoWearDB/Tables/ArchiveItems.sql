CREATE TABLE [ethnowear].[ArchiveItems]
(
    [Id] BIGINT IDENTITY(1,1) NOT NULL,
    [SourceReferenceId] BIGINT NULL,

    [CollectionId] NVARCHAR(100) NULL,
    [InventoryNumber] NVARCHAR(100) NULL,

    [TitleBg] NVARCHAR(300) NULL,
    [TitleEn] NVARCHAR(300) NULL,
    [DescriptionBg] NVARCHAR(MAX) NULL,
    [DescriptionEn] NVARCHAR(MAX) NULL,

    [ArchiveType] NVARCHAR(50) NOT NULL,
    [TrustedLevel] NVARCHAR(50) NOT NULL,

    [PublicationStatus] NVARCHAR(50) NOT NULL CONSTRAINT [DF_ArchiveItems_PublicationStatus] DEFAULT N'DRAFT',

    [SubmittedAt] DATETIME2(7) NULL,
    [PublishedAt] DATETIME2(7) NULL,
    [ArchivedAt] DATETIME2(7) NULL,

    [PeriodText] NVARCHAR(150) NULL,
    [OriginText] NVARCHAR(300) NULL,
    [CurrentLocation] NVARCHAR(300) NULL,

    [OntologyRegionIri] NVARCHAR(1000) NULL,
    [OntologyRegionLocalName] NVARCHAR(200) NULL,
    [OntologyRegionalEmbroideryIri] NVARCHAR(1000) NULL,
    [OntologyRegionalEmbroideryLocalName] NVARCHAR(200) NULL,

    [CreatedAt] DATETIME2(7) NOT NULL CONSTRAINT [DF_ArchiveItems_CreatedAt] DEFAULT SYSUTCDATETIME(),
    [UpdatedAt] DATETIME2(7) NOT NULL CONSTRAINT [DF_ArchiveItems_UpdatedAt] DEFAULT SYSUTCDATETIME(),

    CONSTRAINT [PK_ArchiveItems] PRIMARY KEY CLUSTERED ([Id]),

    CONSTRAINT [FK_ArchiveItems_SourceReference]
        FOREIGN KEY ([SourceReferenceId])
        REFERENCES [ethnowear].[SourceReference] ([Id]),

    CONSTRAINT [CK_ArchiveItems_ArchiveType]
        CHECK ([ArchiveType] IN (
            N'ORNAMENT_EXAMPLE',
            N'EMBROIDERY_SAMPLE',
            N'CLOTHING_ITEM',
            N'PHOTO_REFERENCE',
            N'TEXT_REFERENCE'
        )),

    CONSTRAINT [CK_ArchiveItems_TrustedLevel]
        CHECK ([TrustedLevel] IN (
            N'VERIFIED',
            N'LIKELY',
            N'UNVERIFIED'
        )),

    CONSTRAINT [CK_ArchiveItems_PublicationStatus]
        CHECK ([PublicationStatus] IN (
            N'DRAFT',
            N'IN_REVIEW',
            N'PUBLISHED',
            N'ARCHIVED'
        ))
);

GO

CREATE INDEX [IX_ArchiveItems_SourceReferenceId]
ON [ethnowear].[ArchiveItems] ([SourceReferenceId]);

GO

CREATE INDEX [IX_ArchiveItems_OntologyRegionLocalName]
ON [ethnowear].[ArchiveItems] ([OntologyRegionLocalName]);

GO

CREATE INDEX [IX_ArchiveItems_OntologyRegionalEmbroideryLocalName]
ON [ethnowear].[ArchiveItems] ([OntologyRegionalEmbroideryLocalName]);

GO

CREATE INDEX [IX_ArchiveItems_ArchiveType]
ON [ethnowear].[ArchiveItems] ([ArchiveType]);

GO

CREATE INDEX [IX_ArchiveItems_TrustedLevel]
ON [ethnowear].[ArchiveItems] ([TrustedLevel]);

GO

CREATE INDEX [IX_ArchiveItems_PublicationStatus]
ON [ethnowear].[ArchiveItems] ([PublicationStatus]);

GO
