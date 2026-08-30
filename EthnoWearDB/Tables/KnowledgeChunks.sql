CREATE TABLE [ethnowear].[KnowledgeChunks]
(
    [Id] BIGINT IDENTITY(1,1) NOT NULL,
    [SourceReferenceId] BIGINT NULL,
    [DocumentId] BIGINT NULL,
    [ArchiveItemId] BIGINT NULL,

    [ChunkType] NVARCHAR(50) NOT NULL,
    [OntologyIri] NVARCHAR(1000) NULL,
    [OntologyLocalName] NVARCHAR(200) NULL,

    [Language] NVARCHAR(10) NOT NULL,
    [Content] NVARCHAR(MAX) NOT NULL,
    [SourceTextType] NVARCHAR(50) NOT NULL,
    [ChunkOrdinal] INT NULL,
    [ContentHash] NVARCHAR(128) NOT NULL,
    [GenerationInputHash] CHAR(64) NULL,
    [ChunkingStrategy] NVARCHAR(100) NULL,
    [ChunkingVersion] NVARCHAR(50) NULL,
    [ReviewState] NVARCHAR(50) NOT NULL,
    [TranscriptionApprovalState] NVARCHAR(50) NOT NULL,
    [ProvenanceTrustState] NVARCHAR(50) NOT NULL,
    [IndexingState] NVARCHAR(50) NOT NULL,

    [EmbeddingModel] NVARCHAR(100) NULL,
    [EmbeddingDimensions] INT NULL,
    [VectorCollection] NVARCHAR(150) NULL,
    [VectorPointId] NVARCHAR(255) NULL,
    [IndexedContentHash] NVARCHAR(128) NULL,
    [IndexedAt] DATETIME2(7) NULL,
    [IndexingError] NVARCHAR(1000) NULL,
    [SupersededByKnowledgeChunkId] BIGINT NULL,

    [CreatedAt] DATETIME2(7) NOT NULL CONSTRAINT [DF_KnowledgeChunks_CreatedAt] DEFAULT SYSUTCDATETIME(),
    [UpdatedAt] DATETIME2(7) NOT NULL CONSTRAINT [DF_KnowledgeChunks_UpdatedAt] DEFAULT SYSUTCDATETIME(),

    CONSTRAINT [PK_KnowledgeChunks] PRIMARY KEY CLUSTERED ([Id]),

    CONSTRAINT [FK_KnowledgeChunks_SourceReference]
        FOREIGN KEY ([SourceReferenceId])
        REFERENCES [ethnowear].[SourceReference] ([Id]),

    CONSTRAINT [FK_KnowledgeChunks_Documents]
        FOREIGN KEY ([DocumentId])
        REFERENCES [ethnowear].[Documents] ([Id]),

    CONSTRAINT [FK_KnowledgeChunks_ArchiveItems]
        FOREIGN KEY ([ArchiveItemId])
        REFERENCES [ethnowear].[ArchiveItems] ([Id]),

    CONSTRAINT [FK_KnowledgeChunks_SupersededBy]
        FOREIGN KEY ([SupersededByKnowledgeChunkId])
        REFERENCES [ethnowear].[KnowledgeChunks] ([Id]),

    CONSTRAINT [CK_KnowledgeChunks_ChunkType]
        CHECK ([ChunkType] IN (
            N'GENERAL',
            N'REGION',
            N'ORNAMENT',
            N'TECHNIQUE',
            N'MOTIF',
            N'COLOR',
            N'REGIONAL_EMBROIDERY',
            N'SOURCE_EXCERPT',
            N'BOOK_EXCERPT',
            N'STANDALONE_EVIDENCE'
        )),

    CONSTRAINT [CK_KnowledgeChunks_SourceTextType]
        CHECK ([SourceTextType] IN (
            N'CORRECTED_PAGE_TEXT',
            N'CURATED_ENTITY_CONTENT',
            N'ARCHIVE_DESCRIPTION',
            N'SOURCE_NOTE',
            N'MANUAL_EXCERPT'
        )),

    CONSTRAINT [CK_KnowledgeChunks_ReviewState]
        CHECK ([ReviewState] IN (
            N'NOT_READY',
            N'REVIEW_REQUIRED',
            N'IN_REVIEW',
            N'APPROVED',
            N'REJECTED'
        )),

    CONSTRAINT [CK_KnowledgeChunks_TranscriptionApprovalState]
        CHECK ([TranscriptionApprovalState] IN (
            N'NOT_REQUIRED',
            N'PENDING',
            N'APPROVED',
            N'REJECTED'
        )),

    CONSTRAINT [CK_KnowledgeChunks_ProvenanceTrustState]
        CHECK ([ProvenanceTrustState] IN (
            N'UNKNOWN',
            N'UNTRUSTED',
            N'PARTIAL',
            N'TRUSTED',
            N'VERIFIED'
        )),

    CONSTRAINT [CK_KnowledgeChunks_IndexingState]
        CHECK ([IndexingState] IN (
            N'NOT_ELIGIBLE',
            N'PENDING',
            N'INDEXED',
            N'FAILED',
            N'OUTDATED'
        )),

    CONSTRAINT [CK_KnowledgeChunks_ChunkOrdinal]
        CHECK ([ChunkOrdinal] IS NULL OR [ChunkOrdinal] >= 0),

    CONSTRAINT [CK_KnowledgeChunks_GenerationMetadata]
        CHECK (
            (
                [GenerationInputHash] IS NULL
                AND [ChunkingStrategy] IS NULL
                AND [ChunkingVersion] IS NULL
            )
            OR (
                [GenerationInputHash] IS NOT NULL
                AND LEN([GenerationInputHash]) = 64
                AND [GenerationInputHash] COLLATE Latin1_General_100_BIN2
                    NOT LIKE '%[^0-9a-f]%'
                AND [ChunkingStrategy] IS NOT NULL
                AND LEN(LTRIM(RTRIM([ChunkingStrategy]))) > 0
                AND [ChunkingVersion] IS NOT NULL
                AND LEN(LTRIM(RTRIM([ChunkingVersion]))) > 0
                AND [DocumentId] IS NOT NULL
                AND [ChunkOrdinal] IS NOT NULL
                AND [SourceTextType] = N'CORRECTED_PAGE_TEXT'
            )
        ),

    CONSTRAINT [CK_KnowledgeChunks_EmbeddingDimensions]
        CHECK ([EmbeddingDimensions] IS NULL OR [EmbeddingDimensions] > 0),

    CONSTRAINT [CK_KnowledgeChunks_CorrectedPageDocument]
        CHECK ([SourceTextType] <> N'CORRECTED_PAGE_TEXT' OR [DocumentId] IS NOT NULL),

    CONSTRAINT [CK_KnowledgeChunks_BookExcerpt]
        CHECK (
            [ChunkType] <> N'BOOK_EXCERPT'
            OR (
                [SourceTextType] = N'CORRECTED_PAGE_TEXT'
                AND [DocumentId] IS NOT NULL
            )
        ),

    CONSTRAINT [CK_KnowledgeChunks_StandaloneEvidence]
        CHECK (
            [ChunkType] <> N'STANDALONE_EVIDENCE'
            OR [SourceTextType] = N'CORRECTED_PAGE_TEXT'
        ),

    CONSTRAINT [CK_KnowledgeChunks_IndexedMetadata]
        CHECK (
            [IndexingState] <> N'INDEXED'
            OR (
                [VectorCollection] IS NOT NULL
                AND [VectorPointId] IS NOT NULL
                AND [IndexedContentHash] IS NOT NULL
                AND [IndexedAt] IS NOT NULL
            )
        ),

    CONSTRAINT [CK_KnowledgeChunks_IndexedBookExcerptEligibility]
        CHECK (
            [IndexingState] <> N'INDEXED'
            OR [ChunkType] <> N'BOOK_EXCERPT'
            OR (
                [TranscriptionApprovalState] = N'APPROVED'
                AND [ProvenanceTrustState] IN (N'TRUSTED', N'VERIFIED')
                AND [SourceReferenceId] IS NOT NULL
            )
        ),

    CONSTRAINT [CK_KnowledgeChunks_IndexedStandaloneEligibility]
        CHECK (
            [IndexingState] <> N'INDEXED'
            OR [ChunkType] <> N'STANDALONE_EVIDENCE'
            OR [TranscriptionApprovalState] = N'APPROVED'
        )
);

