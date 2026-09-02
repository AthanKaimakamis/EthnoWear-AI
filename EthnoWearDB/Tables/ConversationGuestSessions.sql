CREATE TABLE [ethnowear].[ConversationGuestSessions]
(
    [Id] BIGINT IDENTITY(1,1) NOT NULL,
    [TokenHash] NVARCHAR(64) NOT NULL,
    [ExpiresAt] DATETIME2(7) NOT NULL,
    [RevokedAt] DATETIME2(7) NULL,
    [CreatedAt] DATETIME2(7) NOT NULL CONSTRAINT [DF_ConversationGuestSessions_CreatedAt] DEFAULT SYSUTCDATETIME(),
    [UpdatedAt] DATETIME2(7) NOT NULL CONSTRAINT [DF_ConversationGuestSessions_UpdatedAt] DEFAULT SYSUTCDATETIME(),
    [RowVersion] ROWVERSION NOT NULL,

    CONSTRAINT [PK_ConversationGuestSessions] PRIMARY KEY CLUSTERED ([Id]),
    CONSTRAINT [CK_ConversationGuestSessions_TokenHash]
        CHECK (DATALENGTH([TokenHash]) = 128
            AND [TokenHash] COLLATE Latin1_General_100_BIN2 NOT LIKE N'%[^0-9a-f]%'),
    CONSTRAINT [CK_ConversationGuestSessions_Expiry] CHECK ([ExpiresAt] > [CreatedAt]),
    CONSTRAINT [CK_ConversationGuestSessions_Revocation]
        CHECK ([RevokedAt] IS NULL OR [RevokedAt] >= [CreatedAt])
);
GO
CREATE UNIQUE INDEX [UQ_ConversationGuestSessions_TokenHash]
ON [ethnowear].[ConversationGuestSessions] ([TokenHash]);
GO
CREATE INDEX [IX_ConversationGuestSessions_ExpiresAt]
ON [ethnowear].[ConversationGuestSessions] ([ExpiresAt]);
