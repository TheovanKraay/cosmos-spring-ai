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

package com.azure.spring.ai.vectorstore.cosmosdb;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.azure.cosmos.CosmosAsyncClient;
import com.azure.cosmos.CosmosAsyncContainer;
import com.azure.cosmos.CosmosClientBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.transformers.TransformersEmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.observation.VectorStoreObservationConvention;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Emulator-based integration tests for {@link CosmosDBVectorStore}.
 * Activated by the Maven 'emulator' profile. Uses key-based auth against the local
 * Cosmos DB Emulator.
 *
 * @author Theo van Kraay
 */
class CosmosDBVectorStoreEmulatorIT {

	private static final String EMULATOR_ENDPOINT = System.getProperty("cosmos.endpoint",
			"https://localhost:8081");

	private static final String EMULATOR_KEY = System.getProperty("cosmos.key",
			"C2y6yDjf5/R+ob0N8A7Cgv30VRDJIWEHLM+4QDU5DE2nQ9nDuVTqobD4b8mGGyPMbIZnqyMsEcaGQy67XIw/Jw==");

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withUserConfiguration(TestApplication.class);

	private VectorStore vectorStore;

	@BeforeEach
	public void setup() {
		this.contextRunner.run(context -> this.vectorStore = context.getBean(VectorStore.class));
	}

	@Test
	void testAddSearchAndDeleteDocuments() {
		Document document1 = new Document(UUID.randomUUID().toString(), "Spring AI integrates with Cosmos DB",
				Map.of("topic", "spring"));
		Document document2 = new Document(UUID.randomUUID().toString(), "Vector search enables semantic queries",
				Map.of("topic", "vectors"));

		this.vectorStore.add(List.of(document1, document2));

		List<Document> results = this.vectorStore
			.similaritySearch(SearchRequest.builder().query("semantic search").topK(5).build());

		assertThat(results).isNotEmpty();

		this.vectorStore.delete(List.of(document1.getId(), document2.getId()));

		List<Document> afterDelete = this.vectorStore
			.similaritySearch(SearchRequest.builder().query("semantic search").topK(5).build());
		assertThat(afterDelete).isEmpty();
	}

	@Test
	void testGetNativeClient() {
		this.contextRunner.run(context -> {
			CosmosDBVectorStore vs = context.getBean(CosmosDBVectorStore.class);
			Optional<CosmosAsyncContainer> nativeClient = vs.getNativeClient();
			assertThat(nativeClient).isPresent();
		});
	}

	@SpringBootConfiguration
	@EnableAutoConfiguration
	static class TestApplication {

		@Bean
		public VectorStore vectorStore(CosmosAsyncClient cosmosClient, EmbeddingModel embeddingModel) {
			return CosmosDBVectorStore.builder(cosmosClient, embeddingModel)
				.databaseName("emulator-test-db")
				.containerName("emulator-vector-store")
				.metadataFields(List.of("topic"))
				.vectorStoreThroughput(400)
				.build();
		}

		@Bean
		public CosmosAsyncClient cosmosClient() {
			return new CosmosClientBuilder().endpoint(EMULATOR_ENDPOINT)
				.key(EMULATOR_KEY)
				.userAgentSuffix("SpringAI-CDBNoSQL-VectorStore-Emulator")
				.gatewayMode()
				.buildAsyncClient();
		}

		@Bean
		public EmbeddingModel embeddingModel() {
			return new TransformersEmbeddingModel();
		}

		@Bean
		public VectorStoreObservationConvention observationConvention() {
			return new VectorStoreObservationConvention() {
			};
		}

	}

}
