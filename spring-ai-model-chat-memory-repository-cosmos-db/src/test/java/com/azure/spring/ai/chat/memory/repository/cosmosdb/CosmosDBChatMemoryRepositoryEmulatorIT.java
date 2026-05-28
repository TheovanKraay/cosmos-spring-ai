/*
 * Copyright 2023-present the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.azure.spring.ai.chat.memory.repository.cosmosdb;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.azure.cosmos.CosmosAsyncClient;
import com.azure.cosmos.CosmosClientBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Emulator-based integration tests for {@link CosmosDBChatMemoryRepository}.
 * Activated by the Maven 'emulator' profile. Uses key-based auth against the local
 * Cosmos DB Emulator.
 *
 * @author Theo van Kraay
 */
class CosmosDBChatMemoryRepositoryEmulatorIT {

	private static final String EMULATOR_ENDPOINT = System.getProperty("cosmos.endpoint",
			"https://localhost:8081");

	private static final String EMULATOR_KEY = System.getProperty("cosmos.key",
			"C2y6yDjf5/R+ob0N8A7Cgv30VRDJIWEHLM+4QDU5DE2nQ9nDuVTqobD4b8mGGyPMbIZnqyMsEcaGQy67XIw/Jw==");

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withUserConfiguration(TestApplication.class);

	private ChatMemoryRepository chatMemoryRepository;

	@BeforeEach
	public void setup() {
		this.contextRunner.run(context -> this.chatMemoryRepository = context.getBean(ChatMemoryRepository.class));
	}

	@Test
	void testSaveAndRetrieveMessages() {
		var conversationId = UUID.randomUUID().toString();

		List<Message> messages = List.of(new UserMessage("Hello from emulator test"),
				new AssistantMessage("Hello! I'm running on the emulator."));

		this.chatMemoryRepository.saveAll(conversationId, messages);

		List<Message> retrieved = this.chatMemoryRepository.findByConversationId(conversationId);
		assertThat(retrieved).hasSize(2);
		assertThat(retrieved.get(0).getText()).isEqualTo("Hello from emulator test");
		assertThat(retrieved.get(0).getMessageType()).isEqualTo(MessageType.USER);
		assertThat(retrieved.get(1).getText()).isEqualTo("Hello! I'm running on the emulator.");
		assertThat(retrieved.get(1).getMessageType()).isEqualTo(MessageType.ASSISTANT);
	}

	@Test
	void testDeleteConversation() {
		var conversationId = UUID.randomUUID().toString();

		this.chatMemoryRepository.saveAll(conversationId,
				List.of(new UserMessage("To be deleted")));

		assertThat(this.chatMemoryRepository.findByConversationId(conversationId)).hasSize(1);

		this.chatMemoryRepository.deleteByConversationId(conversationId);

		assertThat(this.chatMemoryRepository.findByConversationId(conversationId)).isEmpty();
	}

	@Test
	void testFindConversationIds() {
		var id1 = UUID.randomUUID().toString();
		var id2 = UUID.randomUUID().toString();

		this.chatMemoryRepository.saveAll(id1, List.of(new UserMessage("Msg 1")));
		this.chatMemoryRepository.saveAll(id2, List.of(new UserMessage("Msg 2")));

		List<String> ids = this.chatMemoryRepository.findConversationIds();
		assertThat(ids).contains(id1, id2);
	}

	/**
	 * Tests that metadata containing Optional values is serialized correctly.
	 * This catches the bug where Optional values in message metadata would cause
	 * Jackson serialization failures in Cosmos DB.
	 */
	@Test
	void testMetadataWithOptionalValuesRoundTrip() {
		var conversationId = UUID.randomUUID().toString();

		Map<String, Object> metadata = new HashMap<>();
		metadata.put("messageId", Optional.of("msg-123"));
		metadata.put("emptyOptional", Optional.empty());
		metadata.put("normalValue", "hello");

		Message userMsg = UserMessage.builder().text("Test with optionals").metadata(metadata).build();

		this.chatMemoryRepository.saveAll(conversationId, List.of(userMsg));

		List<Message> retrieved = this.chatMemoryRepository.findByConversationId(conversationId);
		assertThat(retrieved).hasSize(1);
		assertThat(retrieved.get(0).getText()).isEqualTo("Test with optionals");
		// Optional.of("msg-123") should be unwrapped to "msg-123"
		assertThat(retrieved.get(0).getMetadata()).containsEntry("messageId", "msg-123");
		// Optional.empty() should be excluded
		assertThat(retrieved.get(0).getMetadata()).doesNotContainKey("emptyOptional");
		// Normal values should pass through unchanged
		assertThat(retrieved.get(0).getMetadata()).containsEntry("normalValue", "hello");
	}

