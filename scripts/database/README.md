# Conversation Schema

Run explicitly from the project root:

```sh
bash scripts/database/apply-conversation-schema.sh
bash scripts/database/apply-public-auth-schema.sh
docker exec -i ethnowear-sqlserver-local sh -c '/opt/mssql-tools18/bin/sqlcmd -S localhost -U sa -P "$MSSQL_SA_PASSWORD" -C -d EthnoWear -b' < scripts/database/test-conversation-schema.sql
python3 scripts/database/test-conversation-concurrency.py
```

Conversation deployment reads the five conversation definitions and their PublicUsers dependency from `EthnoWearDB/Tables`,
creates missing tables transactionally, and leaves existing tables/data untouched.
It is not a general schema migration or drift-repair tool. SQL Server 2022 is required.
Set `SQLSERVER_CONTAINER` to override the container for deployment/concurrency tests.

## Persistence Contract

- Internal keys are BIGINT; Spring generates public UUIDs and validates ownership.
- A conversation belongs to exactly one PublicUser or guest session, never a management User. Guest tokens
  are random secrets; persist only lowercase SHA-256 hashes, never the cookie token.
- Creation request IDs are unique per owner. Turn request IDs are unique per
  conversation. `RequestHash` is SHA-256 of exact UTF-8 `UserMessage` bytes.
  Spring must compare the original request before returning an idempotent response.
- Lock the conversation row while allocating the next `TurnSequence`. Its unique
  index enforces ordering identities. `IsActive` must change together with `Status`;
  the filtered unique index permits only one QUEUED/RUNNING turn per conversation.
- Lock the turn while allocating `EventId`, inserting its event, and updating
  `LastEventId` and the status snapshot in one transaction. Publish SSE after commit.
  Event `CreatedAt` maps to DTO `occurredAt`. Terminal turns have no stage.
- `AnswerJson` contains only a validated public answer, maximum 65,536 UTF-16 code
  units. It is present only on COMPLETED turns. FAILED turns carry a safe uppercase
  error code, never exception details. SQL validates JSON shape/size, not DTO fields.
- Private evidence snapshots are JSON objects, maximum 16,384 UTF-16 code units each.
  Store exact citation/page/ontology identities with a stable evidence key. These
  are historical snapshots, not current retrieval evidence; there are deliberately
  no foreign keys to mutable/deletable source documents. Never store secrets, raw
  model responses, hidden reasoning, or vectors in either JSON field.
- Events and evidence use append-only application mappings. No trigger enforces
  immutability or valid status transitions; lifecycle/ownership/retention safeguards
  belong to the next Spring service stage. All foreign keys use NO ACTION.
- Session expiry does not cascade-delete conversation history. Future bounded
  retention must explicitly delete evidence/events, turns, conversations, then sessions.
- Mutable tables have ROWVERSION for optimistic locking; timestamps use UTC.

The SQL constraint test rolls back its fixtures. The concurrent-session test commits
isolated fixtures to test visibility across connections, then deletes only those fixtures.
Neither test changes existing users, documents, chunks, or media.

## Public Google Accounts

`apply-public-auth-schema.sh` creates PublicUsers, PublicUserIdentities,
PublicUserSessions and PublicLoginChallenges, then performs the explicit
Conversations.OwnerUserId -> PublicUserId migration. It aborts if management-owned
conversations exist; it never treats management IDs as public IDs. Existing guest
history is preserved. All new FKs use NO ACTION. Repeated deployment is a no-op,
not a general drift-repair mechanism.

Google issuer/subject is a case-sensitive unique identity; email is profile data,
not an account-linking key. No password, role or management-user FK is added.
Sessions and one-use nonce challenges store only SHA-256 hashes. Logout revokes
one session; expiration/revocation does not delete its user's conversation history.

For bounded maintenance, an operator may explicitly prune expired login challenges
and expired/revoked public sessions in batches. These tables are authentication
artifacts, not conversation owners; deleting them does not delete public history.
No cleanup or schema mutation runs automatically at startup.
