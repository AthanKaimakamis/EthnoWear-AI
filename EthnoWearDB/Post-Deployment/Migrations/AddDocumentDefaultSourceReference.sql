SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_PADDING ON;
SET ANSI_WARNINGS ON;
SET CONCAT_NULL_YIELDS_NULL ON;
SET ARITHABORT ON;
SET NUMERIC_ROUNDABORT OFF;

IF COL_LENGTH(N'ethnowear.Documents', N'DefaultSourceReferenceId') IS NULL
BEGIN
    ALTER TABLE [ethnowear].[Documents]
    ADD [DefaultSourceReferenceId] BIGINT NULL;
END;

IF NOT EXISTS (
    SELECT 1
    FROM sys.foreign_keys
    WHERE [name] = N'FK_Documents_DefaultSourceReference'
)
BEGIN
    ALTER TABLE [ethnowear].[Documents]
    ADD CONSTRAINT [FK_Documents_DefaultSourceReference]
        FOREIGN KEY ([DefaultSourceReferenceId])
        REFERENCES [ethnowear].[SourceReference] ([Id]);
END;

IF NOT EXISTS (
    SELECT 1
    FROM sys.indexes
    WHERE [name] = N'IX_Documents_DefaultSourceReferenceId'
      AND [object_id] = OBJECT_ID(N'ethnowear.Documents')
)
BEGIN
    CREATE INDEX [IX_Documents_DefaultSourceReferenceId]
    ON [ethnowear].[Documents] ([DefaultSourceReferenceId]);
END;
