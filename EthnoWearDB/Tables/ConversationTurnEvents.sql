CREATE TABLE [ethnowear].[ConversationTurnEvents]
(
    [Id] BIGINT IDENTITY(1,1) NOT NULL,
    [ConversationTurnId] BIGINT NOT NULL,
    -- Public SSE cursor, allocated per turn in the turn-update transaction.
    [EventId] BIGINT NOT NULL,
    [Status] NVARCHAR(20) NOT NULL,
    [Stage] NVARCHAR(50) NULL,
    [ErrorCode] NVARCHAR(100) NULL,
    [CreatedAt] DATETIME2(7) NOT NULL CONSTRAINT [DF_ConversationTurnEvents_CreatedAt] DEFAULT SYSUTCDATETIME(),

    CONSTRAINT [PK_ConversationTurnEvents] PRIMARY KEY CLUSTERED ([Id]),
    CONSTRAINT [FK_ConversationTurnEvents_Turns] FOREIGN KEY ([ConversationTurnId]) REFERENCES [ethnowear].[ConversationTurns] ([Id]),
    CONSTRAINT [UQ_ConversationTurnEvents_Cursor] UNIQUE ([ConversationTurnId], [EventId]),
    CONSTRAINT [CK_ConversationTurnEvents_Cursor] CHECK ([EventId] > 0),
    CONSTRAINT [CK_ConversationTurnEvents_Status]
        CHECK ([Status] COLLATE Latin1_General_100_BIN2 IN (N'QUEUED', N'RUNNING', N'COMPLETED', N'FAILED', N'CANCELLED')),
    CONSTRAINT [CK_ConversationTurnEvents_Stage]
        CHECK (([Status] IN (N'COMPLETED', N'FAILED', N'CANCELLED') AND [Stage] IS NULL)
            OR ([Status] IN (N'QUEUED', N'RUNNING') AND [Stage] IS NOT NULL
                AND [Stage] COLLATE Latin1_General_100_BIN2 IN (
                    N'RECEIVED', N'RESOLVING_ENTITIES', N'READING_ONTOLOGY', N'SEARCHING_ARCHIVE',
                    N'RETRIEVING_SOURCES', N'REASONING', N'INTERPRETING', N'GENERATING_ANSWER', N'VALIDATING_ANSWER'))),
    CONSTRAINT [CK_ConversationTurnEvents_ErrorCode]
        CHECK (([Status] = N'FAILED' AND [ErrorCode] IS NOT NULL
                AND DATALENGTH([ErrorCode]) > 0
                AND [ErrorCode] COLLATE Latin1_General_100_BIN2 NOT LIKE N'%[^A-Z0-9_]%')
            OR ([Status] <> N'FAILED' AND [ErrorCode] IS NULL))
);
