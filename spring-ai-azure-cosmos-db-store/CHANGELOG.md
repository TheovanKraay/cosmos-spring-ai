# Changelog — spring-ai-azure-cosmos-db-store

All notable changes to **`spring-ai-azure-cosmos-db-store`** are documented
here. The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/).

This changelog covers **only the `spring-ai-azure-cosmos-db-store` module**.
Sibling modules (`spring-ai-autoconfigure-vector-store-azure-cosmos-db`,
`spring-ai-model-chat-memory-repository-cosmos-db`,
`spring-ai-autoconfigure-model-chat-memory-repository-cosmos-db`) maintain
their own changelogs.

## [Unreleased]

### Added

### Changed

### Deprecated

### Removed

### Fixed

### Security

## [1.0.0-RC1] — 2026-06-10

### Added

- `CosmosDBVectorStore.Builder.vectorIndexType(...)` to select the Cosmos vector
  index type (`FLAT`, `QUANTIZED_FLAT`, `DISK_ANN`). Default remains `DISK_ANN`,
  so existing code is unaffected. This enables use against the Cosmos DB
  emulator and serverless accounts that do not support `DISK_ANN`.

### Changed

- Upgrade Spring AI to `2.0.0-RC1` (from `2.0.0-M7`).
- Upgrade `azure-spring-data-cosmos` to `7.3.0` (from `5.22.0`), which brings
  an `azure-cosmos` SDK compatible with Netty 4.2 SSL handling. This restores
  Direct (RNTBD) mode connectivity under Spring Boot 4.x.

### Fixed

- `CosmosDBVectorStore` now persists documents correctly under Spring Boot 4 /
  Jackson 3. The previous implementation built payloads with Jackson 3
  `ObjectNode` instances which the Cosmos SDK (Jackson 2) serialized as empty
  `{}` objects, silently dropping content, metadata, and embeddings. Document
  payloads are now built as `Map<String, Object>` so they serialize correctly
  regardless of which Jackson version the underlying SDK uses.

## [1.0.0-M1] — 2026-05-26

### Added

- Initial milestone release of the Azure Cosmos DB vector store for Spring AI
