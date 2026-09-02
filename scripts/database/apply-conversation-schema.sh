#!/usr/bin/env bash
set -euo pipefail

# Explicit manual deployment; never run from application startup.
root=$(cd "$(dirname "$0")/../.." && pwd)
container=${SQLSERVER_CONTAINER:-ethnowear-sqlserver-local}

{
    printf '%s\n' 'SET NOCOUNT ON; SET XACT_ABORT ON;'
    printf '%s\n' 'SET ANSI_NULLS ON; SET QUOTED_IDENTIFIER ON; SET ANSI_PADDING ON;'
    printf '%s\n' 'SET ANSI_WARNINGS ON; SET CONCAT_NULL_YIELDS_NULL ON; SET ARITHABORT ON; SET NUMERIC_ROUNDABORT OFF;'
    printf '%s\n' 'BEGIN TRANSACTION;'
    printf '%s\n' "DECLARE @lockResult INT; EXEC @lockResult = sys.sp_getapplock @Resource = N'EthnoWear.ConversationSchema', @LockMode = 'Exclusive', @LockOwner = 'Transaction', @LockTimeout = 30000;"
    printf '%s\n' "IF @lockResult < 0 THROW 51000, 'Cannot lock conversation schema deployment', 1;"
    for table in PublicUsers ConversationGuestSessions Conversations ConversationTurns ConversationTurnEvents ConversationTurnEvidence; do
        printf "IF OBJECT_ID(N'ethnowear.%s', N'U') IS NULL\nBEGIN\nEXEC sys.sp_executesql N'\n" "$table"
        sed -e '/^[[:space:]]*GO[[:space:]]*$/d' -e "s/'/''/g" "$root/EthnoWearDB/Tables/$table.sql"
        printf "';\nPRINT N'Created %s';\nEND\nELSE PRINT N'Already exists: %s (not modified)';\n" "$table" "$table"
    done
    printf '%s\n' 'COMMIT TRANSACTION;'
} | docker exec -i "$container" sh -c \
    '/opt/mssql-tools18/bin/sqlcmd -S localhost -U sa -P "$MSSQL_SA_PASSWORD" -C -d EthnoWear -b'
