#!/usr/bin/env bash
set -euo pipefail

: "${MSSQL_SA_PASSWORD:?MSSQL_SA_PASSWORD is required}"

server="${ETHNOWEAR_DB_SERVER:-sqlserver,1433}"
database="${ETHNOWEAR_DB_NAME:-EthnoWear}"

echo "Publishing EthnoWear database schema to ${server}/${database}"

/tools/sqlpackage \
    /Action:Publish \
    /SourceFile:/app/EthnoWearDB.dacpac \
    "/TargetServerName:${server}" \
    "/TargetDatabaseName:${database}" \
    /TargetUser:sa \
    "/TargetPassword:${MSSQL_SA_PASSWORD}" \
    /TargetEncryptConnection:True \
    /TargetTrustServerCertificate:True \
    /p:DropObjectsNotInSource=False \
    /p:BlockOnPossibleDataLoss=True

echo "EthnoWear database schema is ready"
