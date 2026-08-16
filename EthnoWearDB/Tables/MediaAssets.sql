CREATE TABLE [ethnowear].[MediaAssets]
(
    [Id] BIGINT IDENTITY(1,1) NOT NULL,
    [SourceReferenceId] BIGINT NULL,

    [FileName] NVARCHAR(255) NULL,
    [FilePath] NVARCHAR(1000) NULL,
    [StorageUrl] NVARCHAR(1000) NULL,
    [MimeType] NVARCHAR(100) NULL,
    [MediaType] NVARCHAR(50) NOT NULL,

    [Width] INT NULL,
    [Height] INT NULL,
    [SizeBytes] BIGINT NULL,
    [Checksum] NVARCHAR(128) NULL,
    [ThumbnailPath] NVARCHAR(1000) NULL,
    [Description] NVARCHAR(2000) NULL,

    [CreatedAt] DATETIME2(7) NOT NULL CONSTRAINT [DF_MediaAssets_CreatedAt] DEFAULT SYSUTCDATETIME(),
    [UpdatedAt] DATETIME2(7) NOT NULL CONSTRAINT [DF_MediaAssets_UpdatedAt] DEFAULT SYSUTCDATETIME(),

    CONSTRAINT [PK_MediaAssets] PRIMARY KEY CLUSTERED ([Id]),

    CONSTRAINT [FK_MediaAssets_SourceReference]
        FOREIGN KEY ([SourceReferenceId])
        REFERENCES [ethnowear].[SourceReference] ([Id]),

    CONSTRAINT [CK_MediaAssets_MediaType]
        CHECK ([MediaType] IN (
            N'IMAGE',
            N'PDF',
            N'SCAN',
            N'THUMBNAIL',
            N'OTHER'
        )),

    CONSTRAINT [CK_MediaAssets_Dimensions]
        CHECK (
            ([Width] IS NULL OR [Width] > 0)
            AND
            ([Height] IS NULL OR [Height] > 0)
        ),

    CONSTRAINT [CK_MediaAssets_SizeBytes]
        CHECK ([SizeBytes] IS NULL OR [SizeBytes] >= 0)
    ,
    CONSTRAINT [CK_MediaAssets_FilePathRelative]
        CHECK ([FilePath] IS NULL OR (
            [FilePath] NOT LIKE N'/%' AND
            [FilePath] NOT LIKE N'%:\\%' AND
            [FilePath] NOT LIKE N'%..%'
        ))
);

GO

CREATE INDEX [IX_MediaAssets_SourceReferenceId]
ON [ethnowear].[MediaAssets] ([SourceReferenceId]);

GO

CREATE INDEX [IX_MediaAssets_Checksum]
ON [ethnowear].[MediaAssets] ([Checksum])
WHERE [Checksum] IS NOT NULL;
