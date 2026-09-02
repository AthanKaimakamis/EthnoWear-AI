CREATE TABLE [ethnowear].[PublicUsers]
(
    [Id] BIGINT IDENTITY(1,1) NOT NULL,
    [PublicId] UNIQUEIDENTIFIER NOT NULL,
    [DisplayName] NVARCHAR(200) NOT NULL,
    [Email] NVARCHAR(320) NOT NULL,
    [Enabled] BIT NOT NULL CONSTRAINT [DF_PublicUsers_Enabled] DEFAULT 1,
    [LastLoginAt] DATETIME2(7) NOT NULL,
    [CreatedAt] DATETIME2(7) NOT NULL CONSTRAINT [DF_PublicUsers_CreatedAt] DEFAULT SYSUTCDATETIME(),
    [UpdatedAt] DATETIME2(7) NOT NULL CONSTRAINT [DF_PublicUsers_UpdatedAt] DEFAULT SYSUTCDATETIME(),
    [RowVersion] ROWVERSION NOT NULL,
    CONSTRAINT [PK_PublicUsers] PRIMARY KEY CLUSTERED ([Id]),
    CONSTRAINT [UQ_PublicUsers_PublicId] UNIQUE ([PublicId]),
    CONSTRAINT [CK_PublicUsers_DisplayName] CHECK (LEN(LTRIM(RTRIM([DisplayName]))) > 0),
    CONSTRAINT [CK_PublicUsers_Email] CHECK (LEN(LTRIM(RTRIM([Email]))) > 0)
);
