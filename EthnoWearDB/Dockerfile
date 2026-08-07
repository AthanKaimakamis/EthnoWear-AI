FROM mcr.microsoft.com/mssql/server:2022-latest

LABEL org.opencontainers.image.title="EthnoWear SQL Server"
LABEL org.opencontainers.image.description="Local Microsoft SQL Server 2022 instance for EthnoWear"

USER root
RUN mkdir -p /var/opt/mssql/data /var/opt/mssql/log /var/opt/mssql/secrets \
    && chown -R mssql:root /var/opt/mssql
USER mssql

EXPOSE 1433

