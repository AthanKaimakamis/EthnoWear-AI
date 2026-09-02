CREATE TABLE [ethnowear].[PublicUserSessions]
(
    [Id] BIGINT IDENTITY(1,1) NOT NULL,
    [PublicUserId] BIGINT NOT NULL,
    [TokenHash] NVARCHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
    [ExpiresAt] DATETIME2(7) NOT NULL,
    [RevokedAt] DATETIME2(7) NULL,
    [CreatedAt] DATETIME2(7) NOT NULL CONSTRAINT [DF_PublicUserSessions_CreatedAt] DEFAULT SYSUTCDATETIME(),
    CONSTRAINT [PK_PublicUserSessions] PRIMARY KEY CLUSTERED ([Id]),
    CONSTRAINT [FK_PublicUserSessions_PublicUsers] FOREIGN KEY ([PublicUserId]) REFERENCES [ethnowear].[PublicUsers] ([Id]),
    CONSTRAINT [UQ_PublicUserSessions_TokenHash] UNIQUE ([TokenHash]),
    CONSTRAINT [CK_PublicUserSessions_TokenHash] CHECK (DATALENGTH([TokenHash]) = 128 AND [TokenHash] NOT LIKE N'%[^0-9a-f]%'),
    CONSTRAINT [CK_PublicUserSessions_Expiry] CHECK ([ExpiresAt] > [CreatedAt]),
    CONSTRAINT [CK_PublicUserSessions_RevokedAt] CHECK ([RevokedAt] IS NULL OR [RevokedAt] >= [CreatedAt])
);
GO
CREATE INDEX [IX_PublicUserSessions_User_Expiry] ON [ethnowear].[PublicUserSessions] ([PublicUserId], [ExpiresAt]);
