CREATE PROCEDURE [ethnowear].[AssignUserRole]
    @UserId BIGINT,
    @RoleName NVARCHAR(50),
    @AssignedByUserId BIGINT = NULL
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;

    SET @RoleName = UPPER(LTRIM(RTRIM(@RoleName)));

    IF NOT EXISTS
    (
        SELECT 1
        FROM [ethnowear].[Users]
        WHERE [Id] = @UserId
    )
        THROW 51200, 'User does not exist.', 1;

    IF @AssignedByUserId IS NOT NULL
       AND NOT EXISTS
       (
           SELECT 1
           FROM [ethnowear].[Users]
           WHERE [Id] = @AssignedByUserId
       )
        THROW 51201, 'Assigning user does not exist.', 1;

    DECLARE @RoleId BIGINT;

    SELECT @RoleId = [Id]
    FROM [ethnowear].[Roles]
    WHERE [Name] = @RoleName;

    IF @RoleId IS NULL
        THROW 51202, 'Role does not exist.', 1;

    IF NOT EXISTS
    (
        SELECT 1
        FROM [ethnowear].[UserRoles]
        WHERE [UserId] = @UserId
          AND [RoleId] = @RoleId
    )
    BEGIN
        INSERT INTO [ethnowear].[UserRoles]
        (
            [UserId],
            [RoleId],
            [AssignedByUserId]
        )
        VALUES
        (
            @UserId,
            @RoleId,
            @AssignedByUserId
        );
    END;
END;
