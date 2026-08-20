CREATE PROCEDURE [ethnowear].[SetUserPasswordHash]
    @UserId BIGINT,
    @PasswordHash NVARCHAR(255),
    @MustChangePassword BIT = 1,
    @TemporaryPasswordExpiresAt DATETIME2(7) = NULL,
    @EnableUser BIT = 1
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;

    IF @UserId IS NULL
        THROW 51000, 'User id is required.', 1;

    IF NOT EXISTS
    (
        SELECT 1
        FROM [ethnowear].[Users]
        WHERE [Id] = @UserId
    )
        THROW 51001, 'User does not exist.', 1;

    IF @PasswordHash IS NULL
       OR LEN(@PasswordHash) <> 68
       OR @PasswordHash NOT LIKE N'{bcrypt}$2[aby]$12$%'
        THROW 51002, 'A Spring delegated BCrypt hash with cost 12 is required.', 1;

    IF @MustChangePassword = 0 AND @TemporaryPasswordExpiresAt IS NOT NULL
        THROW 51003, 'A permanent password cannot have a temporary-password expiry.', 1;

    IF @MustChangePassword = 1
       AND (
           @TemporaryPasswordExpiresAt IS NULL
           OR @TemporaryPasswordExpiresAt <= SYSUTCDATETIME()
       )
        THROW 51004, 'A temporary password requires a future expiry.', 1;

    IF @EnableUser = 1
       AND NOT EXISTS
       (
           SELECT 1
           FROM [ethnowear].[UserRoles]
           WHERE [UserId] = @UserId
       )
        THROW 51005, 'At least one role must be assigned before enabling the user.', 1;

    UPDATE [ethnowear].[Users]
    SET
        [PasswordHash] = @PasswordHash,
        [MustChangePassword] = @MustChangePassword,
        [TemporaryPasswordExpiresAt] = @TemporaryPasswordExpiresAt,
        [Enabled] = @EnableUser,
        [FailedLoginAttempts] = 0,
        [LockedUntil] = NULL,
        [TokenVersion] = [TokenVersion] + 1,
        [UpdatedAt] = SYSUTCDATETIME()
    WHERE [Id] = @UserId;
END;
