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

/**
 * Constants for the Azure Cosmos DB Vector Store community module.
 *
 * @author Theo van Kraay
 */
public final class CosmosDBVectorStoreConstants {

	private CosmosDBVectorStoreConstants() {
	}

	/**
	 * The observation provider name for Cosmos DB vector store telemetry.
	 */
	public static final String PROVIDER_NAME = "cosmosdb";

	/**
	 * The vector store type identifier used for Spring Boot conditional configuration
	 * ({@code spring.ai.vectorstore.type=azure-cosmos-db}).
	 */
	public static final String VECTOR_STORE_TYPE = "azure-cosmos-db";

}