	/**
	 * Tests that AssistantMessage with toolCalls survives a round-trip through
	 * Cosmos DB. This catches the bug where tool call details (id, name, arguments)
	 * were lost on persistence, causing orphaned tool messages in conversation.
	 */
	@Test
	void testAssistantMessageWithToolCallsRoundTrip() {
		var conversationId = UUID.randomUUID().toString();

		List<AssistantMessage.ToolCall> toolCalls = List.of(
				new AssistantMessage.ToolCall("call_abc123", "function", "get_weather",
						"{\"location\": \"Seattle\"}"),
				new AssistantMessage.ToolCall("call_def456", "function", "get_time",
						"{\"timezone\": \"PST\"}"));

		AssistantMessage assistantMsg = AssistantMessage.builder()
			.content("")
			.toolCalls(toolCalls)
			.build();

		this.chatMemoryRepository.saveAll(conversationId, List.of(assistantMsg));

		List<Message> retrieved = this.chatMemoryRepository.findByConversationId(conversationId);
		assertThat(retrieved).hasSize(1);
		assertThat(retrieved.get(0).getMessageType()).isEqualTo(MessageType.ASSISTANT);

		AssistantMessage retrievedAssistant = (AssistantMessage) retrieved.get(0);
		assertThat(retrievedAssistant.hasToolCalls()).isTrue();
		assertThat(retrievedAssistant.getToolCalls()).hasSize(2);

		AssistantMessage.ToolCall firstCall = retrievedAssistant.getToolCalls().get(0);
		assertThat(firstCall.id()).isEqualTo("call_abc123");
		assertThat(firstCall.type()).isEqualTo("function");
		assertThat(firstCall.name()).isEqualTo("get_weather");
		assertThat(firstCall.arguments()).isEqualTo("{\"location\": \"Seattle\"}");

		AssistantMessage.ToolCall secondCall = retrievedAssistant.getToolCalls().get(1);
		assertThat(secondCall.id()).isEqualTo("call_def456");
		assertThat(secondCall.name()).isEqualTo("get_time");
	}

	/**
	 * Tests that ToolResponseMessage with responses survives a round-trip through
	 * Cosmos DB. This catches the bug where tool responses were stored without
	 * their id, name, and responseData, making them useless on retrieval.
	 */
	@Test
	void testToolResponseMessageRoundTrip() {
		var conversationId = UUID.randomUUID().toString();

		List<ToolResponseMessage.ToolResponse> responses = List.of(
				new ToolResponseMessage.ToolResponse("call_abc123", "get_weather",
						"{\"temp\": 72, \"condition\": \"sunny\"}"),
				new ToolResponseMessage.ToolResponse("call_def456", "get_time",
						"2024-01-15T10:30:00-08:00"));

		ToolResponseMessage toolMsg = ToolResponseMessage.builder()
			.responses(responses)
			.build();

		this.chatMemoryRepository.saveAll(conversationId, List.of(toolMsg));

		List<Message> retrieved = this.chatMemoryRepository.findByConversationId(conversationId);
		assertThat(retrieved).hasSize(1);
		assertThat(retrieved.get(0).getMessageType()).isEqualTo(MessageType.TOOL);

		ToolResponseMessage retrievedTool = (ToolResponseMessage) retrieved.get(0);
		assertThat(retrievedTool.getResponses()).hasSize(2);

		ToolResponseMessage.ToolResponse firstResponse = retrievedTool.getResponses().get(0);
		assertThat(firstResponse.id()).isEqualTo("call_abc123");
		assertThat(firstResponse.name()).isEqualTo("get_weather");
		assertThat(firstResponse.responseData()).isEqualTo("{\"temp\": 72, \"condition\": \"sunny\"}");

		ToolResponseMessage.ToolResponse secondResponse = retrievedTool.getResponses().get(1);
		assertThat(secondResponse.id()).isEqualTo("call_def456");
		assertThat(secondResponse.name()).isEqualTo("get_time");
		assertThat(secondResponse.responseData()).isEqualTo("2024-01-15T10:30:00-08:00");
	}

