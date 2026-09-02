IF COL_LENGTH(N'ethnowear.Users', N'DeletedAt') IS NULL
BEGIN
    ALTER TABLE [ethnowear].[Users]
        ADD [DeletedAt] DATETIME2(7) NULL;
END;

GO

IF COL_LENGTH(N'ethnowear.Users', N'DeletedByUserId') IS NULL
BEGIN
    ALTER TABLE [ethnowear].[Users]
        ADD [DeletedByUserId] BIGINT NULL;
END;

GO

IF NOT EXISTS (
    SELECT 1
    FROM sys.foreign_keys
    WHERE [name] = N'FK_Users_DeletedByUser'
)
BEGIN
    ALTER TABLE [ethnowear].[Users] WITH CHECK
        ADD CONSTRAINT [FK_Users_DeletedByUser]
        FOREIGN KEY ([DeletedByUserId])
        REFERENCES [ethnowear].[Users] ([Id]);
END;

GO

IF NOT EXISTS (
    SELECT 1
    FROM sys.check_constraints
    WHERE [name] = N'CK_Users_Deletion'
)
BEGIN
    ALTER TABLE [ethnowear].[Users] WITH CHECK
        ADD CONSTRAINT [CK_Users_Deletion]
        CHECK (
            ([DeletedAt] IS NULL AND [DeletedByUserId] IS NULL)
            OR ([DeletedAt] IS NOT NULL AND [DeletedByUserId] IS NOT NULL AND [Enabled] = 0)
        );
END;

GO

IF NOT EXISTS (
    SELECT 1
    FROM sys.indexes
    WHERE [object_id] = OBJECT_ID(N'ethnowear.Users')
      AND [name] = N'IX_Users_DeletedByUserId'
)
BEGIN
    CREATE INDEX [IX_Users_DeletedByUserId]
        ON [ethnowear].[Users] ([DeletedByUserId])
        WHERE [DeletedByUserId] IS NOT NULL;
END;

GO
