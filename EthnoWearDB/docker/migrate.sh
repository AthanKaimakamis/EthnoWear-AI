#!/usr/bin/env bash
set -euo pipefail

: "${MSSQL_SA_PASSWORD:?MSSQL_SA_PASSWORD is required}"

server="${ETHNOWEAR_DB_SERVER:-sqlserver,1433}"
database="${ETHNOWEAR_DB_NAME:-EthnoWear}"
demo_enabled="${ETHNOWEAR_DEMO_ENABLED:-false}"
demo_bacpac="${ETHNOWEAR_DEMO_BACPAC:-/app/demo/EthnoWear-demo-v1.bacpac}"

if [[ "${demo_enabled}" == "true" && -f "${demo_bacpac}" ]]; then
    if ! /tools/sqlpackage /Action:Export \
        "/SourceServerName:${server}" \
        "/SourceDatabaseName:${database}" \
        /SourceUser:sa \
        "/SourcePassword:${MSSQL_SA_PASSWORD}" \
        /SourceEncryptConnection:True \
        /SourceTrustServerCertificate:True \
        /TargetFile:/tmp/existing-database-probe.bacpac >/dev/null 2>&1; then
        echo "Importing EthnoWear demo database"
        /tools/sqlpackage /Action:Import \
            "/SourceFile:${demo_bacpac}" \
            "/TargetServerName:${server}" \
            "/TargetDatabaseName:${database}" \
            /TargetUser:sa \
            "/TargetPassword:${MSSQL_SA_PASSWORD}" \
            /TargetEncryptConnection:True \
            /TargetTrustServerCertificate:True
    fi
fi

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
