CREATE TABLE [ethnowear].[PublicUserIdentities]
(
    [Id] BIGINT IDENTITY(1,1) NOT NULL,
    [PublicUserId] BIGINT NOT NULL,
    [Issuer] NVARCHAR(100) COLLATE Latin1_General_100_BIN2 NOT NULL,
    [Subject] NVARCHAR(255) COLLATE Latin1_General_100_BIN2 NOT NULL,
    [CreatedAt] DATETIME2(7) NOT NULL CONSTRAINT [DF_PublicUserIdentities_CreatedAt] DEFAULT SYSUTCDATETIME(),
    CONSTRAINT [PK_PublicUserIdentities] PRIMARY KEY CLUSTERED ([Id]),
    CONSTRAINT [FK_PublicUserIdentities_PublicUsers] FOREIGN KEY ([PublicUserId]) REFERENCES [ethnowear].[PublicUsers] ([Id]),
    CONSTRAINT [UQ_PublicUserIdentities_Issuer_Subject] UNIQUE ([Issuer], [Subject]),
    CONSTRAINT [UQ_PublicUserIdentities_User_Issuer] UNIQUE ([PublicUserId], [Issuer]),
    CONSTRAINT [CK_PublicUserIdentities_Google] CHECK ([Issuer] = N'https://accounts.google.com'),
    CONSTRAINT [CK_PublicUserIdentities_Subject] CHECK (LEN(LTRIM(RTRIM([Subject]))) > 0)
);
