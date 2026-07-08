/*******************************************************************************
 * Copyright (c) 2026 Eclipse RDF4J contributors.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 *******************************************************************************/
package org.eclipse.rdf4j.observability.otel;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.any;
import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.eclipse.rdf4j.query.TupleQueryResult;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.eclipse.rdf4j.repository.sparql.SPARQLRepository;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.github.tomakehurst.wiremock.WireMockServer;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;

/**
 * Verifies the zero-code activation path for remote repositories: with this artifact on the classpath and the enable
 * flag set, a plain {@link SPARQLRepository} emits database client spans (including the sanitized SPARQL query text)
 * and propagates the W3C trace context to the remote endpoint — without any explicit {@code wrap(...)} call and without
 * an OpenTelemetry agent.
 */
class RemoteAutoInstrumentationTest {

	private static final AttributeKey<String> DB_SYSTEM_NAME = AttributeKey.stringKey("db.system.name");
	private static final AttributeKey<String> DB_OPERATION_NAME = AttributeKey.stringKey("db.operation.name");
	private static final AttributeKey<String> DB_QUERY_TEXT = AttributeKey.stringKey("db.query.text");
	private static final AttributeKey<String> SERVER_ADDRESS = AttributeKey.stringKey("server.address");

	private static final String SPARQL_JSON_RESULT = "{\"head\":{\"vars\":[\"s\"]},\"results\":{\"bindings\":"
			+ "[{\"s\":{\"type\":\"uri\",\"value\":\"http://example.org/s\"}}]}}";

	private static InMemorySpanExporter spanExporter;
	private static OpenTelemetrySdk sdk;
	private static WireMockServer wireMock;

	@BeforeAll
	static void setUp() {
		GlobalOpenTelemetry.resetForTest();
		spanExporter = InMemorySpanExporter.create();
		sdk = OpenTelemetrySdk.builder()
				.setTracerProvider(SdkTracerProvider.builder()
						.addSpanProcessor(SimpleSpanProcessor.create(spanExporter))
						.build())
				.setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
				.build();
		GlobalOpenTelemetry.set(sdk);

		wireMock = new WireMockServer(wireMockConfig().dynamicPort());
		wireMock.start();
		wireMock.stubFor(any(urlPathEqualTo("/sparql"))
				.willReturn(aResponse()
						.withHeader("Content-Type", "application/sparql-results+json")
						.withBody(SPARQL_JSON_RESULT)));
	}

	@AfterAll
	static void tearDown() {
		System.clearProperty(RuntimeConfiguration.ENABLED_PROPERTY);
		if (wireMock != null) {
			wireMock.stop();
		}
		if (sdk != null) {
			sdk.close();
		}
		GlobalOpenTelemetry.resetForTest();
	}

	@AfterEach
	void cleanUp() {
		System.clearProperty(RuntimeConfiguration.ENABLED_PROPERTY);
		spanExporter.reset();
		wireMock.resetRequests();
	}

	@Test
	void plainSparqlRepositoryEmitsSpanAndPropagatesTraceContext() {
		System.setProperty(RuntimeConfiguration.ENABLED_PROPERTY, "true");

		long rows = queryPlainRepository();

		assertThat(rows).isEqualTo(1);

		List<SpanData> spans = spanExporter.getFinishedSpanItems();
		SpanData select = spans.stream()
				.filter(span -> "SELECT".equals(span.getAttributes().get(DB_OPERATION_NAME)))
				.findFirst()
				.orElseThrow(() -> new AssertionError("no SELECT span found in: " + spans));

		assertThat(select.getKind()).isEqualTo(SpanKind.CLIENT);
		assertThat(select.getAttributes().get(DB_SYSTEM_NAME)).isEqualTo("rdf4j");
		assertThat(select.getAttributes().get(SERVER_ADDRESS)).isEqualTo("localhost");
		assertThat(select.getAttributes().get(DB_QUERY_TEXT))
				.contains("SELECT")
				.doesNotContain("secret-value");

		// W3C trace context propagated to the endpoint without any agent
		wireMock.verify(
				anyRequestedFor(anyUrl()).withHeader("traceparent", matching("00-[0-9a-f]{32}-[0-9a-f]{16}-.*")));
	}

	@Test
	void plainSparqlRepositoryEmitsNothingWhenFlagIsUnset() {
		long rows = queryPlainRepository();

		assertThat(rows).isEqualTo(1);
		assertThat(spanExporter.getFinishedSpanItems()).isEmpty();
		// no trace headers injected either
		assertThat(wireMock.getAllServeEvents())
				.allSatisfy(event -> assertThat(event.getRequest().getHeader("traceparent")).isNull());
	}

	/** Creates a remote repository WITHOUT any telemetry wrapping and runs a SELECT against the stub endpoint. */
	private long queryPlainRepository() {
		SPARQLRepository repository = new SPARQLRepository(wireMock.baseUrl() + "/sparql");
		repository.init();
		try {
			long rows = 0;
			try (RepositoryConnection conn = repository.getConnection();
					TupleQueryResult result = conn
							.prepareTupleQuery("SELECT ?s WHERE { ?s ?p \"secret-value\" }")
							.evaluate()) {
				while (result.hasNext()) {
					result.next();
					rows++;
				}
			}
			return rows;
		} finally {
			repository.shutDown();
		}
	}
}
