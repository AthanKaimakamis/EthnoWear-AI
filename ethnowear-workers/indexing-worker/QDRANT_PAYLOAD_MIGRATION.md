# Qdrant minimal-payload migration

The indexing worker stores exactly this payload:

```json
{"knowledgeChunkId": 123, "contentHash": "<64-character SHA-256>"}
```

SQL Server remains authoritative for text, language, provenance, source, archive,
ontology, approval, and indexing lifecycle state.

## Safe one-time cleanup

1. Stop the indexing worker so the collection cannot change during cleanup.
2. Keep SQL Server and permanent media unchanged.
3. Snapshot the collection only if rollback is required. The snapshot contains
   legacy text and must be protected and later deleted.
4. Delete and recreate only the configured EthnoWear chunk collection using its
   existing embedding dimension and cosine distance.
5. Through the existing Spring administration workflow, queue `INDEX_CHUNK`
   replacements for current, approved, trusted chunks only.
6. Start the updated worker and wait for each SQL/API completion handshake.
7. Sample Qdrant points and verify that each payload contains exactly
   `knowledgeChunkId` and `contentHash`, and that SQL indexing state agrees.
8. Delete the temporary snapshot under the project's retention rules.

Reindexing one existing point is also safe: synchronous upsert uses the stable
KnowledgeChunk ID and replaces the entire point, removing legacy payload keys.
A full rebuild is preferred because it also removes obsolete or ineligible points.
