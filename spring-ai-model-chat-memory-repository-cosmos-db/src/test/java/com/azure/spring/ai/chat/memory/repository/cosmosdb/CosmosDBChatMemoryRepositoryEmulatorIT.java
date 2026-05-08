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

import java.util.List;
import java.util.UUID;

import com.azure.cosmos.CosmosAsyncClient;
import com.azure.cosmos.CosmosClientBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
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
