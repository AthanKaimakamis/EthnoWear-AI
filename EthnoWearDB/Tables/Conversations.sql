CREATE TABLE [ethnowear].[Conversations]
(
    [Id] BIGINT IDENTITY(1,1) NOT NULL,
    [PublicId] UNIQUEIDENTIFIER NOT NULL,
    [PublicUserId] BIGINT NULL,
    [GuestSessionId] BIGINT NULL,
    [ClientRequestId] UNIQUEIDENTIFIER NOT NULL,
    [Language] NVARCHAR(2) NOT NULL,
    [Title] NVARCHAR(300) NULL,
    [CreatedAt] DATETIME2(7) NOT NULL CONSTRAINT [DF_Conversations_CreatedAt] DEFAULT SYSUTCDATETIME(),
    [UpdatedAt] DATETIME2(7) NOT NULL CONSTRAINT [DF_Conversations_UpdatedAt] DEFAULT SYSUTCDATETIME(),
    [RowVersion] ROWVERSION NOT NULL,

    CONSTRAINT [PK_Conversations] PRIMARY KEY CLUSTERED ([Id]),
    CONSTRAINT [FK_Conversations_PublicUsers] FOREIGN KEY ([PublicUserId]) REFERENCES [ethnowear].[PublicUsers] ([Id]),
    CONSTRAINT [FK_Conversations_GuestSessions] FOREIGN KEY ([GuestSessionId]) REFERENCES [ethnowear].[ConversationGuestSessions] ([Id]),
    CONSTRAINT [UQ_Conversations_PublicId] UNIQUE ([PublicId]),
    CONSTRAINT [CK_Conversations_Owner]
        CHECK (([PublicUserId] IS NOT NULL AND [GuestSessionId] IS NULL)
            OR ([PublicUserId] IS NULL AND [GuestSessionId] IS NOT NULL)),
    CONSTRAINT [CK_Conversations_Language]
        CHECK ([Language] COLLATE Latin1_General_100_BIN2 IN (N'bg', N'en')),
    CONSTRAINT [CK_Conversations_Title] CHECK ([Title] IS NULL OR LEN(LTRIM(RTRIM([Title]))) > 0)
);
GO
CREATE UNIQUE INDEX [UQ_Conversations_User_Request]
ON [ethnowear].[Conversations] ([PublicUserId], [ClientRequestId]) WHERE [PublicUserId] IS NOT NULL;
GO
CREATE UNIQUE INDEX [UQ_Conversations_Guest_Request]
ON [ethnowear].[Conversations] ([GuestSessionId], [ClientRequestId]) WHERE [GuestSessionId] IS NOT NULL;
GO
CREATE INDEX [IX_Conversations_User_UpdatedAt]
ON [ethnowear].[Conversations] ([PublicUserId], [UpdatedAt] DESC, [Id] DESC) WHERE [PublicUserId] IS NOT NULL;
GO
CREATE INDEX [IX_Conversations_Guest_UpdatedAt]
ON [ethnowear].[Conversations] ([GuestSessionId], [UpdatedAt] DESC, [Id] DESC) WHERE [GuestSessionId] IS NOT NULL;
