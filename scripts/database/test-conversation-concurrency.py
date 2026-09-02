"""Live SQL Server unique-index test using two independent sessions."""

import os
import subprocess
import uuid


CONTAINER = os.environ.get("SQLSERVER_CONTAINER", "ethnowear-sqlserver-local")
COMMAND = [
    "docker", "exec", "-i", CONTAINER, "sh", "-c",
    '/opt/mssql-tools18/bin/sqlcmd -S localhost -U sa -P "$MSSQL_SA_PASSWORD" '
    '-C -d EthnoWear -b -h -1 -W',
]
SETTINGS = """
SET NOCOUNT ON; SET XACT_ABORT ON;
SET ANSI_NULLS ON; SET QUOTED_IDENTIFIER ON; SET ANSI_PADDING ON;
SET ANSI_WARNINGS ON; SET CONCAT_NULL_YIELDS_NULL ON;
SET ARITHABORT ON; SET NUMERIC_ROUNDABORT OFF;
SET LOCK_TIMEOUT 15000;
"""


def execute(sql):
    result = subprocess.run(COMMAND, input=SETTINGS + sql, text=True,
                            capture_output=True, timeout=30)
    if result.returncode:
        raise AssertionError(result.stdout + result.stderr)
    return result.stdout.strip()


marker = uuid.uuid4().hex
guest = None
holder = None
try:
    guest = int(execute(f"""
        INSERT ethnowear.ConversationGuestSessions(TokenHash,ExpiresAt)
        VALUES(N'{marker * 2}', DATEADD(DAY,1,SYSUTCDATETIME()));
        SELECT CONVERT(BIGINT,SCOPE_IDENTITY());
    """))
    conversation = int(execute(f"""
        INSERT ethnowear.Conversations(PublicId,GuestSessionId,ClientRequestId,Language)
        VALUES(NEWID(),{guest},NEWID(),N'bg');
        SELECT CONVERT(BIGINT,SCOPE_IDENTITY());
    """))

    def insert(sequence):
        return f"""
            INSERT ethnowear.ConversationTurns
                (PublicId,ConversationId,ClientRequestId,TurnSequence,UserMessage,RequestHash,Stage)
            VALUES(NEWID(),{conversation},NEWID(),{sequence},
                N'Isolated concurrency test',REPLICATE(N'a',64),N'RECEIVED');
        """

    holder = subprocess.Popen(COMMAND, stdin=subprocess.PIPE, stdout=subprocess.PIPE,
                              stderr=subprocess.STDOUT, text=True)
    holder.stdin.write(SETTINGS + "BEGIN TRANSACTION;" + insert(1) + """
        RAISERROR('ACTIVE_INSERTED',10,1) WITH NOWAIT;
        WAITFOR DELAY '00:00:04';
        COMMIT TRANSACTION;
    """)
    holder.stdin.close()
    for line in holder.stdout:
        if "ACTIVE_INSERTED" in line:
            break
    else:
        raise AssertionError("First session did not insert its active turn")

    contender = execute("""
        BEGIN TRY
            BEGIN TRANSACTION;
    """ + insert(2) + """
            COMMIT TRANSACTION;
            THROW 51004, 'Concurrent duplicate active turn was accepted', 1;
        END TRY
        BEGIN CATCH
            IF XACT_STATE() <> 0 ROLLBACK TRANSACTION;
            IF ERROR_NUMBER() <> 2601 THROW;
            IF ERROR_MESSAGE() NOT LIKE '%UQ_ConversationTurns_ActiveConversation%' THROW;
            PRINT 'PASS: concurrent active turn rejected by filtered unique index';
        END CATCH;
    """)
    remaining = holder.stdout.read()
    if holder.wait(timeout=10):
        raise AssertionError(remaining)
    print(contender)
    assert execute(f"SELECT COUNT_BIG(*) FROM ethnowear.ConversationTurns WHERE ConversationId={conversation};") == "1"
    print("PASS: exactly one committed active turn")

    execute(f"""
        BEGIN TRANSACTION;
        UPDATE ethnowear.ConversationTurns SET Status=N'CANCELLED',IsActive=0,
            Stage=NULL,FinishedAt=SYSUTCDATETIME() WHERE ConversationId={conversation};
    """ + insert(2) + "COMMIT TRANSACTION;")
    assert execute(f"SELECT COUNT_BIG(*) FROM ethnowear.ConversationTurns WHERE ConversationId={conversation};") == "2"
    assert execute(f"SELECT COUNT_BIG(*) FROM ethnowear.ConversationTurns WHERE ConversationId={conversation} AND IsActive=1;") == "1"
    print("PASS: terminal history retained and new active turn accepted")
finally:
    if holder is not None and holder.poll() is None:
        holder.wait(timeout=15)
    if guest is not None:
        execute(f"""
            BEGIN TRANSACTION;
            DELETE t FROM ethnowear.ConversationTurns t
                JOIN ethnowear.Conversations c ON c.Id=t.ConversationId WHERE c.GuestSessionId={guest};
            DELETE ethnowear.Conversations WHERE GuestSessionId={guest};
            DELETE ethnowear.ConversationGuestSessions WHERE Id={guest};
            COMMIT TRANSACTION;
        """)
        print("PASS: isolated concurrency fixtures removed")