	/**
	 * Tests a full multi-turn conversation with tool calls, simulating the real
	 * agent workflow: User → Assistant(toolCalls) → Tool(responses) → Assistant(final).
	 * This catches the end-to-end bug where the conversation would break on replay.
	 */
	@Test
	void testFullToolCallConversationRoundTrip() {
		var conversationId = UUID.randomUUID().toString();

		UserMessage userMsg = new UserMessage("What's the weather in Seattle?");

		AssistantMessage assistantWithToolCall = AssistantMessage.builder()
			.content("")
			.toolCalls(List.of(new AssistantMessage.ToolCall("call_weather_1", "function",
					"get_weather", "{\"location\": \"Seattle\"}")))
			.build();

		ToolResponseMessage toolResponse = ToolResponseMessage.builder()
			.responses(List.of(new ToolResponseMessage.ToolResponse("call_weather_1",
					"get_weather", "{\"temp\": 55, \"condition\": \"rainy\"}")))
			.build();

		AssistantMessage finalResponse = new AssistantMessage(
				"The weather in Seattle is 55°F and rainy.");

		List<Message> conversation = List.of(userMsg, assistantWithToolCall, toolResponse, finalResponse);

		this.chatMemoryRepository.saveAll(conversationId, conversation);

		List<Message> retrieved = this.chatMemoryRepository.findByConversationId(conversationId);
		assertThat(retrieved).hasSize(4);

		// Verify message types in order
		assertThat(retrieved.get(0).getMessageType()).isEqualTo(MessageType.USER);
		assertThat(retrieved.get(1).getMessageType()).isEqualTo(MessageType.ASSISTANT);
		assertThat(retrieved.get(2).getMessageType()).isEqualTo(MessageType.TOOL);
		assertThat(retrieved.get(3).getMessageType()).isEqualTo(MessageType.ASSISTANT);

		// Verify tool call is preserved
		AssistantMessage retrievedAssistant = (AssistantMessage) retrieved.get(1);
		assertThat(retrievedAssistant.hasToolCalls()).isTrue();
		assertThat(retrievedAssistant.getToolCalls().get(0).id()).isEqualTo("call_weather_1");

		// Verify tool response is preserved
		ToolResponseMessage retrievedTool = (ToolResponseMessage) retrieved.get(2);
		assertThat(retrievedTool.getResponses().get(0).id()).isEqualTo("call_weather_1");
		assertThat(retrievedTool.getResponses().get(0).name()).isEqualTo("get_weather");

		// Verify final response
		assertThat(retrieved.get(3).getText()).contains("55°F");
	}

	@SpringBootConfiguration
	@EnableAutoConfiguration
	static class TestApplication {

		@Bean
		public CosmosAsyncClient cosmosAsyncClient() {
			return new CosmosClientBuilder().endpoint(EMULATOR_ENDPOINT)
				.key(EMULATOR_KEY)
				.userAgentSuffix("SpringAI-CDBNoSQL-ChatMemory-Emulator")
				.gatewayMode()
				.buildAsyncClient();
		}

		@Bean
		public CosmosDBChatMemoryRepositoryConfig config(CosmosAsyncClient cosmosAsyncClient) {
			return CosmosDBChatMemoryRepositoryConfig.builder()
				.withCosmosClient(cosmosAsyncClient)
				.withDatabaseName("emulator-test-db")
				.withContainerName("emulator-chat-memory")
				.build();
		}

		@Bean
		public CosmosDBChatMemoryRepository chatMemoryRepository(CosmosDBChatMemoryRepositoryConfig config) {
			return CosmosDBChatMemoryRepository.create(config);
		}

	}

}
