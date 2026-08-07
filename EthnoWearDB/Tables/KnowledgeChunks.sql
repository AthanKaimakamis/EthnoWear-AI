CREATE TABLE [ethnowear].[KnowledgeChunks]
(
    [Id] BIGINT IDENTITY(1,1) NOT NULL,
    [SourceReferenceId] BIGINT NULL,

    [ChunkType] NVARCHAR(50) NOT NULL,
    [OntologyIri] NVARCHAR(1000) NULL,
    [OntologyLocalName] NVARCHAR(200) NULL,

    [Language] NVARCHAR(10) NOT NULL,
    [Content] NVARCHAR(MAX) NOT NULL,

    [EmbeddingModel] NVARCHAR(100) NULL,
    [EmbeddingId] NVARCHAR(255) NULL,

    [CreatedAt] DATETIME2(7) NOT NULL CONSTRAINT [DF_KnowledgeChunks_CreatedAt] DEFAULT SYSUTCDATETIME(),
    [UpdatedAt] DATETIME2(7) NOT NULL CONSTRAINT [DF_KnowledgeChunks_UpdatedAt] DEFAULT SYSUTCDATETIME(),

    CONSTRAINT [PK_KnowledgeChunks] PRIMARY KEY CLUSTERED ([Id]),

    CONSTRAINT [FK_KnowledgeChunks_SourceReference]
        FOREIGN KEY ([SourceReferenceId])
        REFERENCES [ethnowear].[SourceReference] ([Id]),

    CONSTRAINT [CK_KnowledgeChunks_ChunkType]
        CHECK ([ChunkType] IN (
            N'GENERAL',
            N'REGION',
            N'ORNAMENT',
            N'TECHNIQUE',
            N'MOTIF',
            N'COLOR',
            N'REGIONAL_EMBROIDERY',
            N'SOURCE_EXCERPT'
        ))
);

GO

CREATE INDEX [IX_KnowledgeChunks_SourceReferenceId]
ON [ethnowear].[KnowledgeChunks] ([SourceReferenceId]);

GO

CREATE INDEX [IX_KnowledgeChunks_ChunkType]
ON [ethnowear].[KnowledgeChunks] ([ChunkType]);

GO

CREATE INDEX [IX_KnowledgeChunks_OntologyLocalName]
ON [ethnowear].[KnowledgeChunks] ([OntologyLocalName])
WHERE [OntologyLocalName] IS NOT NULL;
