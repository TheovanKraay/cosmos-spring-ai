# Spring AI Cosmos DB

Community-maintained Spring AI integrations for Azure Cosmos DB.

## Modules

| Module | Description |
|--------|-------------|
| `spring-ai-azure-cosmos-db-store` | Vector Store implementation for Azure Cosmos DB |
| `spring-ai-autoconfigure-vector-store-azure-cosmos-db` | Spring Boot auto-configuration for the Cosmos DB vector store |
| `spring-ai-model-chat-memory-repository-cosmos-db` | Chat Memory Repository implementation for Azure Cosmos DB |

## Coordinates

```xml
<dependency>
    <groupId>com.azure.spring.ai</groupId>
    <artifactId>spring-ai-azure-cosmos-db-store</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

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

## License

Apache License 2.0
