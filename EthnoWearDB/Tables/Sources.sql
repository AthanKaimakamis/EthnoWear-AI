CREATE TABLE [ethnowear].[Sources]
(
    [Id] BIGINT IDENTITY(1,1) NOT NULL,
    [Title] NVARCHAR(300) NOT NULL,
    [Author] NVARCHAR(200) NULL,
    [Publisher] NVARCHAR(200) NULL,
    [PublicationYear] INT NULL,
    [SourceType] NVARCHAR(50) NOT NULL,
    [Language] NVARCHAR(10) NULL,
    [FilePath] NVARCHAR(1000) NULL,
    [Url] NVARCHAR(1000) NULL,
    [Isbn] NVARCHAR(40) NULL,
    [Notes] NVARCHAR(MAX) NULL,
    [IsTrusted] BIT NOT NULL CONSTRAINT [DF_Sources_IsTrusted] DEFAULT (0),
    [RightsStatus] NVARCHAR(30) NOT NULL CONSTRAINT [DF_Sources_RightsStatus] DEFAULT N'UNKNOWN',
    [License] NVARCHAR(500) NULL,
    [PublicDisplayAllowed] BIT NOT NULL CONSTRAINT [DF_Sources_PublicDisplayAllowed] DEFAULT (0),
    [CreatedAt] DATETIME2(7) NOT NULL CONSTRAINT [DF_Sources_CreatedAt] DEFAULT SYSUTCDATETIME(),
    [UpdatedAt] DATETIME2(7) NOT NULL CONSTRAINT [DF_Sources_UpdatedAt] DEFAULT SYSUTCDATETIME(),

    CONSTRAINT [PK_Sources] PRIMARY KEY CLUSTERED ([Id]),

    CONSTRAINT [CK_Sources_SourceType]
        CHECK ([SourceType] IN (
            N'BOOK',
            N'SCANNED_BOOK',
            N'WEBSITE',
            N'MUSEUM_CATALOG',
            N'ARTICLE',
            N'FIELD_NOTE'
        )),

    CONSTRAINT [CK_Sources_RightsStatus]
        CHECK ([RightsStatus] IN (N'UNKNOWN', N'PUBLIC_DOMAIN', N'LICENSED', N'RESTRICTED')),

    CONSTRAINT [CK_Sources_License]
        CHECK ([RightsStatus] <> N'LICENSED'
            OR ([License] IS NOT NULL AND LEN(LTRIM(RTRIM([License]))) > 0)),

    CONSTRAINT [CK_Sources_PublicDisplay]
        CHECK ([PublicDisplayAllowed] = 0
            OR [RightsStatus] IN (N'PUBLIC_DOMAIN', N'LICENSED'))
);
