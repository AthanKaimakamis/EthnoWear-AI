CREATE TABLE [ethnowear].[ConversationTurns]
(
    [Id] BIGINT IDENTITY(1,1) NOT NULL,
    [PublicId] UNIQUEIDENTIFIER NOT NULL,
    [ConversationId] BIGINT NOT NULL,
    [ClientRequestId] UNIQUEIDENTIFIER NOT NULL,
    [TurnSequence] BIGINT NOT NULL,
    [UserMessage] NVARCHAR(1000) NOT NULL,
    -- SHA-256 over exact UTF-8 UserMessage bytes, lowercase hexadecimal.
    [RequestHash] NVARCHAR(64) NOT NULL,
    [Status] NVARCHAR(20) NOT NULL CONSTRAINT [DF_ConversationTurns_Status] DEFAULT N'QUEUED',
    [Stage] NVARCHAR(50) NULL,
    -- Explicit filtered-index key; the check prevents drift from Status.
    [IsActive] BIT NOT NULL CONSTRAINT [DF_ConversationTurns_IsActive] DEFAULT 1,
    -- Validated public answer snapshot only; never raw model output.
    [AnswerJson] NVARCHAR(MAX) NULL,
    [ErrorCode] NVARCHAR(100) NULL,
    [LastEventId] BIGINT NOT NULL CONSTRAINT [DF_ConversationTurns_LastEventId] DEFAULT 0,
    [StartedAt] DATETIME2(7) NULL,
    [FinishedAt] DATETIME2(7) NULL,
    [CreatedAt] DATETIME2(7) NOT NULL CONSTRAINT [DF_ConversationTurns_CreatedAt] DEFAULT SYSUTCDATETIME(),
    [UpdatedAt] DATETIME2(7) NOT NULL CONSTRAINT [DF_ConversationTurns_UpdatedAt] DEFAULT SYSUTCDATETIME(),
    [RowVersion] ROWVERSION NOT NULL,

    CONSTRAINT [PK_ConversationTurns] PRIMARY KEY CLUSTERED ([Id]),
    CONSTRAINT [FK_ConversationTurns_Conversations] FOREIGN KEY ([ConversationId]) REFERENCES [ethnowear].[Conversations] ([Id]),
    CONSTRAINT [UQ_ConversationTurns_PublicId] UNIQUE ([PublicId]),
    CONSTRAINT [UQ_ConversationTurns_Request] UNIQUE ([ConversationId], [ClientRequestId]),
    CONSTRAINT [UQ_ConversationTurns_Sequence] UNIQUE ([ConversationId], [TurnSequence]),
    CONSTRAINT [CK_ConversationTurns_Sequence] CHECK ([TurnSequence] > 0),
    CONSTRAINT [CK_ConversationTurns_Message] CHECK (LEN(LTRIM(RTRIM([UserMessage]))) > 0),
    CONSTRAINT [CK_ConversationTurns_RequestHash]
        CHECK (DATALENGTH([RequestHash]) = 128
            AND [RequestHash] COLLATE Latin1_General_100_BIN2 NOT LIKE N'%[^0-9a-f]%'),
    CONSTRAINT [CK_ConversationTurns_Status]
        CHECK ([Status] COLLATE Latin1_General_100_BIN2 IN (N'QUEUED', N'RUNNING', N'COMPLETED', N'FAILED', N'CANCELLED')),
    CONSTRAINT [CK_ConversationTurns_Active]
        CHECK (([Status] IN (N'QUEUED', N'RUNNING') AND [IsActive] = 1)
            OR ([Status] IN (N'COMPLETED', N'FAILED', N'CANCELLED') AND [IsActive] = 0)),
    CONSTRAINT [CK_ConversationTurns_Stage]
        CHECK (([IsActive] = 0 AND [Stage] IS NULL)
            OR ([IsActive] = 1 AND [Stage] IS NOT NULL
                AND [Stage] COLLATE Latin1_General_100_BIN2 IN (
                    N'RECEIVED', N'RESOLVING_ENTITIES', N'READING_ONTOLOGY', N'SEARCHING_ARCHIVE',
                    N'RETRIEVING_SOURCES', N'REASONING', N'INTERPRETING', N'GENERATING_ANSWER', N'VALIDATING_ANSWER'))),
    CONSTRAINT [CK_ConversationTurns_Answer]
        CHECK (([Status] = N'COMPLETED' AND [AnswerJson] IS NOT NULL
                AND ISJSON([AnswerJson], OBJECT) = 1 AND DATALENGTH([AnswerJson]) <= 131072)
            OR ([Status] <> N'COMPLETED' AND [AnswerJson] IS NULL)),
    CONSTRAINT [CK_ConversationTurns_ErrorCode]
        CHECK (([Status] = N'FAILED' AND [ErrorCode] IS NOT NULL
                AND DATALENGTH([ErrorCode]) > 0
                AND [ErrorCode] COLLATE Latin1_General_100_BIN2 NOT LIKE N'%[^A-Z0-9_]%')
            OR ([Status] <> N'FAILED' AND [ErrorCode] IS NULL)),
    CONSTRAINT [CK_ConversationTurns_LastEventId] CHECK ([LastEventId] >= 0),
    CONSTRAINT [CK_ConversationTurns_Timestamps]
        CHECK (([StartedAt] IS NULL OR [StartedAt] >= [CreatedAt])
            AND ([Status] <> N'QUEUED' OR [StartedAt] IS NULL)
            AND ([Status] NOT IN (N'RUNNING', N'COMPLETED') OR [StartedAt] IS NOT NULL)
            AND (([IsActive] = 1 AND [FinishedAt] IS NULL)
                OR ([IsActive] = 0 AND [FinishedAt] IS NOT NULL
                    AND [FinishedAt] >= [CreatedAt]
                    AND ([StartedAt] IS NULL OR [FinishedAt] >= [StartedAt]))))
);
GO
CREATE UNIQUE INDEX [UQ_ConversationTurns_ActiveConversation]
ON [ethnowear].[ConversationTurns] ([ConversationId]) WHERE [IsActive] = 1;
GO
CREATE INDEX [IX_ConversationTurns_Active_CreatedAt]
ON [ethnowear].[ConversationTurns] ([Status], [CreatedAt], [Id]) WHERE [IsActive] = 1;
