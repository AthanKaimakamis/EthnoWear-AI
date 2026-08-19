CREATE TABLE [ethnowear].[KnowledgeChunkPages]
(
    [Id] BIGINT IDENTITY(1,1) NOT NULL,
    [KnowledgeChunkId] BIGINT NOT NULL,
    [DocumentPageId] BIGINT NOT NULL,

    [PageOrder] INT NOT NULL,
    [StartCharOffset] INT NULL,
    [EndCharOffset] INT NULL,
    [StartsOnPage] BIT NOT NULL,
    [EndsOnPage] BIT NOT NULL,
    [CitationPrintedPageNumber] NVARCHAR(50) NULL,
    [CitationPdfPageIndex] INT NULL,
    [CitationLabel] NVARCHAR(300) NULL,

    [CreatedAt] DATETIME2(7) NOT NULL CONSTRAINT [DF_KnowledgeChunkPages_CreatedAt] DEFAULT SYSUTCDATETIME(),
    [UpdatedAt] DATETIME2(7) NOT NULL CONSTRAINT [DF_KnowledgeChunkPages_UpdatedAt] DEFAULT SYSUTCDATETIME(),

    CONSTRAINT [PK_KnowledgeChunkPages] PRIMARY KEY CLUSTERED ([Id]),

    CONSTRAINT [FK_KnowledgeChunkPages_KnowledgeChunks]
        FOREIGN KEY ([KnowledgeChunkId])
        REFERENCES [ethnowear].[KnowledgeChunks] ([Id]),

    CONSTRAINT [FK_KnowledgeChunkPages_DocumentPages]
        FOREIGN KEY ([DocumentPageId])
        REFERENCES [ethnowear].[DocumentPages] ([Id]),

    CONSTRAINT [UQ_KnowledgeChunkPages_Chunk_Page]
        UNIQUE ([KnowledgeChunkId], [DocumentPageId]),

    CONSTRAINT [UQ_KnowledgeChunkPages_Chunk_PageOrder]
        UNIQUE ([KnowledgeChunkId], [PageOrder]),

    CONSTRAINT [CK_KnowledgeChunkPages_PageOrder]
        CHECK ([PageOrder] > 0),

    CONSTRAINT [CK_KnowledgeChunkPages_StartCharOffset]
        CHECK ([StartCharOffset] IS NULL OR [StartCharOffset] >= 0),

    CONSTRAINT [CK_KnowledgeChunkPages_EndCharOffset]
        CHECK ([EndCharOffset] IS NULL OR [EndCharOffset] >= 0),

    CONSTRAINT [CK_KnowledgeChunkPages_CharOffsetRange]
        CHECK (
            [EndCharOffset] IS NULL
            OR [StartCharOffset] IS NULL
            OR [EndCharOffset] >= [StartCharOffset]
        ),

    CONSTRAINT [CK_KnowledgeChunkPages_CitationPdfPageIndex]
        CHECK ([CitationPdfPageIndex] IS NULL OR [CitationPdfPageIndex] >= 0)
);

GO

CREATE INDEX [IX_KnowledgeChunkPages_KnowledgeChunkId]
ON [ethnowear].[KnowledgeChunkPages] ([KnowledgeChunkId]);

GO

CREATE INDEX [IX_KnowledgeChunkPages_DocumentPageId]
ON [ethnowear].[KnowledgeChunkPages] ([DocumentPageId]);

GO

CREATE INDEX [IX_KnowledgeChunkPages_Chunk_PageOrder]
ON [ethnowear].[KnowledgeChunkPages] ([KnowledgeChunkId], [PageOrder]);
