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
import java.util.UUID;

import com.azure.cosmos.CosmosAsyncClient;
import com.azure.cosmos.CosmosClientBuilder;
import com.azure.cosmos.models.CosmosVectorIndexType;
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
 * Direct mode emulator-based integration test for {@link CosmosDBVectorStore}.
 * Validates that the azure-spring-data-cosmos 7.3.0 upgrade enables Direct/RNTBD
 * connectivity with Netty 4.2. Uses Direct mode against the local Cosmos DB Emulator.
 *
 * @author Theo van Kraay
 */
class CosmosDBVectorStoreDirectModeEmulatorIT {

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

	/**
	 * Tests basic add/search/delete using Direct mode (RNTBD protocol).
	 * This validates the Netty 4.2 + azure-spring-data-cosmos 7.3.0 fix.
	 */
	@Test
	void testDirectModeAddSearchAndDelete() {
		Document document = new Document(UUID.randomUUID().toString(),
				"Direct mode uses RNTBD protocol for lower latency",
				Map.of("topic", "connectivity"));

		this.vectorStore.add(List.of(document));

		List<Document> results = this.vectorStore
			.similaritySearch(SearchRequest.builder().query("RNTBD protocol latency").topK(1).build());

		assertThat(results).isNotEmpty();
		assertThat(results.get(0).getId()).isEqualTo(document.getId());
		assertThat(results.get(0).getText()).isEqualTo("Direct mode uses RNTBD protocol for lower latency");

		this.vectorStore.delete(List.of(document.getId()));

		List<Document> afterDelete = this.vectorStore
			.similaritySearch(SearchRequest.builder().query("RNTBD protocol latency").topK(1).build());
		assertThat(afterDelete).isEmpty();
	}

	/**
	 * Tests that multiple documents round-trip correctly via Direct mode.
	 */
	@Test
	void testDirectModeBulkOperations() {
		List<Document> documents = List.of(
				new Document(UUID.randomUUID().toString(), "Direct mode document one", Map.of("index", "1")),
				new Document(UUID.randomUUID().toString(), "Direct mode document two", Map.of("index", "2")));

		this.vectorStore.add(documents);

		List<Document> results = this.vectorStore
			.similaritySearch(SearchRequest.builder().query("Direct mode document").topK(5).build());

		assertThat(results).hasSizeGreaterThanOrEqualTo(2);

		this.vectorStore.delete(documents.stream().map(Document::getId).toList());
	}

	@SpringBootConfiguration
	@EnableAutoConfiguration
	static class TestApplication {

		@Bean
		public VectorStore vectorStore(CosmosAsyncClient cosmosClient, EmbeddingModel embeddingModel) {
			return CosmosDBVectorStore.builder(cosmosClient, embeddingModel)
				.databaseName("emulator-test-db")
				.containerName("emulator-vector-store-direct")
				.metadataFields(List.of("topic", "index"))
				.vectorStoreThroughput(400)
				.vectorIndexType(CosmosVectorIndexType.FLAT)
				.vectorDimensions(384)
				.build();
		}

		@Bean
		public CosmosAsyncClient cosmosClient() {
			return new CosmosClientBuilder().endpoint(EMULATOR_ENDPOINT)
				.key(EMULATOR_KEY)
				.userAgentSuffix("SpringAI-CDBNoSQL-VectorStore-DirectMode")
				.directMode()
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
