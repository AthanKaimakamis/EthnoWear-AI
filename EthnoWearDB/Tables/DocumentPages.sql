CREATE TABLE [ethnowear].[DocumentPages]
(
    [Id] BIGINT IDENTITY(1,1) NOT NULL,
    [DocumentId] BIGINT NOT NULL,
    [SourceReferenceId] BIGINT NULL,

    [PageKind] NVARCHAR(50) NOT NULL,
    [PageRole] NVARCHAR(50) NOT NULL,
    [PageSequence] INT NOT NULL,
    [PdfPageIndex] INT NULL,
    [PrintedPageNumber] NVARCHAR(50) NULL,
    [PrintedPageSort] INT NULL,
    [PageLabel] NVARCHAR(100) NULL,
    [ProvenanceStatus] NVARCHAR(50) NOT NULL,

    [RawOcrText] NVARCHAR(MAX) NULL,
    [CorrectedText] NVARCHAR(MAX) NULL,
    [CorrectedTextHash] NVARCHAR(128) NULL,
    [OcrEngine] NVARCHAR(100) NULL,
    [OcrEngineVersion] NVARCHAR(100) NULL,
    [OcrLanguage] NVARCHAR(20) NULL,
    [OcrConfidence] DECIMAL(5,4) NULL,

    [CurrentQualityAssessmentId] BIGINT NULL,
    [CurrentQualityScore] DECIMAL(5,4) NULL,
    [CurrentQualityStatus] NVARCHAR(50) NULL,
    [CurrentQualityPassedChecks] INT NULL,
    [CurrentQualityFailedChecks] INT NULL,
    [CurrentQualityAssessedAt] DATETIME2(7) NULL,

    [ProcessingState] NVARCHAR(50) NOT NULL,
    [ReviewState] NVARCHAR(50) NOT NULL,
    [TranscriptionApprovalState] NVARCHAR(50) NOT NULL,
    [ProvenanceTrustState] NVARCHAR(50) NOT NULL,
    [IndexingState] NVARCHAR(50) NOT NULL,

    [Reviewer] NVARCHAR(150) NULL,
    [ReviewedAt] DATETIME2(7) NULL,
    [ReviewNotes] NVARCHAR(MAX) NULL,

    [EvidenceState] NVARCHAR(50) NOT NULL,
    [CanonicalDocumentPageId] BIGINT NULL,
    [ProvenanceNote] NVARCHAR(MAX) NULL,
    [ProvenanceReviewedBy] NVARCHAR(150) NULL,
    [ProvenanceReviewedAt] DATETIME2(7) NULL,

    [RetiredAt] DATETIME2(7) NULL,
    [RetiredBy] NVARCHAR(150) NULL,
    [RetirementReason] NVARCHAR(500) NULL,
    [RowVersion] ROWVERSION NOT NULL,

    [CreatedAt] DATETIME2(7) NOT NULL CONSTRAINT [DF_DocumentPages_CreatedAt] DEFAULT SYSUTCDATETIME(),
    [UpdatedAt] DATETIME2(7) NOT NULL CONSTRAINT [DF_DocumentPages_UpdatedAt] DEFAULT SYSUTCDATETIME(),

    CONSTRAINT [PK_DocumentPages] PRIMARY KEY CLUSTERED ([Id]),

    CONSTRAINT [FK_DocumentPages_Documents]
        FOREIGN KEY ([DocumentId])
        REFERENCES [ethnowear].[Documents] ([Id]),

    CONSTRAINT [FK_DocumentPages_SourceReference]
        FOREIGN KEY ([SourceReferenceId])
        REFERENCES [ethnowear].[SourceReference] ([Id]),

    CONSTRAINT [FK_DocumentPages_CanonicalDocumentPage]
        FOREIGN KEY ([CanonicalDocumentPageId])
        REFERENCES [ethnowear].[DocumentPages] ([Id]),

    CONSTRAINT [FK_DocumentPages_CurrentQualityAssessment]
        FOREIGN KEY ([CurrentQualityAssessmentId])
        REFERENCES [ethnowear].[DocumentPageQualityAssessments] ([Id]),

    CONSTRAINT [UQ_DocumentPages_DocumentId_PageSequence]
        UNIQUE ([DocumentId], [PageSequence]),

    CONSTRAINT [CK_DocumentPages_PageKind]
        CHECK ([PageKind] IN (
            N'DOCUMENT_PAGE',
            N'STANDALONE_IMAGE',
            N'UNKNOWN_FRAGMENT'
        )),

    CONSTRAINT [CK_DocumentPages_PageRole]
        CHECK ([PageRole] IN (
            N'NORMAL',
            N'MISSING_PAGE',
            N'SUPPLEMENTAL_PAGE'
        )),

    CONSTRAINT [CK_DocumentPages_ProvenanceStatus]
        CHECK ([ProvenanceStatus] IN (
            N'KNOWN_SOURCE',
            N'PARTIAL_SOURCE',
            N'UNKNOWN_SOURCE'
        )),

    CONSTRAINT [CK_DocumentPages_ProcessingState]
        CHECK ([ProcessingState] IN (
            N'UPLOADED',
            N'PENDING',
            N'PROCESSING',
            N'COMPLETED',
            N'FAILED',
            N'CANCELLED'
        )),

    CONSTRAINT [CK_DocumentPages_ReviewState]
        CHECK ([ReviewState] IN (
            N'NOT_READY',
            N'REVIEW_REQUIRED',
            N'IN_REVIEW',
            N'APPROVED',
            N'REJECTED'
        )),

    CONSTRAINT [CK_DocumentPages_TranscriptionApprovalState]
        CHECK ([TranscriptionApprovalState] IN (
            N'NOT_REQUIRED',
            N'PENDING',
            N'APPROVED',
            N'REJECTED'
        )),

    CONSTRAINT [CK_DocumentPages_ProvenanceTrustState]
        CHECK ([ProvenanceTrustState] IN (
            N'UNKNOWN',
            N'UNTRUSTED',
            N'PARTIAL',
            N'TRUSTED',
            N'VERIFIED'
        )),

    CONSTRAINT [CK_DocumentPages_IndexingState]
        CHECK ([IndexingState] IN (
            N'NOT_ELIGIBLE',
            N'PENDING',
            N'INDEXED',
            N'FAILED',
            N'OUTDATED'
        )),

    CONSTRAINT [CK_DocumentPages_EvidenceState]
        CHECK ([EvidenceState] IN (
            N'ACTIVE',
            N'SUPERSEDED',
            N'MERGED',
            N'RETIRED'
        )),

    CONSTRAINT [CK_DocumentPages_PageSequence]
        CHECK ([PageSequence] > 0),

    CONSTRAINT [CK_DocumentPages_PdfPageIndex]
        CHECK ([PdfPageIndex] IS NULL OR [PdfPageIndex] >= 0),

    CONSTRAINT [CK_DocumentPages_OcrConfidence]
        CHECK ([OcrConfidence] IS NULL OR [OcrConfidence] BETWEEN 0 AND 1),

    CONSTRAINT [CK_DocumentPages_CurrentQualityScore]
        CHECK ([CurrentQualityScore] IS NULL OR [CurrentQualityScore] BETWEEN 0 AND 1),

    CONSTRAINT [CK_DocumentPages_CurrentQualityStatus]
        CHECK ([CurrentQualityStatus] IS NULL OR [CurrentQualityStatus] IN (
            N'HIGH_QUALITY',
            N'MINOR_REVIEW',
            N'REVIEW_REQUIRED',
            N'POOR_QUALITY',
            N'PROCESSING_FAILED',
            N'INCOMPLETE'
        )),

    CONSTRAINT [CK_DocumentPages_CurrentQualityCounts]
        CHECK (
            ([CurrentQualityPassedChecks] IS NULL AND [CurrentQualityFailedChecks] IS NULL)
            OR (
                [CurrentQualityPassedChecks] >= 0
                AND [CurrentQualityFailedChecks] >= 0
            )
        ),

    CONSTRAINT [CK_DocumentPages_Retirement]
        CHECK (
            ([EvidenceState] <> N'RETIRED' AND [RetiredAt] IS NULL AND [RetiredBy] IS NULL AND [RetirementReason] IS NULL)
            OR (
                [EvidenceState] = N'RETIRED'
                AND [RetiredAt] IS NOT NULL
                AND [RetiredBy] IS NOT NULL
                AND LEN(LTRIM(RTRIM([RetiredBy]))) > 0
                AND [RetirementReason] IS NOT NULL
                AND LEN(LTRIM(RTRIM([RetirementReason]))) > 0
            )
        )
);

