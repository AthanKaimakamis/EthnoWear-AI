SET NOCOUNT ON;
SET XACT_ABORT ON;
SET ANSI_NULLS ON;
SET ANSI_PADDING ON;
SET ANSI_WARNINGS ON;
SET ARITHABORT ON;
SET CONCAT_NULL_YIELDS_NULL ON;
SET QUOTED_IDENTIFIER ON;
SET NUMERIC_ROUNDABORT OFF;
BEGIN TRANSACTION;

DELETE FROM [ethnowear].[ConversationTurnEvents];
DELETE FROM [ethnowear].[ConversationTurnEvidence];
DELETE FROM [ethnowear].[ConversationTurns];
DELETE FROM [ethnowear].[Conversations];
DELETE FROM [ethnowear].[ConversationGuestSessions];
DELETE FROM [ethnowear].[PublicUserSessions];
DELETE FROM [ethnowear].[PublicUserIdentities];
DELETE FROM [ethnowear].[PublicUsers];
DELETE FROM [ethnowear].[PublicLoginChallenges];

UPDATE [ethnowear].[Users]
SET [Username] = CONCAT(N'demo-user-', [Id]), [PasswordHash] = NULL,
    [MustChangePassword] = 1, [TemporaryPasswordExpiresAt] = NULL,
    [Enabled] = 0, [FailedLoginAttempts] = 0, [LockedUntil] = NULL,
    [TokenVersion] = [TokenVersion] + 1, [LastLoginAt] = NULL,
    [UpdatedAt] = SYSUTCDATETIME()
WHERE [NormalizedUsername] <> N'ADMIN';

UPDATE info
SET [FirstName] = N'Demo', [LastName] = CONCAT(N'User ', info.[UserId]),
    [Email] = NULL, [Phone] = NULL, [AddressLine1] = NULL,
    [AddressLine2] = NULL, [City] = NULL, [PostalCode] = NULL,
    [CountryCode] = NULL, [UpdatedAt] = SYSUTCDATETIME()
FROM [ethnowear].[UserInfo] info
JOIN [ethnowear].[Users] users ON users.[Id] = info.[UserId]
WHERE users.[NormalizedUsername] <> N'ADMIN';

UPDATE [ethnowear].[Users]
SET [Username] = N'admin',
    [PasswordHash] = N'{bcrypt}$2a$12$R0omxgE7ImNkEdnNCuNR8eM.W/ETuotDQbJAUTeAPkKqsfvcDX4eS',
    [MustChangePassword] = 0, [TemporaryPasswordExpiresAt] = NULL,
    [Enabled] = 1, [FailedLoginAttempts] = 0, [LockedUntil] = NULL,
    [TokenVersion] = [TokenVersion] + 1, [LastLoginAt] = NULL,
    [DeletedAt] = NULL, [DeletedByUserId] = NULL,
    [UpdatedAt] = SYSUTCDATETIME()
WHERE [NormalizedUsername] = N'ADMIN';

UPDATE info
SET [FirstName] = N'Demo', [LastName] = N'Administrator',
    [Email] = NULL, [Phone] = NULL, [AddressLine1] = NULL,
    [AddressLine2] = NULL, [City] = NULL, [PostalCode] = NULL,
    [CountryCode] = NULL, [UpdatedAt] = SYSUTCDATETIME()
FROM [ethnowear].[UserInfo] info
JOIN [ethnowear].[Users] users ON users.[Id] = info.[UserId]
WHERE users.[NormalizedUsername] = N'ADMIN';

UPDATE [ethnowear].[DocumentPageFigures]
SET [ReviewedBy] = N'demo-reviewer' WHERE [ReviewedBy] IS NOT NULL;
UPDATE [ethnowear].[DocumentPageProvenanceEvents]
SET [ReviewedBy] = N'demo-reviewer';
UPDATE [ethnowear].[DocumentPages]
SET [ProvenanceReviewedBy] = N'demo-reviewer'
WHERE [ProvenanceReviewedBy] IS NOT NULL;

COMMIT TRANSACTION;
