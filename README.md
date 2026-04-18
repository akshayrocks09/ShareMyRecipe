# Recipe Publishing Platform

A production-ready recipe publishing platform built with Java 21 + Spring Boot 3.2.
Two deployable applications: a REST API and an async worker, connected via RabbitMQ.

---

## Architecture

```
Clients  →  API Gateway (Spring Security + JWT)
               ├── Auth Service       → PostgreSQL
               ├── Recipe Service     → PostgreSQL + RabbitMQ (publish events)
               └── Follow Service     → PostgreSQL

RabbitMQ queues  →  Recipe Worker (separate Spring Boot app)
                         ├── Download raw images from S3
                         ├── Resize  →  thumbnail (200×200) + medium (800×600)
                         ├── Upload processed images to S3/MinIO
                         ├── Write CDN URLs back to PostgreSQL
                         └── Upsert recipe document in Elasticsearch
```

### Module layout

```
recipe-platform/
├── pom.xml                    (parent POM, multi-module)
├── docker-compose.yml
├── recipe-api/                (Spring Boot API — port 8080)
│   ├── Dockerfile
│   └── src/main/java/com/recipeplatform/api/
│       ├── config/            SecurityConfig, RabbitMQConfig, S3Config, ElasticsearchConfig
│       ├── controller/        AuthController, RecipeController, ChefController, FeedController
│       ├── dto/               request/*, response/*, RecipeEventMessage
│       ├── entity/            User, ChefProfile, Recipe, RecipeImage, RefreshToken
│       ├── exception/         ApiException, GlobalExceptionHandler
│       ├── repository/        JPA + Elasticsearch repositories
│       ├── security/          JwtTokenProvider, JwtAuthenticationFilter
│       └── service/           AuthService, RecipeService, ChefService, StorageService
└── recipe-worker/             (Spring Boot Worker — port 8081)
    ├── Dockerfile
    └── src/main/java/com/recipeplatform/worker/
        ├── config/            RabbitMQConfig, S3Config, ElasticsearchConfig
        ├── consumer/          RecipeEventConsumer (3 queues, manual ACK)
        ├── dto/               RecipeEventMessage, RecipeDocument
        └── service/           RecipeIngestionService, ImageProcessingService,
                               SearchIndexService, WorkerDatabaseService
```

---

## Prerequisites

| Tool | Version |
|------|---------|
| Java | 21+ |
| Maven | 3.9+ |
| Docker + Docker Compose | 24+ |

---

## Quick Start (Docker Compose)

```bash
# 1. Clone / extract the project
cd recipe-platform

# 2. Start all infrastructure + both apps
docker compose up --build

# 3. API is available at
curl http://localhost:8080/actuator/health
```

### Service ports

| Service | Port | Notes |
|---------|------|-------|
| recipe-api | 8080 | REST API |
| recipe-worker | 8081 | Worker (actuator only) |
| PostgreSQL | 5432 | |
| RabbitMQ | 5672 / 15672 | 15672 = Management UI |
| Elasticsearch | 9200 | |
| MinIO | 9000 / 9001 | 9001 = Console |

---

## Running Locally (without Docker for apps)

```bash
# Start infrastructure only
docker compose up postgres rabbitmq elasticsearch minio minio-init -d

# Build both modules
mvn clean install -DskipTests

# Run API
cd recipe-api
mvn spring-boot:run

# Run Worker (separate terminal)
cd recipe-worker
mvn spring-boot:run
```

---

## Environment Variables

All variables have sensible defaults for local development.

### recipe-api

| Variable | Default | Description |
|----------|---------|-------------|
| `DB_HOST` | `localhost` | PostgreSQL host |
| `DB_PORT` | `5432` | PostgreSQL port |
| `DB_NAME` | `recipe_platform` | Database name |
| `DB_USER` | `postgres` | Database user |
| `DB_PASSWORD` | `postgres` | Database password |
| `RABBITMQ_HOST` | `localhost` | RabbitMQ host |
| `RABBITMQ_USER` | `guest` | RabbitMQ username |
| `RABBITMQ_PASSWORD` | `guest` | RabbitMQ password |
| `ES_HOST` | `localhost` | Elasticsearch host |
| `ES_PORT` | `9200` | Elasticsearch port |
| `JWT_SECRET` | *(dev value)* | **Must be changed in prod** (≥32 chars) |
| `S3_ACCESS_KEY` | `minioadmin` | S3 / MinIO access key |
| `S3_SECRET_KEY` | `minioadmin` | S3 / MinIO secret key |
| `S3_BUCKET` | `recipe-platform` | Bucket name |
| `S3_ENDPOINT` | `http://localhost:9000` | Remove for real AWS |
| `CDN_BASE_URL` | `http://localhost:9000/recipe-platform` | CDN prefix for image URLs |
| `EMAIL_VERIFICATION` | `false` | Toggle email verification |

### recipe-worker

Same database, RabbitMQ, Elasticsearch, and S3 variables plus:

| Variable | Default | Description |
|----------|---------|-------------|
| `WORKER_CONCURRENCY` | `3` | Concurrent RabbitMQ consumers |

---

## API Reference

### Authentication

#### Sign up — `POST /v1/auth/signup`
```json
{
  "email": "chef@example.com",
  "password": "password123",
  "handle": "gordonthecook",
  "displayName": "Gordon The Cook",
  "bio": "I love pasta"
}
```
Returns `201` with `{ accessToken, refreshToken, tokenType, expiresInSeconds, chef }`.

#### Login — `POST /v1/auth/login`
```json
{ "email": "chef@example.com", "password": "password123" }
```

