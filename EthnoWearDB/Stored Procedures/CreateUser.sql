CREATE PROCEDURE [ethnowear].[CreateUser]
    @Username NVARCHAR(100),
    @FirstName NVARCHAR(100),
    @LastName NVARCHAR(100),
    @Email NVARCHAR(320) = NULL,
    @Phone NVARCHAR(50) = NULL,
    @AddressLine1 NVARCHAR(250) = NULL,
    @AddressLine2 NVARCHAR(250) = NULL,
    @City NVARCHAR(100) = NULL,
    @PostalCode NVARCHAR(20) = NULL,
    @CountryCode CHAR(2) = NULL,
    @CreatedByUserId BIGINT = NULL,
    @UserId BIGINT = NULL OUTPUT
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;

    SET @Username = LTRIM(RTRIM(@Username));
    SET @FirstName = LTRIM(RTRIM(@FirstName));
    SET @LastName = LTRIM(RTRIM(@LastName));
    SET @Email = NULLIF(LTRIM(RTRIM(@Email)), N'');
    SET @CountryCode = NULLIF(UPPER(LTRIM(RTRIM(@CountryCode))), N'');

    IF @Username IS NULL OR LEN(@Username) NOT BETWEEN 3 AND 100
        THROW 51100, 'Username must contain between 3 and 100 characters.', 1;

    IF @FirstName IS NULL OR LEN(@FirstName) = 0
        THROW 51101, 'First name is required.', 1;

    IF @LastName IS NULL OR LEN(@LastName) = 0
        THROW 51102, 'Last name is required.', 1;

    IF @CountryCode IS NOT NULL AND @CountryCode NOT LIKE '[A-Z][A-Z]'
        THROW 51103, 'Country code must contain two uppercase letters.', 1;

    IF @CreatedByUserId IS NOT NULL
       AND NOT EXISTS
       (
           SELECT 1
           FROM [ethnowear].[Users]
           WHERE [Id] = @CreatedByUserId
       )
        THROW 51104, 'Creating user does not exist.', 1;

    IF EXISTS
    (
        SELECT 1
        FROM [ethnowear].[Users]
        WHERE [NormalizedUsername] = UPPER(@Username)
    )
        THROW 51105, 'Username already exists.', 1;

    IF @Email IS NOT NULL
       AND EXISTS
       (
           SELECT 1
           FROM [ethnowear].[UserInfo]
           WHERE [NormalizedEmail] = UPPER(@Email)
       )
        THROW 51106, 'Email address already exists.', 1;

    BEGIN TRANSACTION;

    INSERT INTO [ethnowear].[Users]
    (
        [Username],
        [PasswordHash],
        [MustChangePassword],
        [TemporaryPasswordExpiresAt],
        [Enabled],
        [CreatedByUserId]
    )
    VALUES
    (
        @Username,
        NULL,
        1,
        NULL,
        0,
        @CreatedByUserId
    );

    SET @UserId = SCOPE_IDENTITY();

    INSERT INTO [ethnowear].[UserInfo]
    (
        [UserId],
        [FirstName],
        [LastName],
        [Email],
        [Phone],
        [AddressLine1],
        [AddressLine2],
        [City],
        [PostalCode],
        [CountryCode]
    )
    VALUES
    (
        @UserId,
        @FirstName,
        @LastName,
        @Email,
        @Phone,
        @AddressLine1,
        @AddressLine2,
        @City,
        @PostalCode,
        @CountryCode
    );

    COMMIT TRANSACTION;

    SELECT @UserId AS [UserId];
END;
