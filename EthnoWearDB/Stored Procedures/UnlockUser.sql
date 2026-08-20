CREATE PROCEDURE [ethnowear].[UnlockUser]
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
        THROW 51700, 'User does not exist.', 1;

    UPDATE [ethnowear].[Users]
    SET
        [FailedLoginAttempts] = 0,
        [LockedUntil] = NULL,
        [UpdatedAt] = SYSUTCDATETIME()
    WHERE [Id] = @UserId;
END;
