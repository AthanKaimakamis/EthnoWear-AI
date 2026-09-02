CREATE TABLE [ethnowear].[ArchiveItemFeatures]
(
    [Id] BIGINT IDENTITY(1,1) NOT NULL,
    [ArchiveItemId] BIGINT NOT NULL,
    [SourceReferenceId] BIGINT NULL,

    [FeatureType] NVARCHAR(50) NOT NULL,
    [OntologyIri] NVARCHAR(1000) NOT NULL,
    [OntologyLocalName] NVARCHAR(200) NOT NULL,

    [Confidence] DECIMAL(5,4) NULL,
    [Validated] BIT NOT NULL CONSTRAINT [DF_ArchiveItemFeatures_Validated] DEFAULT (0),
    [Notes] NVARCHAR(MAX) NULL,

    [CreatedAt] DATETIME2(7) NOT NULL CONSTRAINT [DF_ArchiveItemFeatures_CreatedAt] DEFAULT SYSUTCDATETIME(),
    [UpdatedAt] DATETIME2(7) NOT NULL CONSTRAINT [DF_ArchiveItemFeatures_UpdatedAt] DEFAULT SYSUTCDATETIME(),

    CONSTRAINT [PK_ArchiveItemFeatures] PRIMARY KEY CLUSTERED ([Id]),

    CONSTRAINT [FK_ArchiveItemFeatures_ArchiveItems]
        FOREIGN KEY ([ArchiveItemId])
        REFERENCES [ethnowear].[ArchiveItems] ([Id]),

    CONSTRAINT [FK_ArchiveItemFeatures_SourceReference]
        FOREIGN KEY ([SourceReferenceId])
        REFERENCES [ethnowear].[SourceReference] ([Id]),

    CONSTRAINT [CK_ArchiveItemFeatures_FeatureType]
        CHECK ([FeatureType] IN (
            N'ORNAMENT',
            N'COLOR',
            N'TECHNIQUE',
            N'MOTIF',
            N'REGION',
            N'REGIONAL_EMBROIDERY',
            N'REGIONAL_MOTIF'
        )),

    CONSTRAINT [CK_ArchiveItemFeatures_Confidence]
        CHECK ([Confidence] IS NULL OR ([Confidence] >= 0 AND [Confidence] <= 1))
);

GO

CREATE INDEX [IX_ArchiveItemFeatures_ArchiveItemId]
ON [ethnowear].[ArchiveItemFeatures] ([ArchiveItemId]);

GO

CREATE INDEX [IX_ArchiveItemFeatures_SourceReferenceId]
ON [ethnowear].[ArchiveItemFeatures] ([SourceReferenceId]);

GO

CREATE INDEX [IX_ArchiveItemFeatures_FeatureType_OntologyLocalName]
ON [ethnowear].[ArchiveItemFeatures] ([FeatureType], [OntologyLocalName]);
