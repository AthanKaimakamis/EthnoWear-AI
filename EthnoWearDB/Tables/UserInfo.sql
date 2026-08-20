CREATE TABLE [ethnowear].[UserInfo]
(
    [UserId] BIGINT NOT NULL,
    [FirstName] NVARCHAR(100) NOT NULL,
    [LastName] NVARCHAR(100) NOT NULL,
    [Email] NVARCHAR(320) NULL,
    [NormalizedEmail] AS UPPER(LTRIM(RTRIM([Email]))) PERSISTED,
    [Phone] NVARCHAR(50) NULL,
    [AddressLine1] NVARCHAR(250) NULL,
    [AddressLine2] NVARCHAR(250) NULL,
    [City] NVARCHAR(100) NULL,
    [PostalCode] NVARCHAR(20) NULL,
    [CountryCode] CHAR(2) NULL,
    [CreatedAt] DATETIME2(7) NOT NULL CONSTRAINT [DF_UserInfo_CreatedAt] DEFAULT SYSUTCDATETIME(),
    [UpdatedAt] DATETIME2(7) NOT NULL CONSTRAINT [DF_UserInfo_UpdatedAt] DEFAULT SYSUTCDATETIME(),
    [RowVersion] ROWVERSION NOT NULL,

    CONSTRAINT [PK_UserInfo] PRIMARY KEY CLUSTERED ([UserId]),

    CONSTRAINT [FK_UserInfo_Users]
        FOREIGN KEY ([UserId])
        REFERENCES [ethnowear].[Users] ([Id])
        ON DELETE CASCADE,

    CONSTRAINT [CK_UserInfo_FirstName]
        CHECK (LEN(LTRIM(RTRIM([FirstName]))) > 0),

    CONSTRAINT [CK_UserInfo_LastName]
        CHECK (LEN(LTRIM(RTRIM([LastName]))) > 0),

    CONSTRAINT [CK_UserInfo_Email]
        CHECK (
            [Email] IS NULL
            OR (
                LEN(LTRIM(RTRIM([Email]))) > 0
                AND [Email] = LTRIM(RTRIM([Email]))
            )
        ),

    CONSTRAINT [CK_UserInfo_CountryCode]
        CHECK (
            [CountryCode] IS NULL
            OR [CountryCode] LIKE '[A-Z][A-Z]'
        )
);

GO

CREATE UNIQUE INDEX [UQ_UserInfo_Email]
ON [ethnowear].[UserInfo] ([Email])
WHERE [Email] IS NOT NULL;
