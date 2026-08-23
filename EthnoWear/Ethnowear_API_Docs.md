# EthnoWear API

Professional API documentation for the EthnoWear cultural-heritage platform.

**API version:** `0.0.1`
**Default local URL:** `http://localhost:8080`
**Content type:** `application/json`, except multipart upload endpoints
**Authentication:** JWT bearer tokens with role-based access control

---

## Table of contents

1. [About the API](#about-the-api)
2. [Core concepts](#core-concepts)
3. [Getting started](#getting-started)
4. [Authentication](#authentication)
5. [Common conventions](#common-conventions)
6. [Public API](#public-api)
7. [Administrative user API](#administrative-user-api)
8. [Administrative archive API](#administrative-archive-api)
9. [Administrative ontology API](#administrative-ontology-api)
10. [Administrative document API](#administrative-document-api)
11. [Errors](#errors)
12. [Workflows](#workflows)
13. [Availability and implementation boundaries](#availability-and-implementation-boundaries)

---

## About the API

EthnoWear is an ontology-driven platform for exploring, documenting, and interpreting traditional Bulgarian embroidery and clothing.

The API supports:

- Browsing cultural concepts such as regions, ornaments, motifs, colors, techniques, and regional embroidery styles.
- Searching an ontology-backed catalogue.
- Viewing cultural concepts together with archive evidence, media, and citations.
- Running deterministic feature analysis through a JADE multi-agent workflow.
- Curating sources, citations, archive items, media, and ontology entities.
- Managing document ingestion, OCR review, provenance, quality, and processing jobs.
- Administering database-backed users, profiles, roles, credentials, and account state.

The API keeps cultural knowledge responsibilities explicit:

- The **ontology** defines concepts and semantic relationships.
- **SQL Server** stores sources, citations, concrete archive evidence, media metadata, documents, and reviewed text.
- The **filesystem** stores uploaded media and document binaries.
- **JADE agents** coordinate deterministic feature interpretation.

Generated LLM answers, vector retrieval, and automatic OCR execution are not currently public API capabilities.

## Core concepts

### Ontology concept

A reusable cultural concept identified by an ontology IRI and technical local name.

Supported concept types:

| API value | Meaning |
|---|---|
| `REGION` | Bulgarian ethnographic region. |
| `REGIONAL_EMBROIDERY` | Regional embroidery classification. |
| `MOTIF` | Cultural or visual motif. |
| `ORNAMENT` | Ornament or ornament element. |
| `TECHNIQUE` | Embroidery technique or stitch. |
| `COLOR` | Ontology-defined color. |

Path parameters also accept readable aliases such as `region`, `regions`, `ornament`, `techniques`, and `regional-embroideries`.

### Archive item

A concrete cultural record, object, image, sample, or documented example. Archive items may reference ontology concepts but do not redefine ontology truth.

### Source and source reference

- A **source** is a book, article, catalogue, website, or other evidence origin.
- A **source reference** identifies an exact chapter, page, figure, or section inside a source.

### Document and page

- A **document** is a PDF, scanned book, page-image set, standalone capture, or unknown fragment set.
- A **document page** is an individually processed and reviewed evidence unit.
- OCR output and corrected human-reviewed text are stored separately.

### Knowledge chunk

A source-backed internal text unit prepared for retrieval and indexing. It is not automatically a public article.

## Getting started

### Interactive documentation

When the application is running with its default Springdoc configuration:

- Swagger UI: `http://localhost:8080/swagger-ui/index.html`
- OpenAPI JSON: `http://localhost:8080/v3/api-docs`

Swagger is the authoritative live reference for generated request and response schemas.

### Health check through a public request

```bash
curl --request GET \
  'http://localhost:8080/api/reference/regions?language=bg'
```

### Response format

Successful JSON endpoints return one of:

- A single response object.
- A JSON array.
- A Spring pageable response.
- `204 No Content` for operations without a response body.

Binary media endpoints return a streamed resource or an HTTP redirect.

## Authentication

Public exploration endpoints require no authentication. Authenticated users can inspect their own identity and change their password. Administrative endpoints use the role matrix below; access is not limited to administrators alone.

### 1. Sign in

`POST /api/auth/login`

Compatibility alias: `POST /api/auth/admin/login`

Request:

```json
{
  "username": "admin",
  "password": "your-password"
}
```

Example:

```bash
curl --request POST \
  'http://localhost:8080/api/auth/login' \
  --header 'Content-Type: application/json' \
  --data '{
    "username": "admin",
    "password": "your-password"
  }'
```

Response:

```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
  "tokenType": "Bearer",
  "expiresIn": 900,
  "expiresAt": "2026-08-20T12:15:00Z",
  "passwordChangeRequired": false
}
```

Invalid credentials return `401 Unauthorized`. Five consecutive failed attempts lock an enabled account for 15 minutes.

### 2. Authorize requests

```http
Authorization: Bearer <accessToken>
```

Example:

```bash
curl --request GET \
  'http://localhost:8080/api/admin/auth' \
  --header 'Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...'
```

Successful verification returns `204 No Content`.

### 3. Inspect the current user

`GET /api/auth/me`

Requires any valid bearer token. Returns the authenticated user's identity, profile summary, roles, and password-change state.

```json
{
  "id": 7,
  "username": "reviewer.maria",
  "firstName": "Maria",
  "lastName": "Ivanova",
  "email": "maria@example.org",
  "roles": ["REVIEWER"],
  "passwordChangeRequired": false
}
```

### 4. Refresh an active session

`POST /api/auth/refresh`

Requires a valid, unexpired bearer token. Returns a replacement token with the same response shape as login. It does not accept credentials or refresh expired sessions.

### 5. Change the current password

`POST /api/auth/password/change`

Requires any valid bearer token. A successful change returns `204 No Content` and invalidates previously issued tokens.

```json
{
  "currentPassword": "current-password",
  "newPassword": "Stronger-Passphrase-2026!"
}
```

The new password must:

- Contain 12 to 128 characters.
- Include uppercase, lowercase, numeric, and symbol characters.
- Not match the username, ignoring letter case.

### Token behavior

- Tokens are signed with HS256.
- The default token lifetime is 15 minutes.
- The API is stateless and does not create server-side login sessions.
- Expired or invalid tokens must be replaced by signing in again.
- Account disablement, password reset, password change, role changes, and administrative unlocking revoke existing tokens.
- Temporary passwords expire after 24 hours and require a password change after sign-in.
- Refresh tokens are not currently implemented.

### Role-based access control

Roles grant different administrative capabilities:

| Capability | `ADMINISTRATOR` | `REVIEWER` | `EDITOR` |
|---|:---:|:---:|:---:|
| Read administrative resources | Yes | Yes | Yes |
| Manage users, roles, credentials, and account state | Yes | No | No |
| Create or edit ontology, archive, media, and document resources | Yes | No | Yes |
| Publish, return, or archive an archive item | Yes | Yes | No |
| Approve or reject a document transcription | Yes | Yes | No |
| Change provenance trust or canonical-page relationships | Yes | Yes | No |

Important details:

- All authenticated roles may issue `GET` requests under `/api/admin/**`, including `GET /api/admin/auth`.
- `REVIEWER` write access is intentionally limited to the review and publication decisions listed above.
- `EDITOR` handles ordinary creation, editing, ingestion, processing, and deletion operations, but cannot perform reviewer-only decisions.
- `ADMINISTRATOR` has the combined permissions and is the only role allowed under `/api/admin/users/**`.
- Unrecognized administrative paths are denied by default.
- A valid token can still receive `403 Forbidden` when its roles do not authorize the requested method and path.

## Common conventions

### Language

Endpoints that expose localized ontology content commonly accept:

```text
language=bg
```

Bulgarian (`bg`) is the default where specified. English labels are returned when requested and available.

### Pagination

Pageable endpoints use standard Spring parameters:

| Parameter | Meaning | Example |
|---|---|---|
| `page` | Zero-based page number. | `page=0` |
| `size` | Items per page. | `size=20` |
| `sort` | Field and optional direction. | `sort=title,asc` |

Example:

```text
GET /api/admin/sources?page=0&size=20&sort=title,asc
```

A pageable response contains fields such as `content`, `number`, `size`, `totalElements`, and `totalPages`.

### Identifiers

- Relational resources use numeric IDs.
- Ontology resources use stable technical local names and IRIs.
- Do not treat a display label as a stable identifier.

### Date and time

Timestamps use ISO 8601 values, normally in UTC.

### Enum values

Enum values are uppercase unless an endpoint documents a path alias.

### Validation

Invalid request bodies return `400 Bad Request` with field-level details when available.

## Public API

### Reference data

Base path: `/api/reference`

These endpoints return lightweight ontology-backed options for forms and filters.

| Method | Path | Description |
|---|---|---|
| `GET` | `/full` | All reference collections in one response. |
| `GET` | `/regions` | Regions. |
| `GET` | `/region-groups` | Region groups. |
| `GET` | `/ornaments` | Ornaments. |
| `GET` | `/colors` | Colors. |
| `GET` | `/techniques` | Techniques. |
| `GET` | `/motifs` | Motifs. |
| `GET` | `/regional-embroidery-types` | Regional embroidery classifications. |

Common query parameter:

| Name | Required | Default | Description |
|---|---:|---:|---|
| `language` | No | `bg` | Requested label language. |

Example:

```bash
curl 'http://localhost:8080/api/reference/techniques?language=en'
```

### Catalogue search

`POST /api/catalogue/search`

Searches ontology concepts and returns cards, facets, and page metadata.

Request fields:

| Field | Type | Description |
|---|---|---|
| `entityType` | enum | Concept type to search. |
| `language` | string | Requested language. |
| `searchText` | string | Free-text label/name search. |
| `categoryLocalNames` | string array | Category filters. |
| `relatedEntityLocalNames` | map | Filters by related ontology entities. |
| `relatedCategoryLocalNames` | map | Filters by related categories. |
| `combinationMode` | `AND` or `OR` | How filters are combined. |

Example:

```bash
curl --request POST \
  'http://localhost:8080/api/catalogue/search?page=0&size=24&sort=label,asc' \
  --header 'Content-Type: application/json' \
  --data '{
    "entityType": "ORNAMENT",
    "language": "bg",
    "searchText": "ромб",
    "categoryLocalNames": [],
    "relatedEntityLocalNames": {
      "REGION": ["ShoplukRegion"]
    },
    "relatedCategoryLocalNames": {},
    "combinationMode": "AND"
  }'
```

### Concept details

`GET /api/catalogue/{entityType}/{localName}`

Returns a combined detail response containing:

- Ontology identity and localized labels.
- Short ontology description.
- Related concepts.
- Curated public content when available.
- Published archive evidence.
- Media and exact citations when available.

Query parameters:

| Name | Default | Description |
|---|---:|---|
| `language` | `bg` | Display language. |
| `page` | `0` | Archive-evidence page. |
| `size` | `12` | Archive evidence per page. |
| `sort` | `id,desc` | Evidence sort. |

Example:

```bash
curl 'http://localhost:8080/api/catalogue/ornament/RhombusOrnament?language=bg'
```

### Regional embroidery archive

`GET /api/archive/regional-embroideries`

Returns published archive records grouped by regional embroidery classification.

| Parameter | Default | Description |
|---|---:|---|
| `language` | `bg` | Display language. |
| `previewSize` | `4` | Preview records per section. |

```bash
curl 'http://localhost:8080/api/archive/regional-embroideries?language=bg&previewSize=4'
```

### Archive item details

`GET /api/archive/items/{id}`

Returns one published archive item with its classifications, observed features, media, annotations, knowledge, and citations.

```bash
curl 'http://localhost:8080/api/archive/items/42'
```

### Media delivery

Preferred endpoint:

`GET /api/media/{mediaId}/content`

Compatibility endpoint:

`GET /api/archive/media/{mediaId}`

Behavior:

- Local files are streamed inline with their MIME type and filename.
- Externally hosted media produces an HTTP redirect.
- Missing media returns `404 Not Found`.

```html
<img src="http://localhost:8080/api/media/25/content" alt="Archive media">
```

### Feature analysis

`POST /api/analyze`

Runs deterministic, ontology-backed analysis through the JADE agent workflow.

Request fields:

| Field | Type | Notes |
|---|---|---|
| `ornaments` | string array | Maximum 20. |
| `colors` | string array | Maximum 20. |
| `techniques` | string array | Maximum 20. |
| `motif` | string | Optional motif hint. |
| `region` | string | Optional region hint. |
| `regionalEmbroidery` | string | Optional regional embroidery hint. |
| `language` | string | Requested explanation language. |

Example:

```bash
curl --request POST \
  'http://localhost:8080/api/analyze' \
  --header 'Content-Type: application/json' \
  --data '{
    "ornaments": ["RhombusOrnament"],
    "colors": ["RedColor", "BlackColor"],
    "techniques": ["CrossTechnique"],
    "motif": null,
    "region": null,
    "regionalEmbroidery": null,
    "language": "bg"
  }'
```

The response contains selected features, ranked candidates, weighted evidence, and a deterministic explanation. This endpoint does not perform image recognition.

## Administrative user API

Base path: `/api/admin/users`

Every endpoint in this section requires an `ADMINISTRATOR` bearer token. Supported roles are `ADMINISTRATOR`, `REVIEWER`, and `EDITOR`.

### List users

`GET /api/admin/users`

Returns a pageable user summary. Use the optional `search` parameter to search user records; standard `page`, `size`, and `sort` parameters are also supported.

```text
GET /api/admin/users?search=maria&page=0&size=20&sort=username,asc
```

Each summary includes `id`, `username`, `firstName`, `lastName`, `email`, `enabled`, `passwordChangeRequired`, and `roles`.

### Get a user

`GET /api/admin/users/{userId}`

Returns the account, profile, roles, status, credential state, lock expiry, last login, and audit timestamps.

### Create a user

`POST /api/admin/users`

Creates an enabled user with a one-time temporary password. Returns `201 Created`, a `Location` header, the new user, and credentials that must be delivered securely.

```json
{
  "username": "reviewer.maria",
  "profile": {
    "firstName": "Maria",
    "lastName": "Ivanova",
    "email": "maria@example.org",
    "phone": "+359000000000",
    "addressLine1": null,
    "addressLine2": null,
    "city": "Plovdiv",
    "postalCode": "4000",
    "countryCode": "BG"
  },
  "roles": ["REVIEWER"]
}
```

```json
{
  "user": {
    "id": 7,
    "username": "reviewer.maria",
    "profile": {
      "firstName": "Maria",
      "lastName": "Ivanova",
      "email": "maria@example.org"
    },
    "roles": ["REVIEWER"],
    "enabled": true,
    "passwordChangeRequired": true,
    "temporaryPasswordExpiresAt": "2026-08-21T12:00:00"
  },
  "credentials": {
    "temporaryPassword": "generated-one-time-password",
    "expiresAt": "2026-08-21T12:00:00"
  }
}
```

Usernames must contain 3 to 100 characters. First and last names are required. Email addresses, when supplied, must be valid and unique. `countryCode`, when supplied, must contain exactly two letters.

> Treat `credentials.temporaryPassword` as sensitive. It is returned in plaintext only by user creation and password-reset operations.

### Update a profile

`PUT /api/admin/users/{userId}/profile`

Replaces the editable profile fields using the same `profile` object shape shown above and returns the updated user.

### Assign or remove a role

| Method | Path | Result |
|---|---|---|
| `PUT` | `/api/admin/users/{userId}/roles/{role}` | Assigns the role and returns the updated user. Reassigning an existing role is safe. |
| `DELETE` | `/api/admin/users/{userId}/roles/{role}` | Removes the role and returns the updated user. |

Role changes invalidate existing tokens. An enabled user must retain at least one role, and the final enabled administrator cannot lose `ADMINISTRATOR`.

### Manage account state

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/api/admin/users/{userId}/enable` | Enables an eligible account and returns the user. |
| `POST` | `/api/admin/users/{userId}/disable` | Disables the account and returns the user. |
| `POST` | `/api/admin/users/{userId}/unlock` | Clears a login lock and failed-attempt state. |
| `POST` | `/api/admin/users/{userId}/reset-password` | Issues a new 24-hour temporary password. |

Enabling requires a password, at least one role, and an unexpired temporary password when a password change is pending. The final enabled administrator cannot be disabled.

Password reset returns:

```json
{
  "temporaryPassword": "generated-one-time-password",
  "expiresAt": "2026-08-21T12:00:00"
}
```

## Administrative archive API

All authenticated roles may read these resources. `ADMINISTRATOR` and `EDITOR` may perform ordinary mutations. Archive publication decisions follow the reviewer rules in the [role-based access-control matrix](#role-based-access-control).

### Standard CRUD resources

The following resources share a common contract:

| Base path | Resource |
|---|---|
| `/api/admin/sources` | Source metadata. |
| `/api/admin/source-references` | Exact citations. |
| `/api/admin/archive-items` | Concrete archive records. |
| `/api/admin/archive-item-features` | Observed ontology-linked features. |
| `/api/admin/archive-item-media` | Archive item/media assignments. |
| `/api/admin/media-entity-links` | Direct media/ontology links. |
| `/api/admin/media-feature-annotations` | Visible feature annotations on media. |
| `/api/admin/knowledge-chunks` | Source-backed retrieval text units. |

Common operations:

| Method | Path | Result |
|---|---|---|
| `GET` | `{basePath}` | Pageable list. |
| `GET` | `{basePath}/{id}` | One resource. |
| `POST` | `{basePath}` | Creates a resource; returns `201 Created`. |
| `PUT` | `{basePath}/{id}` | Updates a resource. |
| `DELETE` | `{basePath}/{id}` | Deletes an unused resource; returns `204`. |

Deletion is dependency-aware. A resource that is still referenced returns `409 Conflict`.

Use the OpenAPI schema for the exact write DTO of each resource.

### Media assets

Base path: `/api/admin/media-assets`

| Method | Path | Description |
|---|---|---|
| `GET` | `/api/admin/media-assets` | Pageable media list. |
| `GET` | `/api/admin/media-assets/{id}` | Media metadata. |
| `POST` | `/api/admin/media-assets/upload` | Multipart file upload. |
| `PATCH` | `/api/admin/media-assets/{id}` | Editable metadata update. |
| `DELETE` | `/api/admin/media-assets/{id}` | Deletes an unreferenced asset and managed files. |

Multipart upload parts:

- `file`: Binary media.
- `metadata`: JSON matching `MediaUploadRequest`.

Metadata example:

```json
{
  "sourceReferenceId": 12,
  "mediaType": "PHOTO",
  "category": "archive-primary",
  "description": "Front view of the embroidered sleeve"
}
```

```bash
curl --request POST \
  'http://localhost:8080/api/admin/media-assets/upload' \
  --header 'Authorization: Bearer <accessToken>' \
  --form 'file=@/absolute/path/example.jpg;type=image/jpeg' \
  --form 'metadata={"sourceReferenceId":12,"mediaType":"PHOTO","category":"archive-primary","description":"Front view"};type=application/json'
```

Default accepted content types:

- `image/jpeg`
- `image/png`
- `image/gif`
- `image/webp`
- `application/pdf`

The default maximum file size is 25 MB.

### Archive publication workflow

Base path: `/api/admin/archive-items/{archiveItemId}`

| Method | Relative path | Required role | Description |
|---|---|---|---|
| `GET` | `/publication-readiness` | Any authenticated role | Evaluates required data without changing state. |
| `POST` | `/submit` | `ADMINISTRATOR` or `EDITOR` | Submits a draft for review. |
| `POST` | `/publish` | `ADMINISTRATOR` or `REVIEWER` | Publishes a ready item. |
| `POST` | `/return-to-draft` | `ADMINISTRATOR` or `REVIEWER` | Returns an item to editable draft state. |
| `POST` | `/archive` | `ADMINISTRATOR` or `REVIEWER` | Archives the item. |

Invalid transitions or missing publication requirements return `409 Conflict`.

## Administrative ontology API

All authenticated roles may read ontology administration endpoints. Creating, updating, linking, unlinking, and deleting ontology entities requires `ADMINISTRATOR` or `EDITOR`.

Ontology administration writes directly to the configured OWL model. Use stable English local names and localized labels.

### Regions, motifs, and regional embroideries

Base path: `/api/admin/ontology/{entityType}`

Allowed `entityType` values:

- `regions`
- `motifs`
- `regional-embroideries`

| Method | Path pattern | Description |
|---|---|---|
| `GET` | `/{entityType}` | Lists entities. |
| `GET` | `/{entityType}/{localName}` | Gets one entity. |
| `POST` | `/{entityType}` | Creates an entity. |
| `PUT` | `/{entityType}/{localName}` | Updates an entity. |
| `DELETE` | `/{entityType}/{localName}` | Deletes an unused entity. |

### Ornaments

Base path: `/api/admin/ontology/ornaments`

| Method | Relative path | Description |
|---|---|---|
| `GET` | `/` | Lists ornaments. |
| `GET` | `/{localName}` | Gets an ornament. |
| `POST` | `/` | Creates an ornament. |
| `PUT` | `/{localName}` | Updates an ornament. |
| `PUT` | `/{localName}/characteristic-regions/{regionLocalName}` | Adds its canonical region relationship. |
| `DELETE` | `/{localName}/characteristic-regions/{regionLocalName}` | Removes its region relationship. |
| `DELETE` | `/{localName}` | Deletes an unused ornament. |

### Techniques

Base path: `/api/admin/ontology/techniques`

The technique API exposes the same operations as the ornament API, including characteristic-region relationship management.

Ontology operations reject:

- Invalid local names or relationships with `400`.
- Missing ontology resources with `404`.
- Duplicates and referenced-resource deletion with `409`.

## Administrative document API

All authenticated roles may inspect document administration resources. Ingestion, metadata changes, OCR import, transcription editing, processing requests, source-provenance changes, job retry, and cancellation require `ADMINISTRATOR` or `EDITOR`. Approval, rejection, trust changes, and canonical-page decisions require `ADMINISTRATOR` or `REVIEWER`.

The document API manages evidence from upload through human review and job scheduling.

### Upload a PDF

`POST /api/admin/documents/upload/pdf`

Multipart parts:

- `command`: JSON upload command.
- `file`: PDF binary.

Command example:

```json
{
  "metadata": {
    "sourceId": 8,
    "title": "Българска везбена орнаментика",
    "author": "Author name",
    "publisher": "Publisher name",
    "publicationYear": 1950,
    "language": "bg",
    "notes": "Digitized reference copy"
  },
  "documentType": "SCANNED_BOOK",
  "provenanceStatus": "KNOWN_SOURCE",
  "provenanceTrustState": "TRUSTED",
  "mediaDescription": "Complete scanned book"
}
```

```bash
curl --request POST \
  'http://localhost:8080/api/admin/documents/upload/pdf' \
  --header 'Authorization: Bearer <accessToken>' \
  --form 'command={"metadata":{"sourceId":8,"title":"Българска везбена орнаментика","author":"Author name","publisher":"Publisher name","publicationYear":1950,"language":"bg","notes":"Digitized reference copy"},"documentType":"SCANNED_BOOK","provenanceStatus":"KNOWN_SOURCE","provenanceTrustState":"TRUSTED","mediaDescription":"Complete scanned book"};type=application/json' \
  --form 'file=@/absolute/path/book.pdf;type=application/pdf'
```

Returns `201 Created`. Page extraction is queued for asynchronous processing.

### Upload a standalone capture

`POST /api/admin/documents/upload/standalone-capture`

Use this endpoint for a photographed page, loose scan, or unknown fragment. The command records explicit source and trust state so uncertain provenance is not hidden.

Returns `201 Created`.

### Add or replace page media

| Method | Path | Description |
|---|---|---|
| `POST` | `/api/admin/documents/{documentId}/pages/missing` | Adds a separately supplied missing page. |
| `POST` | `/api/admin/documents/{documentId}/pages/{pageId}/renditions/replacement` | Adds a replacement rendition while preserving the prior file. |

Both endpoints use multipart `command` and `file` parts and return `201 Created`.

### List and inspect documents

| Method | Path | Description |
|---|---|---|
| `GET` | `/api/admin/documents` | Filtered, pageable document list. |
| `GET` | `/api/admin/documents/{documentId}` | Complete document details. |
| `GET` | `/api/admin/documents/{documentId}/progress` | Processing and review progress. |
| `GET` | `/api/admin/documents/{documentId}/indexing-status` | Chunk/indexing eligibility and counts. |
| `GET` | `/api/admin/documents/{documentId}/jobs` | Document job history. |
| `PUT` | `/api/admin/documents/{documentId}/metadata` | Updates metadata; returns `204`. |

Document list filters:

- `searchText`
- `documentType`
- `provenanceStatus`
- `provenanceTrustState`
- `processingState`
- `reviewState`
- `indexingState`
- `language`
- `sourceId`
- Standard pagination parameters

### List and inspect pages

Base path: `/api/admin/documents/{documentId}/pages`

| Method | Relative path | Description |
|---|---|---|
| `GET` | `/` | Pageable page list. |
| `GET` | `/{pageId}` | Complete page details. |
| `GET` | `/{pageId}/ocr/current` | Current OCR result. |
| `GET` | `/{pageId}/ocr/history` | OCR history. |
| `GET` | `/{pageId}/reviews` | Review history. |
| `GET` | `/{pageId}/provenance` | Provenance history. |
| `GET` | `/{pageId}/jobs` | Page job history. |
| `GET` | `/{pageId}/quality/current` | Current quality assessments. |
| `GET` | `/{pageId}/quality/history` | Quality-assessment history. |

Histories are append-only and pageable where appropriate.

### Import an OCR result manually

`POST /api/admin/document-pages/{pageId}/ocr-results`

Imports a manually produced OCR result into the normal review lifecycle. Returns `201 Created`.

The exact `ManualOcrImportCommand` schema is available in OpenAPI.

### Correct and review transcription

Base path: `/api/admin/document-pages/{pageId}`

#### Save corrected text

`PATCH /transcription`

Requires `ADMINISTRATOR` or `EDITOR`.

```json
{
  "correctedText": "Провереният и коригиран текст на страницата..."
}
```

#### Approve transcription

`POST /approve`

Requires `ADMINISTRATOR` or `REVIEWER`.

```json
{
  "notes": "Compared with the visible scan."
}
```

#### Reject transcription

`POST /reject`

Requires `ADMINISTRATOR` or `REVIEWER`.

```json
{
  "reason": "The OCR omits the second text column."
}
```

Approval is the trust boundary for downstream chunk generation. Raw OCR alone is not trusted evidence.

### Manage page provenance

Base path: `/api/admin/document-pages/{pageId}/provenance-events`

| Method | Relative path | Required role | Description |
|---|---|---|---|
| `POST` | `/source-change` | `ADMINISTRATOR` or `EDITOR` | Changes or identifies the source reference. |
| `POST` | `/trust-change` | `ADMINISTRATOR` or `REVIEWER` | Changes provenance trust state. |
| `POST` | `/canonical-link` | `ADMINISTRATOR` or `REVIEWER` | Links the evidence unit to a canonical page. |
| `POST` | `/canonical-merge` | `ADMINISTRATOR` or `REVIEWER` | Merges it into a canonical page. |
| `POST` | `/canonical-link-reversal` | `ADMINISTRATOR` or `REVIEWER` | Reverses a canonical relationship with a reason. |

Every change creates an auditable event associated with the authenticated user. Original evidence history is preserved.

### Request processing

| Method | Path | Description |
|---|---|---|
| `POST` | `/api/admin/document-pages/{pageId}/ocr` | Queues OCR. |
| `POST` | `/api/admin/document-pages/{pageId}/reprocess` | Queues a new OCR pass. |
| `POST` | `/api/admin/document-pages/{pageId}/quality-assessment` | Queues a quality assessment. |
| `POST` | `/api/admin/documents/{documentId}/chunk-generation` | Queues chunk generation for eligible pages. |

Successful requests return `202 Accepted` with the queued job.

The API records durable jobs; an external worker is responsible for executing automatic OCR work.

### Retry or cancel a job

Base path: `/api/admin/document-processing-jobs/{jobId}`

| Method | Relative path | Description |
|---|---|---|
| `POST` | `/retry` | Retries an eligible job; returns `202 Accepted`. |
| `POST` | `/cancel` | Cancels or requests cancellation. |

Cancellation body:

```json
{
  "reason": "The wrong page rendition was selected."
}
```

Invalid job transitions return `409 Conflict`.

## Errors

### Standard error object

```json
{
  "status": 404,
  "error": "Not Found",
  "message": "Document 999 was not found"
}
```

Validation errors may include a `fields` object:

```json
{
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed",
  "fields": {
    "title": "must not be blank"
  }
}
```

### Status codes

| Status | Meaning |
|---:|---|
| `200 OK` | Successful read or update with a response body. |
| `201 Created` | Resource or uploaded evidence created. |
| `202 Accepted` | Asynchronous processing job accepted. |
| `204 No Content` | Successful operation without a response body. |
| `302 Found` | Media redirects to an external URI. |
| `400 Bad Request` | Invalid JSON, parameter, enum, or validation constraint. |
| `401 Unauthorized` | Invalid credentials or missing/invalid/expired token. |
| `403 Forbidden` | Token does not carry the required role. |
| `404 Not Found` | Resource does not exist or is unavailable through that route. |
| `409 Conflict` | Duplicate, referenced resource, illegal workflow transition, or active duplicate job. |
| `422 Unprocessable Entity` | Valid document request whose evidence is not eligible for the requested processing operation. |

Conflict errors can include extra fields such as publication status, failed requirements, or referencing ontology resources.

## Workflows

### Publish an archive item

```text
Create source
  -> create exact source reference
  -> create archive item
  -> add observed features
  -> upload and attach media
  -> check publication readiness
  -> submit
  -> publish
```

### Review a scanned document

```text
Upload PDF or standalone capture
  -> page extraction/OCR job
  -> inspect current OCR and quality
  -> save corrected transcription
  -> approve or reject
  -> request chunk generation
  -> inspect indexing status
```

### Preserve uncertain provenance

```text
Upload standalone capture as unknown/partial source
  -> review transcription independently
  -> later identify source
  -> link or merge with canonical page
  -> retain original media and provenance events
```

### Provision a staff account

```text
Create user with the least-privileged role
  -> deliver the temporary password through a secure channel
  -> user signs in within 24 hours
  -> user replaces the temporary password
  -> administrator adjusts roles or account state when responsibilities change
```

## Availability and implementation boundaries

### Available

- Public reference, catalogue, details, archive, media, and feature-analysis APIs.
- Protected archive, source, media, knowledge-chunk, and ontology administration.
- Protected document upload, query, OCR import, review, provenance, and job-management APIs.
- Database-backed user, profile, role, password, lock, and account-state administration.
- JWT bearer authentication with `ADMINISTRATOR`, `REVIEWER`, and `EDITOR` roles.
- OpenAPI and Swagger UI.

### Not currently available

- Public conversation/chat endpoint.
- Ollama answer generation or embeddings.
- Qdrant vector retrieval.
- Automatic OCR worker inside this repository.
- Refresh tokens or persistent login sessions.
- Automatic image-feature recognition.

### Reliability rules

- Public browsing does not depend on AI services.
- Ontology relationships remain the semantic source of truth.
- SQL remains authoritative for archive evidence, reviewed text, chunks, and citations.
- Media database records contain safe relative storage keys rather than host paths.
- Deletes are dependency-aware.
- Raw OCR is not considered trusted evidence.
- Only corrected and approved page text is eligible for trusted book-excerpt chunks.

---

## Support and maintenance

- Use Swagger UI for exact live schemas and enum values.
- Report documentation mismatches together with the endpoint, request, response, and API version.
- Keep this document synchronized whenever controllers, security rules, status codes, or request contracts change.
