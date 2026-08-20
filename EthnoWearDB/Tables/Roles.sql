CREATE TABLE [ethnowear].[Roles]
(
    [Id] BIGINT IDENTITY(1,1) NOT NULL,
    [Name] NVARCHAR(50) NOT NULL,
    [Description] NVARCHAR(500) NULL,
    [CreatedAt] DATETIME2(7) NOT NULL CONSTRAINT [DF_Roles_CreatedAt] DEFAULT SYSUTCDATETIME(),
    [UpdatedAt] DATETIME2(7) NOT NULL CONSTRAINT [DF_Roles_UpdatedAt] DEFAULT SYSUTCDATETIME(),
    [RowVersion] ROWVERSION NOT NULL,

    CONSTRAINT [PK_Roles] PRIMARY KEY CLUSTERED ([Id]),

    CONSTRAINT [CK_Roles_Name]
        CHECK ([Name] IN (
            N'ADMINISTRATOR',
            N'REVIEWER',
            N'EDITOR'
        ))
);

GO

CREATE UNIQUE INDEX [UQ_Roles_Name]
ON [ethnowear].[Roles] ([Name]);
