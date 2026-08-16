CREATE TABLE [ethnowear].[MediaEntityLinks]
(
    [Id] BIGINT IDENTITY(1,1) NOT NULL,
    [MediaAssetId] BIGINT NOT NULL,
    [EntityType] NVARCHAR(50) NOT NULL,
    [OntologyIri] NVARCHAR(1000) NOT NULL,
    [OntologyLocalName] NVARCHAR(255) NOT NULL,
    [Description] NVARCHAR(1000) NULL,
    [CreatedAt] DATETIME2(7) NOT NULL CONSTRAINT [DF_MediaEntityLinks_CreatedAt] DEFAULT SYSUTCDATETIME(),
    [UpdatedAt] DATETIME2(7) NOT NULL CONSTRAINT [DF_MediaEntityLinks_UpdatedAt] DEFAULT SYSUTCDATETIME(),
    CONSTRAINT [PK_MediaEntityLinks] PRIMARY KEY CLUSTERED ([Id]),
    CONSTRAINT [FK_MediaEntityLinks_MediaAssets] FOREIGN KEY ([MediaAssetId])
        REFERENCES [ethnowear].[MediaAssets] ([Id]),
    CONSTRAINT [CK_MediaEntityLinks_EntityType] CHECK ([EntityType] IN (
        N'REGION', N'REGIONAL_EMBROIDERY', N'MOTIF',
        N'ORNAMENT', N'TECHNIQUE', N'COLOR'
    )),
    CONSTRAINT [UQ_MediaEntityLinks] UNIQUE ([MediaAssetId], [EntityType], [OntologyIri])
);
GO
CREATE INDEX [IX_MediaEntityLinks_OntologyIri]
ON [ethnowear].[MediaEntityLinks] ([OntologyIri]);
