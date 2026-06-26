# Azure Cosmos DB Vector Store

This document walks you through setting up `CosmosDBVectorStore` to store document embeddings and perform similarity searches.

## What is Azure Cosmos DB?

[Azure Cosmos DB](https://azure.microsoft.com/en-us/services/cosmos-db/) is Microsoft's globally distributed cloud-native database service designed for mission-critical applications. It offers high availability, low latency, and the ability to scale horizontally to meet modern application demands. It was built from the ground up with global distribution, fine-grained multi-tenancy, and horizontal scalability at its core.

## What is DiskANN?

DiskANN (Disk-based Approximate Nearest Neighbor Search) is an innovative technology used in Azure Cosmos DB to enhance the performance of vector searches. It enables efficient and scalable similarity searches across high-dimensional data by indexing embeddings stored in Cosmos DB.

DiskANN provides the following benefits:

- **Efficiency**: By utilizing disk-based structures, DiskANN significantly reduces the time required to find nearest neighbors compared to traditional methods.
- **Scalability**: It can handle large datasets that exceed memory capacity, making it suitable for various applications, including machine learning and AI-driven solutions.
- **Low Latency**: DiskANN minimizes latency during search operations, ensuring that applications can retrieve results quickly even with substantial data volumes.

In the context of Spring AI for Azure Cosmos DB, vector searches will create and leverage DiskANN indexes to ensure optimal performance for similarity queries.

## Auto-Configuration Setup

The easiest way to use the Cosmos DB vector store is with Spring Boot auto-configuration.

### Dependency

```xml
<dependency>
    <groupId>com.azure.spring.ai</groupId>
    <artifactId>spring-ai-autoconfigure-vector-store-azure-cosmos-db</artifactId>
    <version>1.0.0</version>
</dependency>
```

### Configuration Properties

| Property | Description |
|----------|-------------|
| `spring.ai.vectorstore.cosmosdb.databaseName` | The name of the Cosmos DB database to use |
| `spring.ai.vectorstore.cosmosdb.containerName` | The name of the Cosmos DB container to use |
| `spring.ai.vectorstore.cosmosdb.partitionKeyPath` | The path for the partition key |
| `spring.ai.vectorstore.cosmosdb.metadataFields` | Comma-separated list of metadata fields |
| `spring.ai.vectorstore.cosmosdb.vectorStoreThroughput` | The throughput for the vector store |
| `spring.ai.vectorstore.cosmosdb.vectorDimensions` | The number of dimensions for the vectors |
| `spring.ai.vectorstore.cosmosdb.endpoint` | The endpoint for the Cosmos DB |
| `spring.ai.vectorstore.cosmosdb.key` | The key for the Cosmos DB (if not present, [DefaultAzureCredential](https://learn.microsoft.com/azure/developer/java/sdk/authentication/credential-chains#defaultazurecredential-overview) will be used) |

### Example with Auto-Configuration

```java
package com.example.demo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Lazy;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@SpringBootApplication
public class DemoApplication implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoApplication.class);

    @Lazy
    @Autowired
    private VectorStore vectorStore;

    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }

    @Override
    public void run(String... args) throws Exception {
        Document document1 = new Document(UUID.randomUUID().toString(), "Sample content1", Map.of("key1", "value1"));
        Document document2 = new Document(UUID.randomUUID().toString(), "Sample content2", Map.of("key2", "value2"));

        this.vectorStore.add(List.of(document1, document2));

        List<Document> results = this.vectorStore.similaritySearch(
            SearchRequest.builder().query("Sample content").topK(1).build());
        log.info("Search results: {}", results);

        this.vectorStore.delete(List.of(document1.getId(), document2.getId()));
    }
}
```

## Manual Configuration (without Auto-Configuration)

### Dependency

```xml
<dependency>
    <groupId>com.azure.spring.ai</groupId>
    <artifactId>spring-ai-azure-cosmos-db-store</artifactId>
    <version>1.0.0</version>
</dependency>
```

### Example

[DefaultAzureCredential](https://learn.microsoft.com/azure/developer/java/sdk/authentication/credential-chains#defaultazurecredential-overview) is recommended for authentication.

```java
import com.azure.cosmos.CosmosAsyncClient;
import com.azure.cosmos.CosmosClientBuilder;
import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.spring.ai.vectorstore.cosmosdb.CosmosDBVectorStore;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CosmosVectorStoreConfig {

    @Bean
    public VectorStore vectorStore(CosmosAsyncClient cosmosClient, EmbeddingModel embeddingModel) {
        return CosmosDBVectorStore.builder(cosmosClient, embeddingModel)
                .databaseName("my-database")
                .containerName("my-vectors")
                .metadataFields(List.of("country", "year", "city"))
                .partitionKeyPath("/id")
                .vectorStoreThroughput(1000)
                .vectorDimensions(1536)  // Match your embedding model's dimensions
                .batchingStrategy(new TokenCountBatchingStrategy())
                .build();
    }

    @Bean
    public CosmosAsyncClient cosmosClient() {
        return new CosmosClientBuilder()
                .endpoint(System.getenv("COSMOSDB_AI_ENDPOINT"))
                .credential(new DefaultAzureCredentialBuilder().build())
                .userAgentSuffix("SpringAI-CDBNoSQL-VectorStore")
                .gatewayMode()
                .buildAsyncClient();
    }
}
```

### Builder Options

| Option | Description |
|--------|-------------|
| `databaseName` | The name of your Cosmos DB database |
| `containerName` | The name of your container within the database |
| `partitionKeyPath` | The path for the partition key (e.g., "/id") |
| `metadataFields` | List of metadata fields that will be used for filtering |
| `vectorStoreThroughput` | The throughput (RU/s) for the vector store container |
| `vectorDimensions` | The number of dimensions for your vectors (should match your embedding model) |
| `batchingStrategy` | Strategy for batching document operations (optional) |

## Complex Searches with Filters

You can perform more complex searches using filters:

```java
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;

Map<String, Object> metadata1 = Map.of("country", "UK", "year", 2021, "city", "London");
Map<String, Object> metadata2 = Map.of("country", "NL", "year", 2022, "city", "Amsterdam");

Document document1 = new Document("1", "A document about the UK", metadata1);
Document document2 = new Document("2", "A document about the Netherlands", metadata2);

vectorStore.add(List.of(document1, document2));

FilterExpressionBuilder builder = new FilterExpressionBuilder();
List<Document> results = vectorStore.similaritySearch(
    SearchRequest.builder()
        .query("The World")
        .topK(10)
        .filterExpression(builder.in("country", "UK", "NL").build())
        .build());
```

## Accessing the Native Client

The vector store provides access to the underlying native Azure Cosmos DB client through `getNativeClient()`:

```java
import com.azure.spring.ai.vectorstore.cosmosdb.CosmosDBVectorStore;
import com.azure.cosmos.CosmosAsyncContainer;

CosmosDBVectorStore vectorStore = context.getBean(CosmosDBVectorStore.class);
Optional<CosmosAsyncContainer> nativeClient = vectorStore.getNativeClient();

if (nativeClient.isPresent()) {
    CosmosAsyncContainer container = nativeClient.get();
    // Use the native client for Cosmos DB-specific operations
}
```
