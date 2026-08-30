SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;

IF COL_LENGTH(N'ethnowear.DocumentPageTextSuggestions', N'RequiresReview') IS NULL
BEGIN
    ALTER TABLE [ethnowear].[DocumentPageTextSuggestions]
    ADD [RequiresReview] BIT NOT NULL
        CONSTRAINT [DF_DocumentPageTextSuggestions_RequiresReview]
        DEFAULT (1) WITH VALUES;
END;

IF COL_LENGTH(N'ethnowear.DocumentPageTextSuggestions', N'UncertainPassagesJson') IS NULL
BEGIN
    ALTER TABLE [ethnowear].[DocumentPageTextSuggestions]
    ADD [UncertainPassagesJson] NVARCHAR(4000) NOT NULL
        CONSTRAINT [DF_DocumentPageTextSuggestions_UncertainPassagesJson]
        DEFAULT (N'[]') WITH VALUES;
END;

IF NOT EXISTS (
    SELECT 1
    FROM sys.check_constraints
    WHERE [name] = N'CK_DocumentPageTextSuggestions_UncertainPassagesJson'
      AND [parent_object_id] = OBJECT_ID(N'ethnowear.DocumentPageTextSuggestions')
)
BEGIN
    EXEC(N'
        ALTER TABLE [ethnowear].[DocumentPageTextSuggestions]
        ADD CONSTRAINT [CK_DocumentPageTextSuggestions_UncertainPassagesJson]
            CHECK (ISJSON([UncertainPassagesJson]) = 1);
    ');
END;
