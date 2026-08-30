# EthnoWear API

Spring Boot backend for the EthnoWear cultural-heritage platform. It provides
public exploration APIs and protected workflows for ontology, archive, media,
document, OCR, provenance, review, and user administration.

## API documentation

**[Read the complete EthnoWear API documentation](Ethnowear_API_Docs.md)**

The guide includes:

- Public and administrative endpoints.
- JWT authentication and role-based permissions.
- Request and response examples.
- Upload, OCR, review, provenance, and publication workflows.
- Validation rules, status codes, and error responses.

For live schemas while the application is running, open:

- Swagger UI: `http://localhost:8080/swagger-ui/index.html`
- OpenAPI JSON: `http://localhost:8080/v3/api-docs`

## Run locally

From the repository root, start the complete platform:

```bash
docker compose up -d --build
```

Or run the API with Maven from this directory:

```bash
mvn spring-boot:run
```

The API is available at `http://localhost:8080` by default. Environment and
database setup instructions are maintained in the
[repository README](../README.md).
