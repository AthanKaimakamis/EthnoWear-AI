SET NOCOUNT ON;
SET XACT_ABORT OFF;
SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_PADDING ON;
SET ANSI_WARNINGS ON;
SET CONCAT_NULL_YIELDS_NULL ON;
SET ARITHABORT ON;
SET NUMERIC_ROUNDABORT OFF;
GO
CREATE PROCEDURE #ExpectFailure @Name NVARCHAR(150), @Sql NVARCHAR(MAX), @Expected INT
AS
BEGIN
    BEGIN TRY
        EXEC sys.sp_executesql @Sql;
    END TRY
    BEGIN CATCH
        IF ERROR_NUMBER() <> @Expected THROW;
        PRINT N'PASS: ' + @Name;
        RETURN;
    END CATCH;
    THROW 51001, 'Expected constraint violation was not raised', 1;
END;
GO
BEGIN TRANSACTION;
CREATE TABLE #Fixture(GuestId BIGINT, UserId BIGINT, ConversationId BIGINT, TurnId BIGINT, RequestId UNIQUEIDENTIFIER);
INSERT ethnowear.PublicUsers(PublicId, DisplayName, Email, LastLoginAt)
VALUES (NEWID(), N'Schema test', N'test@example.invalid', SYSUTCDATETIME());
DECLARE @userId BIGINT = SCOPE_IDENTITY();
DECLARE @request UNIQUEIDENTIFIER=NEWID();
INSERT ethnowear.ConversationGuestSessions(TokenHash,ExpiresAt)
VALUES(LOWER(CONVERT(NVARCHAR(64),HASHBYTES('SHA2_256',CONVERT(VARCHAR(36),NEWID())),2)),DATEADD(DAY,1,SYSUTCDATETIME()));
DECLARE @guest BIGINT=SCOPE_IDENTITY();
INSERT ethnowear.Conversations(PublicId,GuestSessionId,ClientRequestId,Language)
VALUES(NEWID(),@guest,@request,N'bg');
DECLARE @conversation BIGINT=SCOPE_IDENTITY();
INSERT ethnowear.Conversations(PublicId,PublicUserId,ClientRequestId,Language)
VALUES(NEWID(),@userId,@request,N'en');
PRINT 'PASS: same request ID is isolated by owner';
INSERT ethnowear.ConversationTurns(PublicId,ConversationId,ClientRequestId,TurnSequence,UserMessage,RequestHash,Stage)
VALUES(NEWID(),@conversation,NEWID(),1,N'Test question',REPLICATE(N'a',64),N'RECEIVED');
DECLARE @turn BIGINT=SCOPE_IDENTITY();
INSERT #Fixture VALUES(@guest,@userId,@conversation,@turn,@request);
INSERT ethnowear.ConversationTurnEvents(ConversationTurnId,EventId,Status,Stage)
VALUES(@turn,1,N'QUEUED',N'RECEIVED');
UPDATE ethnowear.ConversationTurns SET LastEventId=1 WHERE Id=@turn;
INSERT ethnowear.ConversationTurnEvidence(ConversationTurnId,EvidenceKey,EvidenceType,SnapshotJson)
VALUES(@turn,N'document:1',N'DOCUMENT',N'{"chunkId":1,"documentId":2,"pageIds":[3],"printedPageNumber":"111"}');
PRINT 'PASS: valid ownership, turn, event and private citation snapshot';
EXEC #ExpectFailure N'missing owner', N'INSERT ethnowear.Conversations(PublicId, ClientRequestId, Language) VALUES(NEWID(), NEWID(), N''bg'')', 547;
EXEC #ExpectFailure N'two owners', N'INSERT ethnowear.Conversations(PublicId, ClientRequestId, Language, PublicUserId, GuestSessionId) SELECT NEWID(), NEWID(), N''bg'', UserId, GuestId FROM #Fixture', 547;
EXEC #ExpectFailure N'unknown user', N'INSERT ethnowear.Conversations(PublicId, ClientRequestId, Language, PublicUserId) VALUES(NEWID(), NEWID(), N''bg'', -1)', 547;
EXEC #ExpectFailure N'unknown guest', N'INSERT ethnowear.Conversations(PublicId, ClientRequestId, Language, GuestSessionId) VALUES(NEWID(), NEWID(), N''bg'', -1)', 547;
EXEC #ExpectFailure N'guest request uniqueness', N'INSERT ethnowear.Conversations(PublicId, ClientRequestId, Language, GuestSessionId) SELECT NEWID(), RequestId, N''bg'', GuestId FROM #Fixture', 2601;
EXEC #ExpectFailure N'user request uniqueness', N'INSERT ethnowear.Conversations(PublicId, ClientRequestId, Language, PublicUserId) SELECT NEWID(), RequestId, N''bg'', UserId FROM #Fixture', 2601;
EXEC #ExpectFailure N'public conversation ID uniqueness', N'INSERT ethnowear.Conversations(PublicId, ClientRequestId, Language, GuestSessionId) SELECT c.PublicId, NEWID(), N''bg'', f.GuestId FROM #Fixture f JOIN ethnowear.Conversations c ON c.Id=f.ConversationId', 2627;
EXEC #ExpectFailure N'unsupported language', N'UPDATE ethnowear.Conversations SET Language=N''fr'' WHERE Id=(SELECT ConversationId FROM #Fixture)', 547;
EXEC #ExpectFailure N'case sensitive language', N'UPDATE ethnowear.Conversations SET Language=N''BG'' WHERE Id=(SELECT ConversationId FROM #Fixture)', 547;
EXEC #ExpectFailure N'empty title', N'UPDATE ethnowear.Conversations SET Title=N''   '' WHERE Id=(SELECT ConversationId FROM #Fixture)', 547;
EXEC #ExpectFailure N'invalid guest hash', N'UPDATE ethnowear.ConversationGuestSessions SET TokenHash=REPLICATE(N''G'',64) WHERE Id=(SELECT GuestId FROM #Fixture)', 547;
EXEC #ExpectFailure N'short guest hash', N'UPDATE ethnowear.ConversationGuestSessions SET TokenHash=N''ab'' WHERE Id=(SELECT GuestId FROM #Fixture)', 547;
EXEC #ExpectFailure N'expired-at-creation guest', N'UPDATE ethnowear.ConversationGuestSessions SET ExpiresAt=CreatedAt WHERE Id=(SELECT GuestId FROM #Fixture)', 547;
EXEC #ExpectFailure N'invalid revocation time', N'UPDATE ethnowear.ConversationGuestSessions SET RevokedAt=DATEADD(SECOND,-1,CreatedAt) WHERE Id=(SELECT GuestId FROM #Fixture)', 547;
EXEC #ExpectFailure N'duplicate guest token', N'INSERT ethnowear.ConversationGuestSessions(TokenHash,ExpiresAt) SELECT TokenHash,DATEADD(DAY,1,SYSUTCDATETIME()) FROM ethnowear.ConversationGuestSessions WHERE Id=(SELECT GuestId FROM #Fixture)', 2601;
EXEC #ExpectFailure N'second active turn', N'INSERT ethnowear.ConversationTurns(PublicId,ConversationId,ClientRequestId,TurnSequence,UserMessage,RequestHash,Stage) SELECT NEWID(),ConversationId,NEWID(),2,N''Test'',REPLICATE(N''a'',64),N''RECEIVED'' FROM #Fixture', 2601;
EXEC #ExpectFailure N'turn request uniqueness', N'INSERT ethnowear.ConversationTurns(PublicId,ConversationId,ClientRequestId,TurnSequence,UserMessage,RequestHash,Status,IsActive,FinishedAt) SELECT NEWID(),t.ConversationId,t.ClientRequestId,2,N''Test'',REPLICATE(N''a'',64),N''CANCELLED'',0,SYSUTCDATETIME() FROM ethnowear.ConversationTurns t JOIN #Fixture f ON t.Id=f.TurnId', 2627;
EXEC #ExpectFailure N'turn sequence uniqueness', N'INSERT ethnowear.ConversationTurns(PublicId,ConversationId,ClientRequestId,TurnSequence,UserMessage,RequestHash,Status,IsActive,FinishedAt) SELECT NEWID(),ConversationId,NEWID(),1,N''Test'',REPLICATE(N''a'',64),N''CANCELLED'',0,SYSUTCDATETIME() FROM #Fixture', 2627;
EXEC #ExpectFailure N'invalid sequence', N'UPDATE ethnowear.ConversationTurns SET TurnSequence=0 WHERE Id=(SELECT TurnId FROM #Fixture)', 547;
EXEC #ExpectFailure N'empty question', N'UPDATE ethnowear.ConversationTurns SET UserMessage=N''   '' WHERE Id=(SELECT TurnId FROM #Fixture)', 547;
EXEC #ExpectFailure N'question maximum length', N'UPDATE ethnowear.ConversationTurns SET UserMessage=REPLICATE(N''x'',1001) WHERE Id=(SELECT TurnId FROM #Fixture)', 2628;
EXEC #ExpectFailure N'invalid request hash', N'UPDATE ethnowear.ConversationTurns SET RequestHash=REPLICATE(N''A'',64) WHERE Id=(SELECT TurnId FROM #Fixture)', 547;
EXEC #ExpectFailure N'invalid status', N'UPDATE ethnowear.ConversationTurns SET Status=N''OTHER'' WHERE Id=(SELECT TurnId FROM #Fixture)', 547;
EXEC #ExpectFailure N'status casing', N'UPDATE ethnowear.ConversationTurns SET Status=N''queued'' WHERE Id=(SELECT TurnId FROM #Fixture)', 547;
EXEC #ExpectFailure N'active status mismatch', N'UPDATE ethnowear.ConversationTurns SET IsActive=0 WHERE Id=(SELECT TurnId FROM #Fixture)', 547;
EXEC #ExpectFailure N'active stage required', N'UPDATE ethnowear.ConversationTurns SET Stage=NULL WHERE Id=(SELECT TurnId FROM #Fixture)', 547;
EXEC #ExpectFailure N'invalid stage', N'UPDATE ethnowear.ConversationTurns SET Stage=N''OTHER'' WHERE Id=(SELECT TurnId FROM #Fixture)', 547;
EXEC #ExpectFailure N'running requires start', N'UPDATE ethnowear.ConversationTurns SET Status=N''RUNNING'' WHERE Id=(SELECT TurnId FROM #Fixture)', 547;
EXEC #ExpectFailure N'active cannot finish', N'UPDATE ethnowear.ConversationTurns SET FinishedAt=SYSUTCDATETIME() WHERE Id=(SELECT TurnId FROM #Fixture)', 547;
EXEC #ExpectFailure N'active cannot have answer', N'UPDATE ethnowear.ConversationTurns SET AnswerJson=N''{}'' WHERE Id=(SELECT TurnId FROM #Fixture)', 547;
EXEC #ExpectFailure N'active cannot have error', N'UPDATE ethnowear.ConversationTurns SET ErrorCode=N''FAILED'' WHERE Id=(SELECT TurnId FROM #Fixture)', 547;
EXEC #ExpectFailure N'negative event cursor', N'UPDATE ethnowear.ConversationTurns SET LastEventId=-1 WHERE Id=(SELECT TurnId FROM #Fixture)', 547;
EXEC #ExpectFailure N'completed requires answer', N'UPDATE ethnowear.ConversationTurns SET Status=N''COMPLETED'',IsActive=0,Stage=NULL,StartedAt=CreatedAt,FinishedAt=SYSUTCDATETIME() WHERE Id=(SELECT TurnId FROM #Fixture)', 547;
EXEC #ExpectFailure N'completed requires valid object', N'UPDATE ethnowear.ConversationTurns SET Status=N''COMPLETED'',IsActive=0,Stage=NULL,StartedAt=CreatedAt,FinishedAt=SYSUTCDATETIME(),AnswerJson=N''[]'' WHERE Id=(SELECT TurnId FROM #Fixture)', 547;
EXEC #ExpectFailure N'answer bound', N'UPDATE ethnowear.ConversationTurns SET Status=N''COMPLETED'',IsActive=0,Stage=NULL,StartedAt=CreatedAt,FinishedAt=SYSUTCDATETIME(),AnswerJson=N''{"answer":"''+REPLICATE(CAST(N''x'' AS NVARCHAR(MAX)),65536)+N''"}'' WHERE Id=(SELECT TurnId FROM #Fixture)', 547;
EXEC #ExpectFailure N'failed requires code', N'UPDATE ethnowear.ConversationTurns SET Status=N''FAILED'',IsActive=0,Stage=NULL,FinishedAt=SYSUTCDATETIME() WHERE Id=(SELECT TurnId FROM #Fixture)', 547;
EXEC #ExpectFailure N'safe error format', N'UPDATE ethnowear.ConversationTurns SET Status=N''FAILED'',IsActive=0,Stage=NULL,FinishedAt=SYSUTCDATETIME(),ErrorCode=N''SQL password: secret'' WHERE Id=(SELECT TurnId FROM #Fixture)', 547;
EXEC #ExpectFailure N'terminal has no stage', N'UPDATE ethnowear.ConversationTurns SET Status=N''CANCELLED'',IsActive=0,FinishedAt=SYSUTCDATETIME() WHERE Id=(SELECT TurnId FROM #Fixture)', 547;
EXEC #ExpectFailure N'event cursor uniqueness', N'INSERT ethnowear.ConversationTurnEvents(ConversationTurnId,EventId,Status,Stage) SELECT TurnId,1,N''QUEUED'',N''RECEIVED'' FROM #Fixture', 2627;
EXEC #ExpectFailure N'event cursor positive', N'INSERT ethnowear.ConversationTurnEvents(ConversationTurnId,EventId,Status,Stage) SELECT TurnId,0,N''QUEUED'',N''RECEIVED'' FROM #Fixture', 547;
EXEC #ExpectFailure N'event stage required', N'INSERT ethnowear.ConversationTurnEvents(ConversationTurnId,EventId,Status) SELECT TurnId,2,N''RUNNING'' FROM #Fixture', 547;
EXEC #ExpectFailure N'event terminal no stage', N'INSERT ethnowear.ConversationTurnEvents(ConversationTurnId,EventId,Status,Stage) SELECT TurnId,2,N''COMPLETED'',N''RECEIVED'' FROM #Fixture', 547;
EXEC #ExpectFailure N'event failed requires safe code', N'INSERT ethnowear.ConversationTurnEvents(ConversationTurnId,EventId,Status,ErrorCode) SELECT TurnId,2,N''FAILED'',N''raw exception'' FROM #Fixture', 547;
EXEC #ExpectFailure N'event unknown turn', N'INSERT ethnowear.ConversationTurnEvents(ConversationTurnId,EventId,Status,Stage) VALUES(-1,1,N''QUEUED'',N''RECEIVED'')', 547;
EXEC #ExpectFailure N'evidence key uniqueness', N'INSERT ethnowear.ConversationTurnEvidence(ConversationTurnId,EvidenceKey,EvidenceType,SnapshotJson) SELECT TurnId,N''document:1'',N''DOCUMENT'',N''{}'' FROM #Fixture', 2627;
EXEC #ExpectFailure N'evidence invalid JSON', N'UPDATE ethnowear.ConversationTurnEvidence SET SnapshotJson=N''{bad'' WHERE ConversationTurnId=(SELECT TurnId FROM #Fixture)', 547;
EXEC #ExpectFailure N'evidence object required', N'UPDATE ethnowear.ConversationTurnEvidence SET SnapshotJson=N''[]'' WHERE ConversationTurnId=(SELECT TurnId FROM #Fixture)', 547;
EXEC #ExpectFailure N'evidence bound', N'UPDATE ethnowear.ConversationTurnEvidence SET SnapshotJson=N''{"text":"''+REPLICATE(CAST(N''x'' AS NVARCHAR(MAX)),16384)+N''"}'' WHERE ConversationTurnId=(SELECT TurnId FROM #Fixture)', 547;
EXEC #ExpectFailure N'evidence type', N'UPDATE ethnowear.ConversationTurnEvidence SET EvidenceType=N''RAW_PROMPT'' WHERE ConversationTurnId=(SELECT TurnId FROM #Fixture)', 547;
EXEC #ExpectFailure N'evidence unknown turn', N'INSERT ethnowear.ConversationTurnEvidence(ConversationTurnId,EvidenceKey,EvidenceType,SnapshotJson) VALUES(-1,N''x'',N''DOCUMENT'',N''{}'')', 547;
EXEC #ExpectFailure N'no implicit history cascade', N'DELETE ethnowear.Conversations WHERE Id=(SELECT ConversationId FROM #Fixture)', 547;
DECLARE @turnId BIGINT=(SELECT TurnId FROM #Fixture);
DECLARE @oldVersion BINARY(8)=(SELECT RowVersion FROM ethnowear.ConversationTurns WHERE Id=@turnId);
UPDATE ethnowear.ConversationTurns SET Status=N'RUNNING',Stage=N'READING_ONTOLOGY',StartedAt=SYSUTCDATETIME() WHERE Id=@turnId;
IF @oldVersion=(SELECT RowVersion FROM ethnowear.ConversationTurns WHERE Id=@turnId)
    THROW 51003, 'ROWVERSION did not change', 1;
PRINT 'PASS: ROWVERSION changes on update';
UPDATE ethnowear.ConversationTurns SET Status=N'COMPLETED',IsActive=0,Stage=NULL,
    FinishedAt=SYSUTCDATETIME(),AnswerJson=N'{"answer":"Verified answer","sources":[]}' WHERE Id=@turnId;
INSERT ethnowear.ConversationTurnEvents(ConversationTurnId,EventId,Status) VALUES(@turnId,2,N'COMPLETED');
UPDATE ethnowear.ConversationTurns SET LastEventId=2 WHERE Id=@turnId;
PRINT 'PASS: completed answer and terminal SSE snapshot';
INSERT ethnowear.ConversationTurns(PublicId,ConversationId,ClientRequestId,TurnSequence,UserMessage,RequestHash,Stage)
SELECT NEWID(),ConversationId,NEWID(),2,N'Follow up',REPLICATE(N'b',64),N'RECEIVED' FROM #Fixture;
DECLARE @nextTurn BIGINT=SCOPE_IDENTITY();
UPDATE ethnowear.ConversationTurns SET Status=N'FAILED',IsActive=0,Stage=NULL,ErrorCode=N'GENERATION_TIMEOUT',FinishedAt=SYSUTCDATETIME()
WHERE Id=@nextTurn;
PRINT 'PASS: next turn after completion and safe failure before start';
INSERT ethnowear.ConversationTurns(PublicId,ConversationId,ClientRequestId,TurnSequence,UserMessage,RequestHash,Stage)
SELECT NEWID(),ConversationId,NEWID(),3,N'Follow up',REPLICATE(N'c',64),N'RECEIVED' FROM #Fixture;
UPDATE ethnowear.ConversationTurns SET Status=N'CANCELLED',IsActive=0,Stage=NULL,FinishedAt=SYSUTCDATETIME()
WHERE Id=SCOPE_IDENTITY();
PRINT 'PASS: next turn after failure and cancellation before start';
ROLLBACK TRANSACTION;
PRINT 'PASS: all fixtures rolled back';
