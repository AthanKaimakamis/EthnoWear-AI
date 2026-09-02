CREATE TABLE [ethnowear].[ConversationTurnEvidence]
(
    [Id] BIGINT IDENTITY(1,1) NOT NULL,
    [ConversationTurnId] BIGINT NOT NULL,
    [EvidenceKey] NVARCHAR(100) NOT NULL,
    [EvidenceType] NVARCHAR(20) NOT NULL,
    -- Private historical IDs/citations and bounded evidence, not live-source FKs.
    -- Source deletion must not silently destroy the conversation audit.
    [SnapshotJson] NVARCHAR(MAX) NOT NULL,
    [CreatedAt] DATETIME2(7) NOT NULL CONSTRAINT [DF_ConversationTurnEvidence_CreatedAt] DEFAULT SYSUTCDATETIME(),

    CONSTRAINT [PK_ConversationTurnEvidence] PRIMARY KEY CLUSTERED ([Id]),
    CONSTRAINT [FK_ConversationTurnEvidence_Turns] FOREIGN KEY ([ConversationTurnId]) REFERENCES [ethnowear].[ConversationTurns] ([Id]),
    CONSTRAINT [UQ_ConversationTurnEvidence_Key] UNIQUE ([ConversationTurnId], [EvidenceKey]),
    CONSTRAINT [CK_ConversationTurnEvidence_Key] CHECK (LEN(LTRIM(RTRIM([EvidenceKey]))) > 0),
    CONSTRAINT [CK_ConversationTurnEvidence_Type]
        CHECK ([EvidenceType] COLLATE Latin1_General_100_BIN2 IN (N'ONTOLOGY', N'DOCUMENT', N'ARCHIVE')),
    CONSTRAINT [CK_ConversationTurnEvidence_Snapshot]
        CHECK (ISJSON([SnapshotJson], OBJECT) = 1 AND DATALENGTH([SnapshotJson]) <= 32768)
);
