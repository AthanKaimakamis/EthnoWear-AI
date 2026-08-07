CREATE TABLE [ethnowear].[SourceReference]
(
    [Id] BIGINT IDENTITY(1,1) NOT NULL,
    [SourceId] BIGINT NOT NULL,

    -- Book / scanned source location
    [Chapter] NVARCHAR(100) NULL,
    [PageFrom] INT NULL,
    [PageTo] INT NULL,
    [FigureNumber] NVARCHAR(100) NULL,
    [SectionTitle] NVARCHAR(300) NULL,

    -- Museum / catalog / web reference location
    [CatalogNumber] NVARCHAR(100) NULL,
    [ReferenceUrl] NVARCHAR(1000) NULL,
    [AccessedDate] DATE NULL,

    -- Flexible locator for paragraph, scan region, timestamp, URL anchor, etc.
    [Locator] NVARCHAR(500) NULL,

    [Note] NVARCHAR(MAX) NULL,

    [CreatedAt] DATETIME2(7) NOT NULL
        CONSTRAINT [DF_SourceReference_CreatedAt] DEFAULT SYSUTCDATETIME(),

    [UpdatedAt] DATETIME2(7) NOT NULL
        CONSTRAINT [DF_SourceReference_UpdatedAt] DEFAULT SYSUTCDATETIME(),

    CONSTRAINT [PK_SourceReference]
        PRIMARY KEY CLUSTERED ([Id]),

    CONSTRAINT [FK_SourceReference_Sources]
        FOREIGN KEY ([SourceId])
        REFERENCES [ethnowear].[Sources] ([Id]),

    CONSTRAINT [CK_SourceReference_PageRange]
        CHECK (
            [PageFrom] IS NULL
            OR [PageTo] IS NULL
            OR [PageTo] >= [PageFrom]
        )
);

GO

CREATE INDEX [IX_SourceReference_SourceId]
ON [ethnowear].[SourceReference] ([SourceId]);
