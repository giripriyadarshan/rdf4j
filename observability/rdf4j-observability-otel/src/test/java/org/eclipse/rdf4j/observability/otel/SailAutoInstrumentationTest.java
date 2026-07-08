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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.model.vocabulary.RDF;
import org.eclipse.rdf4j.query.TupleQueryResult;
import org.eclipse.rdf4j.repository.Repository;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.eclipse.rdf4j.repository.sail.SailRepository;
import org.eclipse.rdf4j.sail.memory.MemoryStore;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;

/**
 * Verifies the zero-code activation path: with this artifact on the classpath and the enable flag set, a plain
 * {@link SailRepository} emits engine-level spans through the {@code SailConnectionDecorator} service provider
 * interface — without any explicit {@code wrap(...)} call.
 */
class SailAutoInstrumentationTest {

	private static final AttributeKey<String> DB_SYSTEM_NAME = AttributeKey.stringKey("db.system.name");
	private static final AttributeKey<String> DB_OPERATION_NAME = AttributeKey.stringKey("db.operation.name");

	private static InMemorySpanExporter spanExporter;
	private static OpenTelemetrySdk sdk;

	@BeforeAll
	static void installGlobalOpenTelemetry() {
		GlobalOpenTelemetry.resetForTest();
		spanExporter = InMemorySpanExporter.create();
		sdk = OpenTelemetrySdk.builder()
				.setTracerProvider(SdkTracerProvider.builder()
						.addSpanProcessor(SimpleSpanProcessor.create(spanExporter))
						.build())
				.build();
		GlobalOpenTelemetry.set(sdk);
	}

	@AfterAll
	static void resetGlobalOpenTelemetry() {
		System.clearProperty(RuntimeConfiguration.ENABLED_PROPERTY);
		if (sdk != null) {
			sdk.close();
		}
		GlobalOpenTelemetry.resetForTest();
	}

	@AfterEach
	void cleanUp() {
		System.clearProperty(RuntimeConfiguration.ENABLED_PROPERTY);
		spanExporter.reset();
	}

	@Test
	void plainSailRepositoryEmitsSpansWhenFlagIsSet() {
		System.setProperty(RuntimeConfiguration.ENABLED_PROPERTY, "true");

		long rows = queryPlainRepository();

		assertThat(rows).isEqualTo(1);
		List<SpanData> spans = spanExporter.getFinishedSpanItems();
		assertThat(spans)
				.as("engine-level spans emitted without any explicit wrap() call")
				.isNotEmpty();

		SpanData evaluate = spans.stream()
				.filter(span -> "EVALUATE".equals(span.getAttributes().get(DB_OPERATION_NAME)))
				.findFirst()
				.orElseThrow(() -> new AssertionError("no EVALUATE span found in: " + spans));
		assertThat(evaluate.getKind()).isEqualTo(SpanKind.CLIENT);
		assertThat(evaluate.getAttributes().get(DB_SYSTEM_NAME)).isEqualTo("rdf4j");
	}

	@Test
	void plainSailRepositoryEmitsNothingWhenFlagIsUnset() {
		long rows = queryPlainRepository();

		assertThat(rows).isEqualTo(1);
		assertThat(spanExporter.getFinishedSpanItems()).isEmpty();
	}

	/** Creates a repository WITHOUT any telemetry wrapping, adds one statement and runs a SELECT. */
	private long queryPlainRepository() {
		Repository repository = new SailRepository(new MemoryStore());
		repository.init();
		try {
			ValueFactory vf = repository.getValueFactory();
			try (RepositoryConnection conn = repository.getConnection()) {
				conn.add(vf.createIRI("http://example.org/s"), RDF.TYPE, vf.createIRI("http://example.org/Thing"));
			}
			long rows = 0;
			try (RepositoryConnection conn = repository.getConnection();
					TupleQueryResult result = conn.prepareTupleQuery("SELECT ?s WHERE { ?s ?p ?o }").evaluate()) {
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