GO

CREATE UNIQUE INDEX [UQ_DocumentPages_DocumentId_PdfPageIndex]
ON [ethnowear].[DocumentPages] ([DocumentId], [PdfPageIndex])
WHERE [PdfPageIndex] IS NOT NULL;

GO

CREATE INDEX [IX_DocumentPages_DocumentId]
ON [ethnowear].[DocumentPages] ([DocumentId]);

GO

CREATE INDEX [IX_DocumentPages_SourceReferenceId]
ON [ethnowear].[DocumentPages] ([SourceReferenceId]);

GO

CREATE INDEX [IX_DocumentPages_DocumentId_PrintedPageSort]
ON [ethnowear].[DocumentPages] ([DocumentId], [PrintedPageSort]);

GO

CREATE INDEX [IX_DocumentPages_ProcessingState_ReviewState]
ON [ethnowear].[DocumentPages] ([ProcessingState], [ReviewState]);

GO

CREATE INDEX [IX_DocumentPages_TranscriptionApprovalState_ProvenanceTrustState]
ON [ethnowear].[DocumentPages] ([TranscriptionApprovalState], [ProvenanceTrustState]);

GO

CREATE INDEX [IX_DocumentPages_IndexingState]
ON [ethnowear].[DocumentPages] ([IndexingState]);

GO

CREATE INDEX [IX_DocumentPages_CanonicalDocumentPageId]
ON [ethnowear].[DocumentPages] ([CanonicalDocumentPageId])
WHERE [CanonicalDocumentPageId] IS NOT NULL;

GO

CREATE INDEX [IX_DocumentPages_DocumentId_CurrentQuality]
ON [ethnowear].[DocumentPages] (
    [DocumentId],
    [CurrentQualityStatus],
    [CurrentQualityScore],
    [CurrentQualityAssessedAt]
);

GO

CREATE INDEX [IX_DocumentPages_RetiredAt]
ON [ethnowear].[DocumentPages] ([RetiredAt])
WHERE [RetiredAt] IS NOT NULL;
