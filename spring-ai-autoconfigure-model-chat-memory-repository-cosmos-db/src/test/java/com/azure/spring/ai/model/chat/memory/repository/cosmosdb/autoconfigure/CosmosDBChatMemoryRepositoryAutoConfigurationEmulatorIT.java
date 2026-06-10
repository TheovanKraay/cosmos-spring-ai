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

package com.azure.spring.ai.model.chat.memory.repository.cosmosdb.autoconfigure;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.azure.spring.ai.chat.memory.repository.cosmosdb.CosmosDBChatMemoryRepository;
import com.azure.spring.ai.chat.memory.repository.cosmosdb.CosmosDBChatMemoryRepositoryConfig;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Emulator-based integration tests for {@link CosmosDBChatMemoryRepositoryAutoConfiguration}.
 * Activated by the Maven 'emulator' profile. Uses key-based auth against the local
 * Cosmos DB Emulator.
 *
 * @author Theo van Kraay
 */
class CosmosDBChatMemoryRepositoryAutoConfigurationEmulatorIT {

	private static final String EMULATOR_ENDPOINT = System.getProperty("cosmos.endpoint",
			"https://localhost:8081");

	private static final String EMULATOR_KEY = System.getProperty("cosmos.key",
			"C2y6yDjf5/R+ob0N8A7Cgv30VRDJIWEHLM+4QDU5DE2nQ9nDuVTqobD4b8mGGyPMbIZnqyMsEcaGQy67XIw/Jw==");

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(CosmosDBChatMemoryRepositoryAutoConfiguration.class))
		.withPropertyValues(
				"spring.ai.chat.memory.repository.cosmosdb.endpoint=" + EMULATOR_ENDPOINT,
				"spring.ai.chat.memory.repository.cosmosdb.key=" + EMULATOR_KEY,
				"spring.ai.chat.memory.repository.cosmosdb.database-name=emulator-autoconfig-db",
				"spring.ai.chat.memory.repository.cosmosdb.container-name=emulator-autoconfig-chat-memory");

	@Test
	void autoConfigurationCreatesBeans() {
		this.contextRunner.run(context -> {
			assertThat(context).hasSingleBean(CosmosDBChatMemoryRepository.class);
			assertThat(context).hasSingleBean(CosmosDBChatMemoryRepositoryConfig.class);
			assertThat(context).hasSingleBean(CosmosDBChatMemoryRepositoryProperties.class);
		});
	}

	@Test
	void saveAndRetrieveMessages() {
		this.contextRunner.run(context -> {
			CosmosDBChatMemoryRepository memory = context.getBean(CosmosDBChatMemoryRepository.class);

			String conversationId = UUID.randomUUID().toString();
			assertThat(memory.findByConversationId(conversationId)).isEmpty();

			memory.saveAll(conversationId,
					List.of(new UserMessage("Hello from emulator autoconfig test"),
							new AssistantMessage("Hello! Auto-configured on the emulator.")));

			List<org.springframework.ai.chat.messages.Message> retrieved = memory.findByConversationId(conversationId);
			assertThat(retrieved).hasSize(2);
			assertThat(retrieved.get(0).getMessageType()).isEqualTo(MessageType.USER);
			assertThat(retrieved.get(0).getText()).isEqualTo("Hello from emulator autoconfig test");
			assertThat(retrieved.get(1).getMessageType()).isEqualTo(MessageType.ASSISTANT);
			assertThat(retrieved.get(1).getText()).isEqualTo("Hello! Auto-configured on the emulator.");

			// Cleanup
			memory.deleteByConversationId(conversationId);
			assertThat(memory.findByConversationId(conversationId)).isEmpty();
		});
	}

	@Test
	void findConversationIds() {
		this.contextRunner.run(context -> {
			CosmosDBChatMemoryRepository memory = context.getBean(CosmosDBChatMemoryRepository.class);

			String id1 = UUID.randomUUID().toString();
			String id2 = UUID.randomUUID().toString();

			memory.saveAll(id1, List.of(new UserMessage("Question 1")));
			memory.saveAll(id2, List.of(new UserMessage("Question 2")));

			List<String> conversationIds = memory.findConversationIds();
			assertThat(conversationIds).contains(id1, id2);

			// Cleanup
			memory.deleteByConversationId(id1);
			memory.deleteByConversationId(id2);
		});
	}

	@Test
	void customProperties() {
		this.contextRunner
			.withPropertyValues(
					"spring.ai.chat.memory.repository.cosmosdb.partition-key-path=/customPartitionKey",
					"spring.ai.chat.memory.repository.cosmosdb.container-name=emulator-autoconfig-custom-pk")
			.run(context -> {
				CosmosDBChatMemoryRepositoryProperties properties = context
					.getBean(CosmosDBChatMemoryRepositoryProperties.class);
				assertThat(properties.getEndpoint()).isEqualTo(EMULATOR_ENDPOINT);
				assertThat(properties.getDatabaseName()).isEqualTo("emulator-autoconfig-db");
				assertThat(properties.getContainerName()).isEqualTo("emulator-autoconfig-custom-pk");
				assertThat(properties.getPartitionKeyPath()).isEqualTo("/customPartitionKey");
			});
	}

}
