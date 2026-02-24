# Zanzibar Sample — Ktor + OpenFGA ReBAC

A Ktor application demonstrating **Relationship-Based Access Control (ReBAC)** using [OpenFGA](https://openfga.dev) with auto-starting TestContainers in development mode.

## Features

- **Auto-start TestContainers** — PostgreSQL + OpenFGA spin up automatically when Ktor starts in dev mode
- **Auto-applied authorization model** — The OpenFGA schema is written on startup
- **Kotlin DSL for route protection** — Clean `rebac { permission("view") { ... } }` syntax
- **Example document API** — Protected CRUD routes demonstrating the ReBAC pattern

## Prerequisites

- JDK 21+
- Docker (for dev-mode TestContainers)

## Quick Start

```bash
./gradlew run
```

Ktor starts in **development mode** by default. On startup it will:

1. Start a **PostgreSQL** container
2. Run **OpenFGA migrations**
3. Start an **OpenFGA server** container
4. Create a store and write the authorization model

## Authorization Model

The model is defined in OpenFGA DSL at `src/main/resources/openfga/model.fga`:

```
model
  schema 1.1

type user

type document
  relations
    define admin: [user]
    define reader: [user]
    define writer: [user]

    define edit: writer
    define view: reader or edit
```

The OpenFGA API only accepts JSON, so the app loads `src/main/resources/openfga/model.json` at startup.
After editing the `.fga` file, regenerate the JSON using the [FGA CLI](https://github.com/openfga/cli):

```bash
# Install the FGA CLI (once)
brew install openfga/tap/fga

# Convert DSL → JSON
fga model transform --inputfile src/main/resources/openfga/model.fga > src/main/resources/openfga/model.json
```

## API Usage

### 1. Grant a user access

```bash
curl -X POST http://localhost:8080/admin/tuples \
  -H "Content-Type: application/json" \
  -d '{"user":"alice","relation":"reader","object_type":"document","object_id":"1"}'
```

### 2. Access a protected document

```bash
# ✅ Allowed — alice is a reader (has "view" permission)
curl http://localhost:8080/documents/1 -H "X-User: alice"

# ❌ Forbidden — bob has no permissions
curl http://localhost:8080/documents/1 -H "X-User: bob"
```

### 3. Grant write access and edit

```bash
curl -X POST http://localhost:8080/admin/tuples \
  -H "Content-Type: application/json" \
  -d '{"user":"alice","relation":"writer","object_type":"document","object_id":"1"}'

# ✅ Allowed — alice is a writer (has "edit" permission)
curl -X PUT http://localhost:8080/documents/1 -H "X-User: alice"
```

## ReBAC Kotlin DSL

The DSL provides two levels of API:

### High-level — `rebac` block

```kotlin
route("/documents/{documentId}") {
    rebac("document", "documentId") {
        permission("view") {
            get { call.respond(mapOf("content" to "...")) }
        }
        permission("edit") {
            put { call.respond(mapOf("status" to "updated")) }
        }
    }
}
```

### Low-level — `requirePermission`

```kotlin
route("/documents/{documentId}") {
    requirePermission("document", "documentId", "view") {
        get { call.respond(mapOf("content" to "...")) }
    }
}
```

## Configuration

In `application.yaml`:

```yaml
ktor:
  development: true        # enables TestContainers autostart

openfga:
  userHeader: "X-User"     # header used to identify the user
```

For production, set `development: false` and configure:

```yaml
openfga:
  apiUrl: "https://your-openfga-server:8080"
  storeId: "your-store-id"
  authorizationModelId: "your-model-id"
```

