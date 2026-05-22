# Azure Cosmos DB Chat Memory Repository

The `CosmosDBChatMemoryRepository` provides persistent chat memory storage using Azure Cosmos DB. It implements the Spring AI `ChatMemoryRepository` interface for storing and retrieving conversation messages.

## Dependency

```xml
<dependency>
    <groupId>com.azure.spring.ai</groupId>
    <artifactId>spring-ai-model-chat-memory-repository-cosmos-db</artifactId>
    <version>1.0.0-M1</version>
</dependency>
```

## Usage

### Manual Configuration

```java
import com.azure.cosmos.CosmosAsyncClient;
import com.azure.cosmos.CosmosClientBuilder;
import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.spring.ai.chat.memory.repository.cosmosdb.CosmosDBChatMemoryRepository;
import com.azure.spring.ai.chat.memory.repository.cosmosdb.CosmosDBChatMemoryRepositoryConfig;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;

// Create the Cosmos DB client
CosmosAsyncClient cosmosClient = new CosmosClientBuilder()
    .endpoint("https://your-account.documents.azure.com:443/")
    .credential(new DefaultAzureCredentialBuilder().build())
    .userAgentSuffix("SpringAI-CDBNoSQL-ChatMemoryRepository")
    .gatewayMode()
    .buildAsyncClient();

// Create the repository
ChatMemoryRepository chatMemoryRepository = CosmosDBChatMemoryRepository
    .create(CosmosDBChatMemoryRepositoryConfig.builder()
        .withCosmosClient(cosmosClient)
        .withDatabaseName("chat-memory-db")
        .withContainerName("conversations")
        .build());

// Use with MessageWindowChatMemory
ChatMemory chatMemory = MessageWindowChatMemory.builder()
    .chatMemoryRepository(chatMemoryRepository)
    .maxMessages(10)
    .build();
```

### Spring Boot Auto-Configuration

When using Spring Boot, configure via `application.properties`:

```properties
spring.ai.chat.memory.repository.cosmosdb.endpoint=https://your-account.documents.azure.com:443/
spring.ai.chat.memory.repository.cosmosdb.database-name=SpringAIChatMemory
spring.ai.chat.memory.repository.cosmosdb.container-name=ChatMemory
spring.ai.chat.memory.repository.cosmosdb.connection-mode=gateway
```

Then inject the repository:

```java
import com.azure.spring.ai.chat.memory.repository.cosmosdb.CosmosDBChatMemoryRepository;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.beans.factory.annotation.Autowired;

@Autowired
CosmosDBChatMemoryRepository chatMemoryRepository;

ChatMemory chatMemory = MessageWindowChatMemory.builder()
    .chatMemoryRepository(chatMemoryRepository)
    .maxMessages(10)
    .build();
```

## Configuration Properties

| Property | Description | Default |
|----------|-------------|---------|
| `spring.ai.chat.memory.repository.cosmosdb.endpoint` | Azure Cosmos DB endpoint URI | (required) |
| `spring.ai.chat.memory.repository.cosmosdb.key` | Primary or secondary key (if absent, uses Azure Identity) | |
| `spring.ai.chat.memory.repository.cosmosdb.connection-mode` | Connection mode: `direct` or `gateway` | `gateway` |
| `spring.ai.chat.memory.repository.cosmosdb.database-name` | Database name | `SpringAIChatMemory` |
| `spring.ai.chat.memory.repository.cosmosdb.container-name` | Container name | `ChatMemory` |
| `spring.ai.chat.memory.repository.cosmosdb.partition-key-path` | Partition key path | `/conversationId` |

## Authentication

Two methods are supported:

1. **Key-based authentication**: Provide the `spring.ai.chat.memory.repository.cosmosdb.key` property.
2. **Azure Identity authentication** (recommended): When no key is provided, the repository uses [DefaultAzureCredential](https://learn.microsoft.com/azure/developer/java/sdk/authentication/credential-chains#defaultazurecredential-overview) for managed identity, service principal, or other Azure credential sources.

## Schema Initialization

The repository automatically creates the database and container if they don't exist. The container uses `/conversationId` as the partition key for optimal performance. No manual schema setup is required.

## Operations

The repository supports the following operations:

```java
// Save messages for a conversation
chatMemoryRepository.saveAll(conversationId, messages);

// Retrieve messages for a conversation
List<Message> messages = chatMemoryRepository.findByConversationId(conversationId);

// List all conversation IDs
List<String> ids = chatMemoryRepository.findConversationIds();

// Delete a conversation
chatMemoryRepository.deleteByConversationId(conversationId);
```

Messages are stored with their type (USER, ASSISTANT, SYSTEM) and retrieved in the order they were saved, which is the expected format for LLM conversation history.
