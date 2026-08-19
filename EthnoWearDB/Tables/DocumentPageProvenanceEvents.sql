CREATE TABLE [ethnowear].[DocumentPageProvenanceEvents]
(
    [Id] BIGINT IDENTITY(1,1) NOT NULL,
    [DocumentPageId] BIGINT NOT NULL,

    [EventType] NVARCHAR(50) NOT NULL,
    [PreviousSourceReferenceId] BIGINT NULL,
    [NewSourceReferenceId] BIGINT NULL,
    [PreviousProvenanceStatus] NVARCHAR(50) NULL,
    [NewProvenanceStatus] NVARCHAR(50) NOT NULL,
    [PreviousTrustState] NVARCHAR(50) NULL,
    [NewTrustState] NVARCHAR(50) NOT NULL,
    [PreviousCanonicalDocumentPageId] BIGINT NULL,
    [NewCanonicalDocumentPageId] BIGINT NULL,
    [ReviewedBy] NVARCHAR(150) NOT NULL,
    [Reason] NVARCHAR(1000) NOT NULL,
    [CreatedAt] DATETIME2(7) NOT NULL CONSTRAINT [DF_DocumentPageProvenanceEvents_CreatedAt] DEFAULT SYSUTCDATETIME(),

    CONSTRAINT [PK_DocumentPageProvenanceEvents] PRIMARY KEY CLUSTERED ([Id]),

    CONSTRAINT [FK_DocumentPageProvenanceEvents_DocumentPages]
        FOREIGN KEY ([DocumentPageId])
        REFERENCES [ethnowear].[DocumentPages] ([Id]),

    CONSTRAINT [FK_DocumentPageProvenanceEvents_PreviousSourceReference]
        FOREIGN KEY ([PreviousSourceReferenceId])
        REFERENCES [ethnowear].[SourceReference] ([Id]),

    CONSTRAINT [FK_DocumentPageProvenanceEvents_NewSourceReference]
        FOREIGN KEY ([NewSourceReferenceId])
        REFERENCES [ethnowear].[SourceReference] ([Id]),

    CONSTRAINT [FK_DocumentPageProvenanceEvents_PreviousCanonicalPage]
        FOREIGN KEY ([PreviousCanonicalDocumentPageId])
        REFERENCES [ethnowear].[DocumentPages] ([Id]),

    CONSTRAINT [FK_DocumentPageProvenanceEvents_NewCanonicalPage]
        FOREIGN KEY ([NewCanonicalDocumentPageId])
        REFERENCES [ethnowear].[DocumentPages] ([Id]),

    CONSTRAINT [CK_DocumentPageProvenanceEvents_EventType]
        CHECK ([EventType] IN (
            N'SOURCE_IDENTIFIED',
            N'SOURCE_REFERENCE_CHANGED',
            N'TRUST_CHANGED',
            N'LINKED_TO_CANONICAL_PAGE',
            N'MERGED',
            N'LINK_REVERSED'
        )),

    CONSTRAINT [CK_DocumentPageProvenanceEvents_PreviousProvenanceStatus]
        CHECK (
            [PreviousProvenanceStatus] IS NULL
            OR [PreviousProvenanceStatus] IN (
                N'KNOWN_SOURCE',
                N'PARTIAL_SOURCE',
                N'UNKNOWN_SOURCE'
            )
        ),

    CONSTRAINT [CK_DocumentPageProvenanceEvents_NewProvenanceStatus]
        CHECK ([NewProvenanceStatus] IN (
            N'KNOWN_SOURCE',
            N'PARTIAL_SOURCE',
            N'UNKNOWN_SOURCE'
        )),

    CONSTRAINT [CK_DocumentPageProvenanceEvents_PreviousTrustState]
        CHECK (
            [PreviousTrustState] IS NULL
            OR [PreviousTrustState] IN (
                N'UNKNOWN',
                N'UNTRUSTED',
                N'PARTIAL',
                N'TRUSTED',
                N'VERIFIED'
            )
        ),

    CONSTRAINT [CK_DocumentPageProvenanceEvents_NewTrustState]
        CHECK ([NewTrustState] IN (
            N'UNKNOWN',
            N'UNTRUSTED',
            N'PARTIAL',
            N'TRUSTED',
            N'VERIFIED'
        )),

    CONSTRAINT [CK_DocumentPageProvenanceEvents_ReviewedBy]
        CHECK (LEN(LTRIM(RTRIM([ReviewedBy]))) > 0),

    CONSTRAINT [CK_DocumentPageProvenanceEvents_Reason]
        CHECK (LEN(LTRIM(RTRIM([Reason]))) > 0)
);

GO

CREATE INDEX [IX_DocumentPageProvenanceEvents_DocumentPageId_CreatedAt]
ON [ethnowear].[DocumentPageProvenanceEvents] ([DocumentPageId], [CreatedAt] DESC);

GO

CREATE INDEX [IX_DocumentPageProvenanceEvents_NewSourceReferenceId]
ON [ethnowear].[DocumentPageProvenanceEvents] ([NewSourceReferenceId])
WHERE [NewSourceReferenceId] IS NOT NULL;

GO

CREATE INDEX [IX_DocumentPageProvenanceEvents_NewCanonicalPageId]
ON [ethnowear].[DocumentPageProvenanceEvents] ([NewCanonicalDocumentPageId])
WHERE [NewCanonicalDocumentPageId] IS NOT NULL;