GO

CREATE INDEX [IX_KnowledgeChunks_SourceReferenceId]
ON [ethnowear].[KnowledgeChunks] ([SourceReferenceId]);

GO

CREATE INDEX [IX_KnowledgeChunks_DocumentId]
ON [ethnowear].[KnowledgeChunks] ([DocumentId]);

GO

CREATE INDEX [IX_KnowledgeChunks_ArchiveItemId]
ON [ethnowear].[KnowledgeChunks] ([ArchiveItemId]);

GO

CREATE INDEX [IX_KnowledgeChunks_ChunkType]
ON [ethnowear].[KnowledgeChunks] ([ChunkType]);

GO

CREATE INDEX [IX_KnowledgeChunks_OntologyLocalName]
ON [ethnowear].[KnowledgeChunks] ([OntologyLocalName])
WHERE [OntologyLocalName] IS NOT NULL;

GO

CREATE INDEX [IX_KnowledgeChunks_IndexingEligibility]
ON [ethnowear].[KnowledgeChunks] ([IndexingState], [ProvenanceTrustState], [TranscriptionApprovalState]);

GO

CREATE INDEX [IX_KnowledgeChunks_ContentHash]
ON [ethnowear].[KnowledgeChunks] ([ContentHash]);

GO

CREATE INDEX [IX_KnowledgeChunks_VectorPointId]
ON [ethnowear].[KnowledgeChunks] ([VectorPointId])
WHERE [VectorPointId] IS NOT NULL;

GO

CREATE INDEX [IX_KnowledgeChunks_SupersededByKnowledgeChunkId]
ON [ethnowear].[KnowledgeChunks] ([SupersededByKnowledgeChunkId])
WHERE [SupersededByKnowledgeChunkId] IS NOT NULL;

GO

CREATE UNIQUE INDEX [UQ_KnowledgeChunks_Document_Generation_Ordinal]
ON [ethnowear].[KnowledgeChunks] (
    [DocumentId],
    [GenerationInputHash],
    [ChunkOrdinal]
)
WHERE [GenerationInputHash] IS NOT NULL;
