IF COL_LENGTH(N'ethnowear.ArchiveItems', N'OntologyRegionalMotifIri') IS NULL
BEGIN
    ALTER TABLE [ethnowear].[ArchiveItems]
        ADD [OntologyRegionalMotifIri] NVARCHAR(1000) NULL;
END;

GO

IF COL_LENGTH(N'ethnowear.ArchiveItems', N'OntologyRegionalMotifLocalName') IS NULL
BEGIN
    ALTER TABLE [ethnowear].[ArchiveItems]
        ADD [OntologyRegionalMotifLocalName] NVARCHAR(200) NULL;
END;

GO

IF NOT EXISTS (
    SELECT 1
    FROM sys.check_constraints
    WHERE [name] = N'CK_ArchiveItems_RegionalMotifIdentity'
)
BEGIN
    ALTER TABLE [ethnowear].[ArchiveItems] WITH CHECK
        ADD CONSTRAINT [CK_ArchiveItems_RegionalMotifIdentity]
        CHECK (([OntologyRegionalMotifIri] IS NULL AND [OntologyRegionalMotifLocalName] IS NULL)
            OR ([OntologyRegionalMotifIri] IS NOT NULL AND [OntologyRegionalMotifLocalName] IS NOT NULL));
END;

GO

IF EXISTS (
    SELECT 1 FROM sys.check_constraints
    WHERE [name] = N'CK_ArchiveItemFeatures_FeatureType'
)
    ALTER TABLE [ethnowear].[ArchiveItemFeatures]
        DROP CONSTRAINT [CK_ArchiveItemFeatures_FeatureType];
GO

ALTER TABLE [ethnowear].[ArchiveItemFeatures] WITH CHECK
    ADD CONSTRAINT [CK_ArchiveItemFeatures_FeatureType]
    CHECK ([FeatureType] IN (
        N'ORNAMENT', N'COLOR', N'TECHNIQUE', N'MOTIF',
        N'REGION', N'REGIONAL_EMBROIDERY', N'REGIONAL_MOTIF'
    ));
GO

IF EXISTS (
    SELECT 1 FROM sys.check_constraints
    WHERE [name] = N'CK_MediaEntityLinks_EntityType'
)
    ALTER TABLE [ethnowear].[MediaEntityLinks]
        DROP CONSTRAINT [CK_MediaEntityLinks_EntityType];
GO

ALTER TABLE [ethnowear].[MediaEntityLinks] WITH CHECK
    ADD CONSTRAINT [CK_MediaEntityLinks_EntityType]
    CHECK ([EntityType] IN (
        N'REGION', N'REGIONAL_EMBROIDERY', N'REGIONAL_MOTIF',
        N'MOTIF', N'ORNAMENT', N'TECHNIQUE', N'COLOR'
    ));
GO

IF NOT EXISTS (
    SELECT 1
    FROM sys.indexes
    WHERE [object_id] = OBJECT_ID(N'ethnowear.ArchiveItems')
      AND [name] = N'IX_ArchiveItems_OntologyRegionalMotifLocalName'
)
BEGIN
    CREATE INDEX [IX_ArchiveItems_OntologyRegionalMotifLocalName]
        ON [ethnowear].[ArchiveItems] ([OntologyRegionalMotifLocalName]);
END;

GO
