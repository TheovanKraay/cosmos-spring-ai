# Azure Cosmos DB Vector Store Auto-Configuration

Spring Boot auto-configuration for the Azure Cosmos DB Vector Store. This module provides zero-config setup when using Spring Boot.

## Dependency

```xml
<dependency>
    <groupId>com.azure.spring.ai</groupId>
    <artifactId>spring-ai-autoconfigure-vector-store-azure-cosmos-db</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

This transitively brings in the `spring-ai-azure-cosmos-db-store` module.

## Configuration Properties

Add these to your `application.properties` or `application.yml`:

```properties
spring.ai.vectorstore.cosmosdb.endpoint=https://your-account.documents.azure.com:443/
spring.ai.vectorstore.cosmosdb.databaseName=my-database
spring.ai.vectorstore.cosmosdb.containerName=my-vectors
spring.ai.vectorstore.cosmosdb.metadataFields=country,year,city
spring.ai.vectorstore.cosmosdb.vectorStoreThroughput=1000
spring.ai.vectorstore.cosmosdb.vectorDimensions=1536
spring.ai.vectorstore.cosmosdb.partitionKeyPath=/id
```

### Authentication

**Key-based** (set the `key` property):
```properties
spring.ai.vectorstore.cosmosdb.key=your-cosmos-db-key
```

**Azure Identity** (recommended for production — omit the `key` property):
When no key is provided, the auto-configuration uses [DefaultAzureCredential](https://learn.microsoft.com/azure/developer/java/sdk/authentication/credential-chains#defaultazurecredential-overview) for authentication via managed identity, service principal, or other Azure credential sources.

## Usage

Once configured, simply inject `VectorStore`:

```java
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;

@Autowired
private VectorStore vectorStore;
```

The auto-configuration will create the database and container if they don't exist.

## Full Property Reference

| Property | Description | Default |
|----------|-------------|---------|
| `spring.ai.vectorstore.cosmosdb.endpoint` | Cosmos DB endpoint URI | (required) |
| `spring.ai.vectorstore.cosmosdb.key` | Primary or secondary key | (uses DefaultAzureCredential if absent) |
| `spring.ai.vectorstore.cosmosdb.databaseName` | Database name | (required) |
| `spring.ai.vectorstore.cosmosdb.containerName` | Container name | (required) |
| `spring.ai.vectorstore.cosmosdb.partitionKeyPath` | Partition key path | `/id` |
| `spring.ai.vectorstore.cosmosdb.metadataFields` | Metadata fields for filtering | (empty) |
| `spring.ai.vectorstore.cosmosdb.vectorStoreThroughput` | Container throughput (RU/s) | `400` |
| `spring.ai.vectorstore.cosmosdb.vectorDimensions` | Vector dimensions | `1536` |