#### Refresh token — `POST /v1/auth/refresh`
```json
{ "refreshToken": "<refresh-token>" }
```

---

### Recipes (Public)

#### List recipes — `GET /v1/recipes`

Query parameters:

| Param | Type | Description |
|-------|------|-------------|
| `q` | string | Full-text search (title, summary, ingredients, steps) |
| `published_from` | ISO datetime | Filter by publish date start |
| `published_to` | ISO datetime | Filter by publish date end |
| `chef_id` | UUID | Filter by chef ID |
| `chef_handle` | string | Filter by chef handle |
| `page` | int | Page number (default `1`) |
| `page_size` | int | Results per page (default `20`, max `100`) |

Response always includes a `meta` block:
```json
{
  "data": [ ... ],
  "meta": {
    "page": 1,
    "pageSize": 20,
    "total": 340,
    "totalPages": 17,
    "hasNext": true,
    "hasPrevious": false
  }
}
```

#### Get recipe — `GET /v1/recipes/{id}`

---

### Recipes (Authoring — requires `CHEF` or `ADMIN` role)

All authoring endpoints require `Authorization: Bearer <access-token>`.

#### Create draft — `POST /v1/recipes`
```json
{
  "title": "Spaghetti Carbonara",
  "summary": "Classic Roman pasta",
  "ingredients": [
    { "name": "spaghetti", "quantity": "400", "unit": "g" },
    { "name": "eggs", "quantity": "4", "unit": null }
  ],
  "steps": [
    { "stepNumber": 1, "instruction": "Boil salted water" },
    { "stepNumber": 2, "instruction": "Cook pasta al dente" }
  ],
  "labels": ["italian", "pasta", "quick"],
  "imageKeys": []
}
```
Returns `201 DRAFT`.

#### Update — `PATCH /v1/recipes/{id}`
Partial update — only provided fields are changed.

#### Publish — `PATCH /v1/recipes/{id}/publish`
Returns `202 Accepted`. The worker processes images and indexes the recipe asynchronously.

#### Unpublish — `PATCH /v1/recipes/{id}/unpublish`
Reverts to `DRAFT` and removes from search index.

#### Delete — `DELETE /v1/recipes/{id}`
Returns `204`.

---

### Image Upload Flow

```
1. GET  /v1/recipes/{id}/images/upload-url?filename=photo.jpg
        → { uploadUrl: "https://...", key: "uploads/recipes/..." }

2. PUT  {uploadUrl}   (client uploads directly to S3)
        Content-Type: image/jpeg
        Body: <binary image data>

3. POST /v1/recipes/{id}/images
        { "imageKeys": ["uploads/recipes/.../photo.jpg"] }
        → 201 [{ id, processingState: "PENDING", ... }]

4. Worker picks up the event, resizes images, writes
   thumb_url and medium_url back to the database.
```

---

### Following

#### Follow a chef — `POST /v1/chefs/{id}/follow`
Returns `204`.

#### Unfollow — `DELETE /v1/chefs/{id}/follow`
Returns `204`.

#### Chef profile — `GET /v1/chefs/{id}` or `GET /v1/chefs/handle/{handle}`
Returns chef detail including `followerCount`, `followingCount`, `isFollowedByMe`.

#### Followers list — `GET /v1/chefs/{id}/followers`
#### Following list — `GET /v1/chefs/{id}/following`
Both paginated with the standard `meta` block.

---

### Feed (authenticated)

#### `GET /v1/feed`
Returns published recipes from all chefs the caller follows.
Supports `published_from`, `published_to`, `page`, `page_size`.

---

## Message Queue

| Exchange | Type | Routing key | Queue | Consumer |
|----------|------|-------------|-------|----------|
| `recipe.events` | topic | `recipe.published` | `recipe.publish.queue` | Worker |
| `recipe.events` | topic | `recipe.updated` | `recipe.update.queue` | Worker |
| `recipe.events` | topic | `recipe.deleted` | `recipe.delete.queue` | Worker |
| `recipe.events.dlx` | direct | `recipe.events.dlq` | `recipe.events.dlq` | Manual inspection |

Messages that fail after retry are moved to the **Dead Letter Queue** for manual inspection.
The worker uses **manual ACK** — messages are only acknowledged after successful processing.

---

## Running Tests

```bash
# All tests (both modules)
mvn test

# API tests only
cd recipe-api && mvn test

# Worker tests only
cd recipe-worker && mvn test
```

Tests use H2 in-memory database (PostgreSQL compatibility mode).
RabbitMQ and Elasticsearch are mocked via `@MockBean`.

---

## Database Schema

Managed by **Flyway**. Migration files live in `recipe-api/src/main/resources/db/migration/`.

Key tables: `users`, `chef_profiles`, `chef_follows`, `recipes`, `recipe_images`, `refresh_tokens`.

JSONB columns on `recipes`: `ingredients`, `steps`, `labels` — all indexed with GIN for fast filtering.

---

## Security Notes

- JWT access token TTL: **15 minutes** (configurable via `app.jwt.access-token-ttl-ms`)
- Refresh token TTL: **7 days** (configurable via `app.jwt.refresh-token-ttl-days`)
- Passwords hashed with **BCrypt**
- JWT signed with **HMAC-SHA256**
- **Change `JWT_SECRET`** before any production deployment — must be at least 32 characters
- Image uploads go directly to S3 via pre-signed PUT URLs — binary data never passes through the API server

---

## Feature Flags

| Flag | Property | Default |
|------|----------|---------|
| Email verification | `app.features.email-verification-enabled` | `false` |
