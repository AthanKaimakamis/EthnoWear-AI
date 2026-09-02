CREATE TABLE [ethnowear].[OntologyVersions]
(
    [Id] BIGINT IDENTITY(1,1) NOT NULL,
    [VersionNumber] BIGINT NOT NULL,
    [PreviousVersionId] BIGINT NULL,
    [RestoredFromVersionId] BIGINT NULL,
    [CreatedByUserId] BIGINT NULL,
    [ChangeReason] NVARCHAR(500) NOT NULL,
    [OntologyContent] NVARCHAR(MAX) NOT NULL,
    [ContentHash] CHAR(64) NOT NULL,
    [FileName] NVARCHAR(260) NOT NULL,
    [OntologyNamespace] NVARCHAR(500) NOT NULL,
    [IsValid] BIT NOT NULL,
    [ValidationMessage] NVARCHAR(2000) NULL,
    [Status] NVARCHAR(20) NOT NULL,
    [CreatedAt] DATETIME2(7) NOT NULL
        CONSTRAINT [DF_OntologyVersions_CreatedAt] DEFAULT SYSUTCDATETIME(),

    CONSTRAINT [PK_OntologyVersions]
        PRIMARY KEY CLUSTERED ([Id]),

    CONSTRAINT [FK_OntologyVersions_PreviousVersion]
        FOREIGN KEY ([PreviousVersionId])
        REFERENCES [ethnowear].[OntologyVersions] ([Id]),

    CONSTRAINT [FK_OntologyVersions_RestoredFromVersion]
        FOREIGN KEY ([RestoredFromVersionId])
        REFERENCES [ethnowear].[OntologyVersions] ([Id]),

    CONSTRAINT [FK_OntologyVersions_CreatedByUser]
        FOREIGN KEY ([CreatedByUserId])
        REFERENCES [ethnowear].[Users] ([Id]),

    CONSTRAINT [CK_OntologyVersions_VersionNumber]
        CHECK ([VersionNumber] > 0),

    CONSTRAINT [CK_OntologyVersions_ChangeReason]
        CHECK (
            LEN(LTRIM(RTRIM([ChangeReason]))) BETWEEN 1 AND 500
            AND [ChangeReason] = LTRIM(RTRIM([ChangeReason]))
        ),

    CONSTRAINT [CK_OntologyVersions_Content]
        CHECK (DATALENGTH([OntologyContent]) > 0),

    CONSTRAINT [CK_OntologyVersions_ContentHash]
        CHECK (
            [ContentHash] NOT LIKE '%[^0-9a-f]%'
            AND LEN([ContentHash]) = 64
        ),

    CONSTRAINT [CK_OntologyVersions_FileName]
        CHECK (
            LEN(LTRIM(RTRIM([FileName]))) BETWEEN 1 AND 260
            AND [FileName] = LTRIM(RTRIM([FileName]))
        ),

    CONSTRAINT [CK_OntologyVersions_Namespace]
        CHECK (
            LEN(LTRIM(RTRIM([OntologyNamespace]))) BETWEEN 1 AND 500
            AND [OntologyNamespace] = LTRIM(RTRIM([OntologyNamespace]))
        ),

    CONSTRAINT [CK_OntologyVersions_Status]
        CHECK ([Status] IN ('STAGED', 'ACTIVE', 'SUPERSEDED', 'FAILED')),

    CONSTRAINT [CK_OntologyVersions_ActiveValidity]
        CHECK ([Status] <> 'ACTIVE' OR [IsValid] = 1),

    CONSTRAINT [CK_OntologyVersions_ValidationMessage]
        CHECK (
            [ValidationMessage] IS NULL
            OR LEN(LTRIM(RTRIM([ValidationMessage]))) BETWEEN 1 AND 2000
        ),

    CONSTRAINT [CK_OntologyVersions_PreviousNotSelf]
        CHECK ([PreviousVersionId] IS NULL OR [PreviousVersionId] <> [Id]),

    CONSTRAINT [CK_OntologyVersions_RestoredFromNotSelf]
        CHECK ([RestoredFromVersionId] IS NULL OR [RestoredFromVersionId] <> [Id])
);

GO

CREATE UNIQUE INDEX [UQ_OntologyVersions_VersionNumber]
ON [ethnowear].[OntologyVersions] ([VersionNumber]);

GO

CREATE UNIQUE INDEX [UQ_OntologyVersions_Active]
ON [ethnowear].[OntologyVersions] ([Status])
WHERE [Status] = 'ACTIVE';

GO

CREATE INDEX [IX_OntologyVersions_CreatedAt]
ON [ethnowear].[OntologyVersions] ([CreatedAt] DESC, [Id] DESC);

GO

CREATE INDEX [IX_OntologyVersions_ContentHash]
ON [ethnowear].[OntologyVersions] ([ContentHash]);

GO

CREATE INDEX [IX_OntologyVersions_PreviousVersionId]
ON [ethnowear].[OntologyVersions] ([PreviousVersionId])
WHERE [PreviousVersionId] IS NOT NULL;

GO

CREATE INDEX [IX_OntologyVersions_RestoredFromVersionId]
ON [ethnowear].[OntologyVersions] ([RestoredFromVersionId])
WHERE [RestoredFromVersionId] IS NOT NULL;

GO

CREATE INDEX [IX_OntologyVersions_CreatedByUserId]
ON [ethnowear].[OntologyVersions] ([CreatedByUserId])
WHERE [CreatedByUserId] IS NOT NULL;
