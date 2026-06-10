# Changelog — spring-ai-autoconfigure-vector-store-azure-cosmos-db

All notable changes to **`spring-ai-autoconfigure-vector-store-azure-cosmos-db`**
are documented here. The format is based on
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project
adheres to [Semantic Versioning](https://semver.org/).

This changelog covers **only this autoconfigure module**. Sibling modules
(`spring-ai-azure-cosmos-db-store`,
`spring-ai-model-chat-memory-repository-cosmos-db`,
`spring-ai-autoconfigure-model-chat-memory-repository-cosmos-db`) maintain
their own changelogs.

> This module depends on `spring-ai-azure-cosmos-db-store` via the
> `<spring-ai-cosmos-db-store.version>` property in this module's `pom.xml`.

## [Unreleased]

### Added

### Changed

### Deprecated

### Removed

### Fixed

### Security

## [1.0.0-RC1] — 2026-06-10

### Added

- `spring.ai.vectorstore.cosmosdb.vectorIndexType` configuration property to
  select the Cosmos vector index type (`FLAT`, `QUANTIZED_FLAT`, `DISK_ANN`)
  without replacing the auto-configured `CosmosDBVectorStore` bean. Default
  remains `DISK_ANN`.

### Changed

- Upgrade Spring AI to `2.0.0-RC1` (from `2.0.0-M7`).

### Removed

- Unused `<azure-spring-data-cosmos.version>` Maven property. This module has
  no direct dependency on `azure-spring-data-cosmos`; the version is governed
  by the underlying `spring-ai-azure-cosmos-db-store` module.

## [1.0.0-M1] — 2026-05-26

### Added

- Initial milestone release of the Azure Cosmos DB vector store auto-configuration for Spring Boot
- Pins `spring-ai-azure-cosmos-db-store` at `1.0.0-M1` (`<spring-ai-cosmos-db-store.version>`)
