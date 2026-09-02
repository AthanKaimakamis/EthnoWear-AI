CREATE TABLE [ethnowear].[MediaAssets]
(
    [Id] BIGINT IDENTITY(1,1) NOT NULL,
    [SourceReferenceId] BIGINT NULL,

    [Origin] NVARCHAR(50) NOT NULL CONSTRAINT [DF_MediaAssets_Origin] DEFAULT N'USER_UPLOAD',
    [RetentionPolicy] NVARCHAR(50) NOT NULL CONSTRAINT [DF_MediaAssets_RetentionPolicy] DEFAULT N'KEEP_PERMANENTLY',
    [StorageState] NVARCHAR(50) NOT NULL CONSTRAINT [DF_MediaAssets_StorageState] DEFAULT N'AVAILABLE',
    [RetentionUntil] DATETIME2(7) NULL,
    [PurgedAt] DATETIME2(7) NULL,
    [PurgeReason] NVARCHAR(500) NULL,

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
    [RightsStatus] NVARCHAR(30) NOT NULL CONSTRAINT [DF_MediaAssets_RightsStatus] DEFAULT N'UNKNOWN',
    [License] NVARCHAR(500) NULL,
    [PublicDisplayAllowed] BIT NOT NULL CONSTRAINT [DF_MediaAssets_PublicDisplayAllowed] DEFAULT (0),

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

    CONSTRAINT [CK_MediaAssets_Origin]
        CHECK ([Origin] IN (
            N'USER_UPLOAD',
            N'DOCUMENT_ORIGINAL',
            N'MANUAL_REPLACEMENT',
            N'GENERATED'
        )),

    CONSTRAINT [CK_MediaAssets_RetentionPolicy]
        CHECK ([RetentionPolicy] IN (
            N'KEEP_PERMANENTLY',
            N'KEEP_ORIGINAL_ONLY'
        )),

    CONSTRAINT [CK_MediaAssets_StorageState]
        CHECK ([StorageState] IN (N'AVAILABLE', N'PURGED')),

    CONSTRAINT [CK_MediaAssets_PurgeState]
        CHECK (
            (
                [StorageState] = N'AVAILABLE'
                AND [PurgedAt] IS NULL
                AND [PurgeReason] IS NULL
            )
            OR (
                [StorageState] = N'PURGED'
                AND [Origin] = N'GENERATED'
                AND [RetentionPolicy] = N'KEEP_ORIGINAL_ONLY'
                AND [PurgedAt] IS NOT NULL
                AND [PurgeReason] IS NOT NULL
                AND LEN(LTRIM(RTRIM([PurgeReason]))) > 0
            )
        ),

    CONSTRAINT [CK_MediaAssets_Dimensions]
        CHECK (
            ([Width] IS NULL OR [Width] > 0)
            AND
            ([Height] IS NULL OR [Height] > 0)
        ),

    CONSTRAINT [CK_MediaAssets_SizeBytes]
        CHECK ([SizeBytes] IS NULL OR [SizeBytes] >= 0)
    ,
    CONSTRAINT [CK_MediaAssets_FilePathStorageKey]
        CHECK (
            [FilePath] IS NULL OR (
                LEN([FilePath]) > 0
                AND DATALENGTH([FilePath]) = DATALENGTH(LTRIM(RTRIM([FilePath])))
                AND LEFT([FilePath], 1) <> N'/'
                AND RIGHT([FilePath], 1) <> N'/'
                AND [FilePath] NOT LIKE N'%\%'
                AND [FilePath] NOT LIKE N'%:%'
                AND [FilePath] NOT LIKE N'%//%'
                AND [FilePath] <> N'.'
                AND [FilePath] <> N'..'
                AND [FilePath] NOT LIKE N'./%'
                AND [FilePath] NOT LIKE N'../%'
                AND [FilePath] NOT LIKE N'%/./%'
                AND [FilePath] NOT LIKE N'%/../%'
                AND [FilePath] NOT LIKE N'%/.'
                AND [FilePath] NOT LIKE N'%/..'
            )
        ),

    CONSTRAINT [CK_MediaAssets_ThumbnailPathStorageKey]
        CHECK (
            [ThumbnailPath] IS NULL OR (
                LEN([ThumbnailPath]) > 0
                AND DATALENGTH([ThumbnailPath]) = DATALENGTH(LTRIM(RTRIM([ThumbnailPath])))
                AND LEFT([ThumbnailPath], 1) <> N'/'
                AND RIGHT([ThumbnailPath], 1) <> N'/'
                AND [ThumbnailPath] NOT LIKE N'%\%'
                AND [ThumbnailPath] NOT LIKE N'%:%'
                AND [ThumbnailPath] NOT LIKE N'%//%'
                AND [ThumbnailPath] <> N'.'
                AND [ThumbnailPath] <> N'..'
                AND [ThumbnailPath] NOT LIKE N'./%'
                AND [ThumbnailPath] NOT LIKE N'../%'
                AND [ThumbnailPath] NOT LIKE N'%/./%'
                AND [ThumbnailPath] NOT LIKE N'%/../%'
                AND [ThumbnailPath] NOT LIKE N'%/.'
                AND [ThumbnailPath] NOT LIKE N'%/..'
            )
        ),

    CONSTRAINT [CK_MediaAssets_RightsStatus]
        CHECK ([RightsStatus] IN (N'UNKNOWN', N'PUBLIC_DOMAIN', N'LICENSED', N'RESTRICTED')),

    CONSTRAINT [CK_MediaAssets_License]
        CHECK ([RightsStatus] <> N'LICENSED'
            OR ([License] IS NOT NULL AND LEN(LTRIM(RTRIM([License]))) > 0)),

    CONSTRAINT [CK_MediaAssets_PublicDisplay]
        CHECK ([PublicDisplayAllowed] = 0
            OR [RightsStatus] IN (N'PUBLIC_DOMAIN', N'LICENSED'))
);

GO

CREATE INDEX [IX_MediaAssets_SourceReferenceId]
ON [ethnowear].[MediaAssets] ([SourceReferenceId]);

GO

CREATE INDEX [IX_MediaAssets_Checksum]
ON [ethnowear].[MediaAssets] ([Checksum])
WHERE [Checksum] IS NOT NULL;

GO

CREATE INDEX [IX_MediaAssets_Cleanup]
ON [ethnowear].[MediaAssets] ([StorageState], [Origin], [RetentionUntil])
WHERE [StorageState] = N'AVAILABLE'
  AND [Origin] = N'GENERATED';
