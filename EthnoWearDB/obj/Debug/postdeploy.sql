SET NOCOUNT ON;

MERGE [ethnowear].[Roles] AS [target]
USING (VALUES
    (N'ADMINISTRATOR', N'Full administration, user management, and archive management'),
    (N'REVIEWER', N'Reviews and approves archive, OCR, and knowledge content'),
    (N'EDITOR', N'Creates and edits archive and document content')
) AS [source] ([Name], [Description])
ON [target].[Name] = [source].[Name]
WHEN MATCHED AND ISNULL([target].[Description], N'') <> [source].[Description]
    THEN UPDATE SET
        [Description] = [source].[Description],
        [UpdatedAt] = SYSUTCDATETIME()
WHEN NOT MATCHED BY TARGET
    THEN INSERT ([Name], [Description])
         VALUES ([source].[Name], [source].[Description]);

DECLARE @AdminUserId BIGINT;

SELECT @AdminUserId = [Id]
FROM [ethnowear].[Users]
WHERE [NormalizedUsername] = N'ADMIN';

IF @AdminUserId IS NULL
BEGIN
    INSERT INTO [ethnowear].[Users]
    (
        [Username],
        [PasswordHash],
        [MustChangePassword],
        [TemporaryPasswordExpiresAt],
        [Enabled]
    )
    VALUES
    (
        N'admin',
        N'{bcrypt}$2a$12$R0omxgE7ImNkEdnNCuNR8eM.W/ETuotDQbJAUTeAPkKqsfvcDX4eS',
        0,
        NULL,
        1
    );

    SET @AdminUserId = SCOPE_IDENTITY();

    INSERT INTO [ethnowear].[UserInfo]
    (
        [UserId],
        [FirstName],
        [LastName]
    )
    VALUES
    (
        @AdminUserId,
        N'Demo',
        N'Administrator'
    );
END;

DECLARE @AdministratorRoleId BIGINT;

SELECT @AdministratorRoleId = [Id]
FROM [ethnowear].[Roles]
WHERE [Name] = N'ADMINISTRATOR';

IF NOT EXISTS
(
    SELECT 1
    FROM [ethnowear].[UserRoles]
    WHERE [UserId] = @AdminUserId
      AND [RoleId] = @AdministratorRoleId
)
BEGIN
    INSERT INTO [ethnowear].[UserRoles]
    (
        [UserId],
        [RoleId]
    )
    VALUES
    (
        @AdminUserId,
        @AdministratorRoleId
    );
END;

GO
