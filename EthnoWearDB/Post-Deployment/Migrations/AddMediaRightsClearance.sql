SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;

IF COL_LENGTH(N'ethnowear.Sources', N'RightsStatus') IS NULL
BEGIN
    ALTER TABLE [ethnowear].[Sources]
    ADD [RightsStatus] NVARCHAR(30) NOT NULL
        CONSTRAINT [DF_Sources_RightsStatus] DEFAULT N'UNKNOWN' WITH VALUES;
END;

IF COL_LENGTH(N'ethnowear.Sources', N'License') IS NULL
    ALTER TABLE [ethnowear].[Sources] ADD [License] NVARCHAR(500) NULL;

IF COL_LENGTH(N'ethnowear.Sources', N'PublicDisplayAllowed') IS NULL
BEGIN
    ALTER TABLE [ethnowear].[Sources]
    ADD [PublicDisplayAllowed] BIT NOT NULL
        CONSTRAINT [DF_Sources_PublicDisplayAllowed] DEFAULT (0) WITH VALUES;
END;

IF COL_LENGTH(N'ethnowear.MediaAssets', N'RightsStatus') IS NULL
BEGIN
    ALTER TABLE [ethnowear].[MediaAssets]
    ADD [RightsStatus] NVARCHAR(30) NOT NULL
        CONSTRAINT [DF_MediaAssets_RightsStatus] DEFAULT N'UNKNOWN' WITH VALUES;
END;

IF COL_LENGTH(N'ethnowear.MediaAssets', N'License') IS NULL
    ALTER TABLE [ethnowear].[MediaAssets] ADD [License] NVARCHAR(500) NULL;

IF COL_LENGTH(N'ethnowear.MediaAssets', N'PublicDisplayAllowed') IS NULL
BEGIN
    ALTER TABLE [ethnowear].[MediaAssets]
    ADD [PublicDisplayAllowed] BIT NOT NULL
        CONSTRAINT [DF_MediaAssets_PublicDisplayAllowed] DEFAULT (0) WITH VALUES;
END;

GO

IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE [name] = N'CK_Sources_RightsStatus')
    ALTER TABLE [ethnowear].[Sources] ADD CONSTRAINT [CK_Sources_RightsStatus]
        CHECK ([RightsStatus] IN (N'UNKNOWN', N'PUBLIC_DOMAIN', N'LICENSED', N'RESTRICTED'));

IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE [name] = N'CK_Sources_License')
    ALTER TABLE [ethnowear].[Sources] ADD CONSTRAINT [CK_Sources_License]
        CHECK ([RightsStatus] <> N'LICENSED'
            OR ([License] IS NOT NULL AND LEN(LTRIM(RTRIM([License]))) > 0));

IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE [name] = N'CK_Sources_PublicDisplay')
    ALTER TABLE [ethnowear].[Sources] ADD CONSTRAINT [CK_Sources_PublicDisplay]
        CHECK ([PublicDisplayAllowed] = 0
            OR [RightsStatus] IN (N'PUBLIC_DOMAIN', N'LICENSED'));

IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE [name] = N'CK_MediaAssets_RightsStatus')
    ALTER TABLE [ethnowear].[MediaAssets] ADD CONSTRAINT [CK_MediaAssets_RightsStatus]
        CHECK ([RightsStatus] IN (N'UNKNOWN', N'PUBLIC_DOMAIN', N'LICENSED', N'RESTRICTED'));

IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE [name] = N'CK_MediaAssets_License')
    ALTER TABLE [ethnowear].[MediaAssets] ADD CONSTRAINT [CK_MediaAssets_License]
        CHECK ([RightsStatus] <> N'LICENSED'
            OR ([License] IS NOT NULL AND LEN(LTRIM(RTRIM([License]))) > 0));

IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE [name] = N'CK_MediaAssets_PublicDisplay')
    ALTER TABLE [ethnowear].[MediaAssets] ADD CONSTRAINT [CK_MediaAssets_PublicDisplay]
        CHECK ([PublicDisplayAllowed] = 0
            OR [RightsStatus] IN (N'PUBLIC_DOMAIN', N'LICENSED'));
