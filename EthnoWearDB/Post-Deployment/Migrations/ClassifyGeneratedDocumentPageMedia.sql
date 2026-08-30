SET ANSI_NULLS ON;
SET ANSI_PADDING ON;
SET ANSI_WARNINGS ON;
SET ARITHABORT ON;
SET CONCAT_NULL_YIELDS_NULL ON;
SET QUOTED_IDENTIFIER ON;
SET NUMERIC_ROUNDABORT OFF;
SET NOCOUNT ON;

UPDATE media
SET
    [Origin] = N'GENERATED',
    [RetentionPolicy] = N'KEEP_ORIGINAL_ONLY',
    [UpdatedAt] = SYSUTCDATETIME()
FROM [ethnowear].[MediaAssets] AS media
WHERE
    (
        media.[Origin] <> N'GENERATED'
        OR media.[RetentionPolicy] <> N'KEEP_ORIGINAL_ONLY'
    )
    AND EXISTS (
        SELECT 1
        FROM [ethnowear].[DocumentPageMedia] AS rendition
        WHERE rendition.[MediaAssetId] = media.[Id]
          AND rendition.[ProducingJobId] IS NOT NULL
          AND rendition.[RenditionType] IN (
              N'PDF_PAGE_RENDER',
              N'PREPROCESSED_OCR_INPUT',
              N'CROPPED',
              N'DESKEWED',
              N'BINARIZED',
              N'SEARCHABLE_PDF_PAGE',
              N'THUMBNAIL'
          )
    )
    AND NOT EXISTS (
        SELECT 1
        FROM [ethnowear].[DocumentPageMedia] AS preserved
        WHERE preserved.[MediaAssetId] = media.[Id]
          AND preserved.[RenditionType] IN (
              N'ORIGINAL_UPLOAD',
              N'PHONE_PHOTO',
              N'REPLACEMENT_SCAN'
          )
    );
