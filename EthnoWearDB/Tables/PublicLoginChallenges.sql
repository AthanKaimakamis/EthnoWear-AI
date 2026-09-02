CREATE TABLE [ethnowear].[PublicLoginChallenges]
(
    [Id] BIGINT IDENTITY(1,1) NOT NULL,
    [NonceHash] NVARCHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
    [ExpiresAt] DATETIME2(7) NOT NULL,
    [ConsumedAt] DATETIME2(7) NULL,
    [CreatedAt] DATETIME2(7) NOT NULL CONSTRAINT [DF_PublicLoginChallenges_CreatedAt] DEFAULT SYSUTCDATETIME(),
    CONSTRAINT [PK_PublicLoginChallenges] PRIMARY KEY CLUSTERED ([Id]),
    CONSTRAINT [UQ_PublicLoginChallenges_NonceHash] UNIQUE ([NonceHash]),
    CONSTRAINT [CK_PublicLoginChallenges_NonceHash] CHECK (DATALENGTH([NonceHash]) = 128 AND [NonceHash] NOT LIKE N'%[^0-9a-f]%'),
    CONSTRAINT [CK_PublicLoginChallenges_Expiry] CHECK ([ExpiresAt] > [CreatedAt]),
    CONSTRAINT [CK_PublicLoginChallenges_ConsumedAt] CHECK ([ConsumedAt] IS NULL OR [ConsumedAt] >= [CreatedAt])
);
GO
CREATE INDEX [IX_PublicLoginChallenges_Expiry] ON [ethnowear].[PublicLoginChallenges] ([ExpiresAt]);
