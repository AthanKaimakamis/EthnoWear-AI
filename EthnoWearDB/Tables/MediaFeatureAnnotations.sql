CREATE TABLE [ethnowear].[MediaFeatureAnnotations]
(
    [Id] BIGINT IDENTITY(1,1) NOT NULL,
    [ArchiveItemMediaId] BIGINT NOT NULL,
    [ArchiveItemFeatureId] BIGINT NOT NULL,

    [AnnotationType] NVARCHAR(50) NOT NULL,

    [X] DECIMAL(9,6) NULL,
    [Y] DECIMAL(9,6) NULL,
    [Width] DECIMAL(9,6) NULL,
    [Height] DECIMAL(9,6) NULL,

    [Note] NVARCHAR(MAX) NULL,

    [CreatedAt] DATETIME2(7) NOT NULL CONSTRAINT [DF_MediaFeatureAnnotations_CreatedAt] DEFAULT SYSUTCDATETIME(),
    [UpdatedAt] DATETIME2(7) NOT NULL CONSTRAINT [DF_MediaFeatureAnnotations_UpdatedAt] DEFAULT SYSUTCDATETIME(),

    CONSTRAINT [PK_MediaFeatureAnnotations] PRIMARY KEY CLUSTERED ([Id]),

    CONSTRAINT [FK_MediaFeatureAnnotations_ArchiveItemMedia]
        FOREIGN KEY ([ArchiveItemMediaId])
        REFERENCES [ethnowear].[ArchiveItemMedia] ([Id]),

    CONSTRAINT [FK_MediaFeatureAnnotations_ArchiveItemFeatures]
        FOREIGN KEY ([ArchiveItemFeatureId])
        REFERENCES [ethnowear].[ArchiveItemFeatures] ([Id]),

    CONSTRAINT [CK_MediaFeatureAnnotations_AnnotationType]
        CHECK ([AnnotationType] IN (
            N'VISIBLE_IN_IMAGE',
            N'PRIMARY_SUBJECT',
            N'DETAIL_VIEW',
            N'CROP_REGION'
        )),

    CONSTRAINT [CK_MediaFeatureAnnotations_Box]
        CHECK (
            ([X] IS NULL AND [Y] IS NULL AND [Width] IS NULL AND [Height] IS NULL)
            OR
            (
                [X] >= 0 AND [X] <= 1
                AND [Y] >= 0 AND [Y] <= 1
                AND [Width] > 0 AND [Width] <= 1
                AND [Height] > 0 AND [Height] <= 1
            )
        )
);

GO

CREATE INDEX [IX_MediaFeatureAnnotations_ArchiveItemMediaId]
ON [ethnowear].[MediaFeatureAnnotations] ([ArchiveItemMediaId]);

GO

CREATE INDEX [IX_MediaFeatureAnnotations_ArchiveItemFeatureId]
ON [ethnowear].[MediaFeatureAnnotations] ([ArchiveItemFeatureId]);
