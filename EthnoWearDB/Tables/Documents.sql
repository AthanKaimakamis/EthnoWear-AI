CREATE TABLE [ethnowear].[Documents]
(
    [Id] BIGINT IDENTITY(1,1) NOT NULL,
    [SourceId] BIGINT NULL,
    [OriginalMediaAssetId] BIGINT NULL,

    [DocumentType] NVARCHAR(50) NOT NULL,
    [ProvenanceStatus] NVARCHAR(50) NOT NULL,

    [Title] NVARCHAR(300) NOT NULL,
    [Author] NVARCHAR(200) NULL,
    [Publisher] NVARCHAR(200) NULL,
    [PublicationYear] INT NULL,
    [Language] NVARCHAR(10) NULL,
    [PageCount] INT NULL,

    [ProcessingState] NVARCHAR(50) NOT NULL,
    [ReviewState] NVARCHAR(50) NOT NULL,
    [ProvenanceTrustState] NVARCHAR(50) NOT NULL,
    [IndexingState] NVARCHAR(50) NOT NULL,

    [Notes] NVARCHAR(MAX) NULL,
    [MergedIntoDocumentId] BIGINT NULL,

    [CreatedAt] DATETIME2(7) NOT NULL CONSTRAINT [DF_Documents_CreatedAt] DEFAULT SYSUTCDATETIME(),
    [UpdatedAt] DATETIME2(7) NOT NULL CONSTRAINT [DF_Documents_UpdatedAt] DEFAULT SYSUTCDATETIME(),

    CONSTRAINT [PK_Documents] PRIMARY KEY CLUSTERED ([Id]),

    CONSTRAINT [FK_Documents_Sources]
        FOREIGN KEY ([SourceId])
        REFERENCES [ethnowear].[Sources] ([Id]),

    CONSTRAINT [FK_Documents_OriginalMediaAsset]
        FOREIGN KEY ([OriginalMediaAssetId])
        REFERENCES [ethnowear].[MediaAssets] ([Id]),

    CONSTRAINT [FK_Documents_MergedInto]
        FOREIGN KEY ([MergedIntoDocumentId])
        REFERENCES [ethnowear].[Documents] ([Id]),

    CONSTRAINT [CK_Documents_DocumentType]
        CHECK ([DocumentType] IN (
            N'PDF_DOCUMENT',
            N'SCANNED_BOOK',
            N'PAGE_IMAGE_SET',
            N'STANDALONE_CAPTURE',
            N'UNKNOWN_FRAGMENT_SET'
        )),

    CONSTRAINT [CK_Documents_ProvenanceStatus]
        CHECK ([ProvenanceStatus] IN (
            N'KNOWN_SOURCE',
            N'PARTIAL_SOURCE',
            N'UNKNOWN_SOURCE'
        )),

    CONSTRAINT [CK_Documents_ProcessingState]
        CHECK ([ProcessingState] IN (
            N'UPLOADED',
            N'PENDING',
            N'PROCESSING',
            N'COMPLETED',
            N'FAILED',
            N'CANCELLED'
        )),

    CONSTRAINT [CK_Documents_ReviewState]
        CHECK ([ReviewState] IN (
            N'NOT_READY',
            N'REVIEW_REQUIRED',
            N'IN_REVIEW',
            N'APPROVED',
            N'REJECTED'
        )),

    CONSTRAINT [CK_Documents_ProvenanceTrustState]
        CHECK ([ProvenanceTrustState] IN (
            N'UNKNOWN',
            N'UNTRUSTED',
            N'PARTIAL',
            N'TRUSTED',
            N'VERIFIED'
        )),

    CONSTRAINT [CK_Documents_IndexingState]
        CHECK ([IndexingState] IN (
            N'NOT_ELIGIBLE',
            N'PENDING',
            N'INDEXED',
            N'FAILED',
            N'OUTDATED'
        )),

    CONSTRAINT [CK_Documents_PageCount]
        CHECK ([PageCount] IS NULL OR [PageCount] >= 0),

    CONSTRAINT [CK_Documents_KnownSourceRequiresSource]
        CHECK ([ProvenanceStatus] <> N'KNOWN_SOURCE' OR [SourceId] IS NOT NULL)
);

GO

CREATE INDEX [IX_Documents_SourceId]
ON [ethnowear].[Documents] ([SourceId]);

GO

CREATE INDEX [IX_Documents_OriginalMediaAssetId]
ON [ethnowear].[Documents] ([OriginalMediaAssetId]);

GO

CREATE INDEX [IX_Documents_DocumentType_ProvenanceStatus]
ON [ethnowear].[Documents] ([DocumentType], [ProvenanceStatus]);

GO

CREATE INDEX [IX_Documents_ProcessingState_ReviewState]
ON [ethnowear].[Documents] ([ProcessingState], [ReviewState]);

GO

CREATE INDEX [IX_Documents_IndexingState]
ON [ethnowear].[Documents] ([IndexingState]);

GO

CREATE INDEX [IX_Documents_MergedIntoDocumentId]
ON [ethnowear].[Documents] ([MergedIntoDocumentId])
WHERE [MergedIntoDocumentId] IS NOT NULL;
