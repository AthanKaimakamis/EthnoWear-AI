-- Explicit deployment only. Never reinterpret a management user id as a public id.
IF COL_LENGTH(N'ethnowear.Conversations', N'OwnerUserId') IS NOT NULL
BEGIN
    EXEC sys.sp_executesql N'
        IF EXISTS (SELECT 1 FROM ethnowear.Conversations WHERE OwnerUserId IS NOT NULL)
            THROW 51000, ''Management-owned conversations require an explicit ownership migration decision'', 1;
        ALTER TABLE ethnowear.Conversations DROP CONSTRAINT FK_Conversations_Users;
        ALTER TABLE ethnowear.Conversations DROP CONSTRAINT CK_Conversations_Owner;
        DROP INDEX UQ_Conversations_User_Request ON ethnowear.Conversations;
        DROP INDEX IX_Conversations_User_UpdatedAt ON ethnowear.Conversations;
        EXEC sys.sp_rename N''ethnowear.Conversations.OwnerUserId'', N''PublicUserId'', N''COLUMN'';
    ';
    EXEC sys.sp_executesql N'
        ALTER TABLE ethnowear.Conversations WITH CHECK ADD CONSTRAINT FK_Conversations_PublicUsers
            FOREIGN KEY (PublicUserId) REFERENCES ethnowear.PublicUsers(Id);
        ALTER TABLE ethnowear.Conversations WITH CHECK ADD CONSTRAINT CK_Conversations_Owner
            CHECK ((PublicUserId IS NOT NULL AND GuestSessionId IS NULL)
                OR (PublicUserId IS NULL AND GuestSessionId IS NOT NULL));
        CREATE UNIQUE INDEX UQ_Conversations_User_Request ON ethnowear.Conversations
            (PublicUserId, ClientRequestId) WHERE PublicUserId IS NOT NULL;
        CREATE INDEX IX_Conversations_User_UpdatedAt ON ethnowear.Conversations
            (PublicUserId, UpdatedAt DESC, Id DESC) WHERE PublicUserId IS NOT NULL;
    ';
END;
