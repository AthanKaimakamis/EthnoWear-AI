CREATE PROCEDURE [ethnowear].[EnableUser]
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
        THROW 51500, 'User does not exist.', 1;

    IF EXISTS
    (
        SELECT 1
        FROM [ethnowear].[Users]
        WHERE [Id] = @UserId
          AND [PasswordHash] IS NULL
    )
        THROW 51501, 'A password must be assigned before enabling the user.', 1;

    IF NOT EXISTS
    (
        SELECT 1
        FROM [ethnowear].[UserRoles]
        WHERE [UserId] = @UserId
    )
        THROW 51502, 'At least one role must be assigned before enabling the user.', 1;

    IF EXISTS
    (
        SELECT 1
        FROM [ethnowear].[Users]
        WHERE [Id] = @UserId
          AND [MustChangePassword] = 1
          AND [TemporaryPasswordExpiresAt] <= SYSUTCDATETIME()
    )
        THROW 51503, 'The temporary password has expired.', 1;

    UPDATE [ethnowear].[Users]
    SET
        [Enabled] = 1,
        [UpdatedAt] = SYSUTCDATETIME()
    WHERE [Id] = @UserId;
END;
