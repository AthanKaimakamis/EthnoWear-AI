#!/usr/bin/env bash
set -euo pipefail
root=$(cd "$(dirname "$0")/../.." && pwd)
container=${SQLSERVER_CONTAINER:-ethnowear-sqlserver-local}
{
    printf '%s\n' 'SET NOCOUNT ON; SET XACT_ABORT ON; SET ANSI_NULLS ON; SET QUOTED_IDENTIFIER ON;'
    printf '%s\n' 'SET ANSI_PADDING ON; SET ANSI_WARNINGS ON; SET CONCAT_NULL_YIELDS_NULL ON; SET ARITHABORT ON; SET NUMERIC_ROUNDABORT OFF;'
    printf '%s\n' 'BEGIN TRANSACTION;'
    printf '%s\n' "DECLARE @r INT; EXEC @r = sys.sp_getapplock @Resource=N'EthnoWear.ConversationSchema', @LockMode='Exclusive', @LockOwner='Transaction', @LockTimeout=30000; IF @r < 0 THROW 51000, 'Schema lock unavailable', 1;"
    for table in PublicUsers PublicUserIdentities PublicUserSessions PublicLoginChallenges; do
        printf "IF OBJECT_ID(N'ethnowear.%s', N'U') IS NULL BEGIN EXEC sys.sp_executesql N'\n" "$table"
        sed -e '/^[[:space:]]*GO[[:space:]]*$/d' -e "s/'/''/g" "$root/EthnoWearDB/Tables/$table.sql"
        printf "'; PRINT N'Created %s'; END;\n" "$table"
    done
    cat "$root/scripts/database/migrate-conversation-public-owner.sql"
    printf '\n%s\n' "COMMIT TRANSACTION; PRINT N'Public authentication schema applied';"
} | docker exec -i "$container" sh -c \
    '/opt/mssql-tools18/bin/sqlcmd -S localhost -U sa -P "$MSSQL_SA_PASSWORD" -C -d EthnoWear -b'
