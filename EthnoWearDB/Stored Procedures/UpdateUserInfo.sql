CREATE PROCEDURE [ethnowear].[UpdateUserInfo]
    @UserId BIGINT,
    @FirstName NVARCHAR(100),
    @LastName NVARCHAR(100),
    @Email NVARCHAR(320) = NULL,
    @Phone NVARCHAR(50) = NULL,
    @AddressLine1 NVARCHAR(250) = NULL,
    @AddressLine2 NVARCHAR(250) = NULL,
    @City NVARCHAR(100) = NULL,
    @PostalCode NVARCHAR(20) = NULL,
    @CountryCode CHAR(2) = NULL
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;

    SET @FirstName = LTRIM(RTRIM(@FirstName));
    SET @LastName = LTRIM(RTRIM(@LastName));
    SET @Email = NULLIF(LTRIM(RTRIM(@Email)), N'');
    SET @CountryCode = NULLIF(UPPER(LTRIM(RTRIM(@CountryCode))), N'');

    IF @FirstName IS NULL OR LEN(@FirstName) = 0
        THROW 51400, 'First name is required.', 1;

    IF @LastName IS NULL OR LEN(@LastName) = 0
        THROW 51401, 'Last name is required.', 1;

    IF @CountryCode IS NOT NULL AND @CountryCode NOT LIKE '[A-Z][A-Z]'
        THROW 51402, 'Country code must contain two uppercase letters.', 1;

    IF NOT EXISTS
    (
        SELECT 1
        FROM [ethnowear].[UserInfo]
        WHERE [UserId] = @UserId
    )
        THROW 51403, 'User profile does not exist.', 1;

    IF @Email IS NOT NULL
       AND EXISTS
       (
           SELECT 1
           FROM [ethnowear].[UserInfo]
           WHERE [NormalizedEmail] = UPPER(@Email)
             AND [UserId] <> @UserId
       )
        THROW 51404, 'Email address already exists.', 1;

    UPDATE [ethnowear].[UserInfo]
    SET
        [FirstName] = @FirstName,
        [LastName] = @LastName,
        [Email] = @Email,
        [Phone] = @Phone,
        [AddressLine1] = @AddressLine1,
        [AddressLine2] = @AddressLine2,
        [City] = @City,
        [PostalCode] = @PostalCode,
        [CountryCode] = @CountryCode,
        [UpdatedAt] = SYSUTCDATETIME()
    WHERE [UserId] = @UserId;
END;
