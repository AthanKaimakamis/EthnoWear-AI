CREATE PROCEDURE [ethnowear].[RemoveUserRole]
    @UserId BIGINT,
    @RoleName NVARCHAR(50)
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;

    SET @RoleName = UPPER(LTRIM(RTRIM(@RoleName)));

    DECLARE @RoleId BIGINT;

    SELECT @RoleId = [Id]
    FROM [ethnowear].[Roles]
    WHERE [Name] = @RoleName;

    IF @RoleId IS NULL
        THROW 51300, 'Role does not exist.', 1;

    IF NOT EXISTS
    (
        SELECT 1
        FROM [ethnowear].[Users]
        WHERE [Id] = @UserId
    )
        THROW 51302, 'User does not exist.', 1;

    IF @RoleName = N'ADMINISTRATOR'
       AND EXISTS
       (
           SELECT 1
           FROM [ethnowear].[Users]
           WHERE [Id] = @UserId
             AND [Enabled] = 1
       )
       AND (
           SELECT COUNT_BIG(*)
           FROM [ethnowear].[Users] AS [users]
           INNER JOIN [ethnowear].[UserRoles] AS [userRoles]
               ON [userRoles].[UserId] = [users].[Id]
           WHERE [users].[Enabled] = 1
             AND [userRoles].[RoleId] = @RoleId
       ) <= 1
        THROW 51301, 'The final enabled administrator cannot lose the administrator role.', 1;

    IF EXISTS
       (
           SELECT 1
           FROM [ethnowear].[Users]
           WHERE [Id] = @UserId
             AND [Enabled] = 1
       )
       AND (
           SELECT COUNT_BIG(*)
           FROM [ethnowear].[UserRoles]
           WHERE [UserId] = @UserId
       ) <= 1
        THROW 51303, 'An enabled user must retain at least one role.', 1;

    DELETE FROM [ethnowear].[UserRoles]
    WHERE [UserId] = @UserId
      AND [RoleId] = @RoleId;
END;
