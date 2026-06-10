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

> This module depends on `spring-ai-azure-cosmos-db-store`. Each release
> should record which `store` version it pins (the
> `<spring-ai-cosmos-db-store.version>` property in this module's `pom.xml`).

## [Unreleased]

### Added

### Changed

### Deprecated

### Removed

### Fixed

### Security

## [1.0.0-RC1] — 2026-06-10

### Changed

- Upgrade to Spring AI `2.0.0-RC1`
- Upgrade `azure-spring-data-cosmos` to `7.3.0`
- Pin `spring-ai-azure-cosmos-db-store` at `1.0.0-RC1` (`<spring-ai-cosmos-db-store.version>`)

### Fixed

- Restore content null guard in auto-configuration

## [1.0.0-M1] — 2026-05-26

### Added

- Initial milestone release of the Azure Cosmos DB vector store auto-configuration for Spring Boot
- Pins `spring-ai-azure-cosmos-db-store` at `1.0.0-M1` (`<spring-ai-cosmos-db-store.version>`)
