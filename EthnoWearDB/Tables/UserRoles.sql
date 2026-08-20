CREATE TABLE [ethnowear].[UserRoles]
(
    [UserId] BIGINT NOT NULL,
    [RoleId] BIGINT NOT NULL,
    [AssignedByUserId] BIGINT NULL,
    [AssignedAt] DATETIME2(7) NOT NULL CONSTRAINT [DF_UserRoles_AssignedAt] DEFAULT SYSUTCDATETIME(),

    CONSTRAINT [PK_UserRoles] PRIMARY KEY CLUSTERED ([UserId], [RoleId]),

    CONSTRAINT [FK_UserRoles_Users]
        FOREIGN KEY ([UserId])
        REFERENCES [ethnowear].[Users] ([Id]),

    CONSTRAINT [FK_UserRoles_Roles]
        FOREIGN KEY ([RoleId])
        REFERENCES [ethnowear].[Roles] ([Id]),

    CONSTRAINT [FK_UserRoles_AssignedByUser]
        FOREIGN KEY ([AssignedByUserId])
        REFERENCES [ethnowear].[Users] ([Id])
);

GO

CREATE INDEX [IX_UserRoles_RoleId]
ON [ethnowear].[UserRoles] ([RoleId]);

GO

CREATE INDEX [IX_UserRoles_AssignedByUserId]
ON [ethnowear].[UserRoles] ([AssignedByUserId])
WHERE [AssignedByUserId] IS NOT NULL;
