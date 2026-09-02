CREATE TABLE [ethnowear].[Users]
(
    [Id] BIGINT IDENTITY(1,1) NOT NULL,
    [Username] NVARCHAR(100) NOT NULL,
    [NormalizedUsername] AS UPPER(LTRIM(RTRIM([Username]))) PERSISTED,
    [PasswordHash] NVARCHAR(255) NULL,
    [MustChangePassword] BIT NOT NULL CONSTRAINT [DF_Users_MustChangePassword] DEFAULT (1),
    [TemporaryPasswordExpiresAt] DATETIME2(7) NULL,
    [Enabled] BIT NOT NULL CONSTRAINT [DF_Users_Enabled] DEFAULT (1),
    [FailedLoginAttempts] INT NOT NULL CONSTRAINT [DF_Users_FailedLoginAttempts] DEFAULT (0),
    [LockedUntil] DATETIME2(7) NULL,
    [TokenVersion] INT NOT NULL CONSTRAINT [DF_Users_TokenVersion] DEFAULT (0),
    [LastLoginAt] DATETIME2(7) NULL,
    [CreatedByUserId] BIGINT NULL,
    [DeletedAt] DATETIME2(7) NULL,
    [DeletedByUserId] BIGINT NULL,
    [CreatedAt] DATETIME2(7) NOT NULL CONSTRAINT [DF_Users_CreatedAt] DEFAULT SYSUTCDATETIME(),
    [UpdatedAt] DATETIME2(7) NOT NULL CONSTRAINT [DF_Users_UpdatedAt] DEFAULT SYSUTCDATETIME(),
    [RowVersion] ROWVERSION NOT NULL,

    CONSTRAINT [PK_Users] PRIMARY KEY CLUSTERED ([Id]),

    CONSTRAINT [FK_Users_CreatedByUser]
        FOREIGN KEY ([CreatedByUserId])
        REFERENCES [ethnowear].[Users] ([Id]),

    CONSTRAINT [FK_Users_DeletedByUser]
        FOREIGN KEY ([DeletedByUserId])
        REFERENCES [ethnowear].[Users] ([Id]),

    CONSTRAINT [CK_Users_Username]
        CHECK (
            LEN(LTRIM(RTRIM([Username]))) BETWEEN 3 AND 100
            AND [Username] = LTRIM(RTRIM([Username]))
        ),

    CONSTRAINT [CK_Users_PasswordHash]
        CHECK (
            [PasswordHash] IS NULL
            OR LEN(LTRIM(RTRIM([PasswordHash]))) > 0
        ),

    CONSTRAINT [CK_Users_EnabledPassword]
        CHECK ([Enabled] = 0 OR [PasswordHash] IS NOT NULL),

    CONSTRAINT [CK_Users_FailedLoginAttempts]
        CHECK ([FailedLoginAttempts] >= 0),

    CONSTRAINT [CK_Users_TokenVersion]
        CHECK ([TokenVersion] >= 0),

    CONSTRAINT [CK_Users_TemporaryPassword]
        CHECK (
            (
                [PasswordHash] IS NULL
                AND [Enabled] = 0
                AND [MustChangePassword] = 1
                AND [TemporaryPasswordExpiresAt] IS NULL
            )
            OR (
                [PasswordHash] IS NOT NULL
                AND [MustChangePassword] = 1
                AND [TemporaryPasswordExpiresAt] IS NOT NULL
            )
            OR (
                [PasswordHash] IS NOT NULL
                AND [MustChangePassword] = 0
                AND [TemporaryPasswordExpiresAt] IS NULL
            )
        ),

    CONSTRAINT [CK_Users_Deletion]
        CHECK (
            ([DeletedAt] IS NULL AND [DeletedByUserId] IS NULL)
            OR ([DeletedAt] IS NOT NULL AND [DeletedByUserId] IS NOT NULL AND [Enabled] = 0)
        )
);

GO

CREATE UNIQUE INDEX [UQ_Users_NormalizedUsername]
ON [ethnowear].[Users] ([NormalizedUsername]);

GO

CREATE INDEX [IX_Users_CreatedByUserId]
ON [ethnowear].[Users] ([CreatedByUserId])
WHERE [CreatedByUserId] IS NOT NULL;

GO

CREATE INDEX [IX_Users_DeletedByUserId]
ON [ethnowear].[Users] ([DeletedByUserId])
WHERE [DeletedByUserId] IS NOT NULL;

GO

CREATE INDEX [IX_Users_Enabled_LockedUntil]
ON [ethnowear].[Users] ([Enabled], [LockedUntil]);
