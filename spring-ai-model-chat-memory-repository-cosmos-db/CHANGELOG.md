# Changelog — spring-ai-model-chat-memory-repository-cosmos-db

All notable changes to **`spring-ai-model-chat-memory-repository-cosmos-db`**
are documented here. The format is based on
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project
adheres to [Semantic Versioning](https://semver.org/).

This changelog covers **only the chat-memory-repository module**. Sibling
modules (`spring-ai-azure-cosmos-db-store`,
`spring-ai-autoconfigure-vector-store-azure-cosmos-db`,
`spring-ai-autoconfigure-model-chat-memory-repository-cosmos-db`) maintain
their own changelogs.

## [Unreleased]

### Added

### Changed

### Deprecated

### Removed

### Fixed

### Security

## [1.0.0] — 2026-06-25

### Changed

- Upgraded Spring AI to 2.0.0 (GA) and Spring Boot to 4.1.0 (GA).

## [1.0.0-RC1] — 2026-06-10

### Changed

- Upgrade Spring AI to `2.0.0-RC1` (from `2.0.0-M7`).
- Upgrade `azure-spring-data-cosmos` to `7.3.0` (from `5.22.0`), which brings
  an `azure-cosmos` SDK compatible with Netty 4.2 SSL handling. This restores
  Direct (RNTBD) mode connectivity under Spring Boot 4.x.

### Fixed

- `CosmosDBChatMemoryRepository.createMessageDocument` no longer throws
  `NullPointerException` when message metadata contains `Optional.empty()`
  values. Metadata entries that unwrap to `null` are now skipped instead of
  being passed to `Collectors.toMap`, which rejects null values.
- Tool messages now round-trip correctly. `AssistantMessage.toolCalls` and
  `ToolResponseMessage` payloads are persisted and reconstructed on read, so
  multi-turn tool-call conversations retain their state across reloads. Prior
  to this fix only the text `content` was stored, causing agents using
  tool-calling patterns to lose context.

## [1.0.0-M1] — 2026-05-26

### Added

- Initial milestone release of the Azure Cosmos DB chat memory repository for Spring AI
