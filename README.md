# Spring AI Cosmos DB

Community-maintained Spring AI integrations for Azure Cosmos DB.

## Modules

| Module | Artifact ID | Description |
|--------|-------------|-------------|
| [Vector Store](docs/vector-store.md) | `spring-ai-azure-cosmos-db-store` | Vector Store implementation using DiskANN |
| Vector Store Auto-Configuration | `spring-ai-autoconfigure-vector-store-azure-cosmos-db` | Spring Boot auto-configuration for the vector store (see [Vector Store](docs/vector-store.md)) |
| [Chat Memory Repository](docs/chat-memory.md) | `spring-ai-model-chat-memory-repository-cosmos-db` | Chat Memory Repository for conversation persistence |
| [Chat Memory Auto-Configuration](docs/chat-memory-autoconfigure.html) | `spring-ai-autoconfigure-model-chat-memory-repository-cosmos-db` | Spring Boot auto-configuration for chat memory |

## Maven Coordinates

```xml
<dependency>
    <groupId>com.azure.spring.ai</groupId>
    <artifactId>spring-ai-azure-cosmos-db-store</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

```xml
<dependency>
    <groupId>com.azure.spring.ai</groupId>
    <artifactId>spring-ai-autoconfigure-vector-store-azure-cosmos-db</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

```xml
<dependency>
    <groupId>com.azure.spring.ai</groupId>
    <artifactId>spring-ai-model-chat-memory-repository-cosmos-db</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

```xml
<dependency>
    <groupId>com.azure.spring.ai</groupId>
    <artifactId>spring-ai-autoconfigure-model-chat-memory-repository-cosmos-db</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

## Prerequisites

- Java 17+
- Spring Boot 4.1+
- Spring AI 2.0+
- An Azure Cosmos DB account (or the [Cosmos DB Emulator](https://learn.microsoft.com/en-us/azure/cosmos-db/emulator) for local development)

## Building

```bash
mvn clean install -DskipTests
```

## Running Emulator Tests Locally

1. Start the [Azure Cosmos DB Emulator](https://learn.microsoft.com/en-us/azure/cosmos-db/emulator)
2. Import the emulator certificate into your JDK truststore
3. Run:

```bash
mvn verify -Pemulator
```

## Running Full Integration Tests

Set `AZURE_COSMOSDB_ENDPOINT` environment variable pointing to your Cosmos DB account, then:

```bash
mvn verify
```

## Version Compatibility

| Spring AI Version | Cosmos DB Module | Notes |
|-------------------|-----------------|-------|
| 1.x | Built-in | Cosmos DB support included directly in Spring AI |
| 2.x | This project 1.x | Cosmos DB support moved to this standalone repo |

## Migration from Spring AI

These modules were previously part of the [Spring AI](https://github.com/spring-projects/spring-ai) monorepo. If you're migrating from Spring AI 1.x:

| Old GroupId | New GroupId |
|-------------|-------------|
| `org.springframework.ai` | `com.azure.spring.ai` |

| Old Package | New Package |
|-------------|-------------|
| `org.springframework.ai.vectorstore.cosmosdb` | `com.azure.spring.ai.vectorstore.cosmosdb` |
| `org.springframework.ai.vectorstore.cosmosdb.autoconfigure` | `com.azure.spring.ai.vectorstore.cosmosdb.autoconfigure` |
| `org.springframework.ai.chat.memory.repository.cosmosdb` | `com.azure.spring.ai.chat.memory.repository.cosmosdb` |
| `org.springframework.ai.model.chat.memory.repository.cosmosdb.autoconfigure` | `com.azure.spring.ai.model.chat.memory.repository.cosmosdb.autoconfigure` |

## License

Apache License 2.0
