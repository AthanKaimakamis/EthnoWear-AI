CREATE PROCEDURE [ethnowear].[DisableUser]
    @UserId BIGINT
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;

    IF NOT EXISTS
    (
        SELECT 1
        FROM [ethnowear].[Users]
        WHERE [Id] = @UserId
    )
        THROW 51600, 'User does not exist.', 1;

    DECLARE @AdministratorRoleId BIGINT;

    SELECT @AdministratorRoleId = [Id]
    FROM [ethnowear].[Roles]
    WHERE [Name] = N'ADMINISTRATOR';

    IF EXISTS
       (
           SELECT 1
           FROM [ethnowear].[Users] AS [users]
           INNER JOIN [ethnowear].[UserRoles] AS [userRoles]
               ON [userRoles].[UserId] = [users].[Id]
           WHERE [users].[Id] = @UserId
             AND [users].[Enabled] = 1
             AND [userRoles].[RoleId] = @AdministratorRoleId
       )
       AND (
           SELECT COUNT_BIG(*)
           FROM [ethnowear].[Users] AS [users]
           INNER JOIN [ethnowear].[UserRoles] AS [userRoles]
               ON [userRoles].[UserId] = [users].[Id]
           WHERE [users].[Enabled] = 1
             AND [userRoles].[RoleId] = @AdministratorRoleId
       ) <= 1
        THROW 51601, 'The final enabled administrator cannot be disabled.', 1;

    UPDATE [ethnowear].[Users]
    SET
        [Enabled] = 0,
        [TokenVersion] = [TokenVersion] + 1,
        [UpdatedAt] = SYSUTCDATETIME()
    WHERE [Id] = @UserId;
END;
