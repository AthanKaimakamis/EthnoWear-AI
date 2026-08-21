CREATE TABLE [ethnowear].[DocumentPageMedia]
(
    [Id] BIGINT IDENTITY(1,1) NOT NULL,
    [DocumentPageId] BIGINT NOT NULL,
    [MediaAssetId] BIGINT NOT NULL,

    [RenditionType] NVARCHAR(50) NOT NULL,
    [IsOriginal] BIT NOT NULL,
    [IsPreferredOcrInput] BIT NOT NULL,
    [DerivativeOfDocumentPageMediaId] BIGINT NULL,
    [ProducingJobId] BIGINT NULL,
    [ProducingAttempt] INT NULL,
    [DisplayOrder] INT NOT NULL,

    [Width] INT NULL,
    [Height] INT NULL,
    [Dpi] INT NULL,
    [ColorMode] NVARCHAR(50) NULL,
    [RenditionHash] NVARCHAR(128) NULL,
    [Notes] NVARCHAR(MAX) NULL,

    [CreatedAt] DATETIME2(7) NOT NULL CONSTRAINT [DF_DocumentPageMedia_CreatedAt] DEFAULT SYSUTCDATETIME(),
    [UpdatedAt] DATETIME2(7) NOT NULL CONSTRAINT [DF_DocumentPageMedia_UpdatedAt] DEFAULT SYSUTCDATETIME(),

    CONSTRAINT [PK_DocumentPageMedia] PRIMARY KEY CLUSTERED ([Id]),

    CONSTRAINT [FK_DocumentPageMedia_DocumentPages]
        FOREIGN KEY ([DocumentPageId])
        REFERENCES [ethnowear].[DocumentPages] ([Id]),

    CONSTRAINT [FK_DocumentPageMedia_MediaAssets]
        FOREIGN KEY ([MediaAssetId])
        REFERENCES [ethnowear].[MediaAssets] ([Id]),

    CONSTRAINT [FK_DocumentPageMedia_DerivativeOf]
        FOREIGN KEY ([DerivativeOfDocumentPageMediaId])
        REFERENCES [ethnowear].[DocumentPageMedia] ([Id]),

    CONSTRAINT [FK_DocumentPageMedia_ProducingJob]
        FOREIGN KEY ([ProducingJobId])
        REFERENCES [ethnowear].[DocumentProcessingJobs] ([Id]),

    CONSTRAINT [UQ_DocumentPageMedia_Page_Media]
        UNIQUE ([DocumentPageId], [MediaAssetId]),

    CONSTRAINT [CK_DocumentPageMedia_RenditionType]
        CHECK ([RenditionType] IN (
            N'ORIGINAL_UPLOAD',
            N'PDF_PAGE_RENDER',
            N'PHONE_PHOTO',
            N'PREPROCESSED_OCR_INPUT',
            N'CROPPED',
            N'DESKEWED',
            N'BINARIZED',
            N'SEARCHABLE_PDF_PAGE',
            N'THUMBNAIL',
            N'REPLACEMENT_SCAN'
        )),

    CONSTRAINT [CK_DocumentPageMedia_DisplayOrder]
        CHECK ([DisplayOrder] >= 0),

    CONSTRAINT [CK_DocumentPageMedia_Dimensions]
        CHECK (
            ([Width] IS NULL OR [Width] > 0)
            AND ([Height] IS NULL OR [Height] > 0)
            AND ([Dpi] IS NULL OR [Dpi] > 0)
        ),

    CONSTRAINT [CK_DocumentPageMedia_OriginalDerivative]
        CHECK ([IsOriginal] = 0 OR [DerivativeOfDocumentPageMediaId] IS NULL),

    CONSTRAINT [CK_DocumentPageMedia_ProducingAttempt]
        CHECK (
            (
                [ProducingJobId] IS NULL
                AND [ProducingAttempt] IS NULL
            )
            OR (
                [ProducingJobId] IS NOT NULL
                AND [ProducingAttempt] IS NOT NULL
                AND [ProducingAttempt] > 0
            )
        )
);

GO

CREATE UNIQUE INDEX [UQ_DocumentPageMedia_PreferredOcrInput]
ON [ethnowear].[DocumentPageMedia] ([DocumentPageId])
WHERE [IsPreferredOcrInput] = 1;

GO

CREATE INDEX [IX_DocumentPageMedia_DocumentPageId]
ON [ethnowear].[DocumentPageMedia] ([DocumentPageId]);

GO

CREATE INDEX [IX_DocumentPageMedia_MediaAssetId]
ON [ethnowear].[DocumentPageMedia] ([MediaAssetId]);

GO

CREATE INDEX [IX_DocumentPageMedia_Page_RenditionType]
ON [ethnowear].[DocumentPageMedia] ([DocumentPageId], [RenditionType]);

GO

CREATE INDEX [IX_DocumentPageMedia_ProducingJobId]
ON [ethnowear].[DocumentPageMedia] ([ProducingJobId])
WHERE [ProducingJobId] IS NOT NULL;

GO

CREATE UNIQUE INDEX [UQ_DocumentPageMedia_LogicalRendition]
ON [ethnowear].[DocumentPageMedia]
(
    [DocumentPageId],
    [RenditionType],
    [ProducingJobId],
    [ProducingAttempt]
)
WHERE [ProducingJobId] IS NOT NULL
  AND [ProducingAttempt] IS NOT NULL;

GO

CREATE INDEX [IX_DocumentPageMedia_RenditionHash]
ON [ethnowear].[DocumentPageMedia] ([RenditionHash])
WHERE [RenditionHash] IS NOT NULL;
