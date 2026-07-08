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
import org.eclipse.rdf4j.query.BooleanQuery;
import org.eclipse.rdf4j.query.GraphQueryResult;
import org.eclipse.rdf4j.query.TupleQuery;
import org.eclipse.rdf4j.query.TupleQueryResult;
import org.eclipse.rdf4j.repository.Repository;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.eclipse.rdf4j.repository.sail.SailRepository;
import org.eclipse.rdf4j.sail.memory.MemoryStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.testing.junit5.OpenTelemetryExtension;
import io.opentelemetry.sdk.trace.data.SpanData;

class Rdf4jTelemetryTest {

	@RegisterExtension
	static final OpenTelemetryExtension otelTesting = OpenTelemetryExtension.create();

	private static final AttributeKey<String> DB_SYSTEM_NAME = AttributeKey.stringKey("db.system.name");
	private static final AttributeKey<String> DB_OPERATION_NAME = AttributeKey.stringKey("db.operation.name");
	private static final AttributeKey<String> DB_NAMESPACE = AttributeKey.stringKey("db.namespace");
	private static final AttributeKey<String> DB_QUERY_TEXT = AttributeKey.stringKey("db.query.text");
	private static final AttributeKey<String> DB_QUERY_SUMMARY = AttributeKey.stringKey("db.query.summary");
	private static final AttributeKey<Long> DB_RESPONSE_RETURNED_ROWS = AttributeKey
			.longKey("db.response.returned_rows");

	private Repository repository;

	@BeforeEach
	void setUp() {
		repository = new SailRepository(new MemoryStore());
		repository.init();
		ValueFactory vf = repository.getValueFactory();
		try (RepositoryConnection conn = repository.getConnection()) {
			conn.add(vf.createIRI("http://example.org/s"), RDF.TYPE, vf.createIRI("http://example.org/Thing"));
			conn.add(vf.createIRI("http://example.org/s"), vf.createIRI("http://example.org/name"),
					vf.createLiteral("secret-name"));
		}
	}

	@AfterEach
	void tearDown() {
		repository.shutDown();
	}

	@Test
	void selectQueryEmitsClientSpanWithSanitizedText() {
		Rdf4jTelemetry telemetry = Rdf4jTelemetry.create(otelTesting.getOpenTelemetry());
		Repository wrapped = telemetry.wrap(repository, "test-repo");

		long rows = 0;
		try (RepositoryConnection conn = wrapped.getConnection()) {
			TupleQuery query = conn.prepareTupleQuery("SELECT ?s WHERE { ?s ?p \"secret-name\" }");
			try (TupleQueryResult result = query.evaluate()) {
				while (result.hasNext()) {
					result.next();
					rows++;
				}
			}
		}

		assertThat(rows).isEqualTo(1);
		List<SpanData> spans = otelTesting.getSpans();
		assertThat(spans).hasSize(1);

		SpanData span = spans.get(0);
		assertThat(span.getKind()).isEqualTo(SpanKind.CLIENT);
		assertThat(span.getName()).isEqualTo("SELECT test-repo");

		Attributes attributes = span.getAttributes();
		assertThat(attributes.get(DB_SYSTEM_NAME)).isEqualTo("rdf4j");
		assertThat(attributes.get(DB_OPERATION_NAME)).isEqualTo("SELECT");
		assertThat(attributes.get(DB_NAMESPACE)).isEqualTo("test-repo");
		assertThat(attributes.get(DB_QUERY_SUMMARY)).isEqualTo("SELECT test-repo");
		assertThat(attributes.get(DB_RESPONSE_RETURNED_ROWS)).isEqualTo(1L);
		assertThat(attributes.get(DB_QUERY_TEXT))
				.doesNotContain("secret-name")
				.contains("SELECT");
	}

	@Test
	void askQueryEmitsSpan() {
		Rdf4jTelemetry telemetry = Rdf4jTelemetry.create(otelTesting.getOpenTelemetry());
		Repository wrapped = telemetry.wrap(repository, "test-repo");

		boolean answer;
		try (RepositoryConnection conn = wrapped.getConnection()) {
			BooleanQuery query = conn.prepareBooleanQuery("ASK { ?s ?p ?o }");
			answer = query.evaluate();
		}

		assertThat(answer).isTrue();
		List<SpanData> spans = otelTesting.getSpans();
		assertThat(spans).hasSize(1);
		assertThat(spans.get(0).getName()).isEqualTo("ASK test-repo");
		assertThat(spans.get(0).getAttributes().get(DB_OPERATION_NAME)).isEqualTo("ASK");
	}

	@Test
	void constructQueryEmitsGraphSpan() {
		Rdf4jTelemetry telemetry = Rdf4jTelemetry.create(otelTesting.getOpenTelemetry());
		Repository wrapped = telemetry.wrap(repository, "test-repo");

		long statements = 0;
		try (RepositoryConnection conn = wrapped.getConnection()) {
			try (GraphQueryResult result = conn
					.prepareGraphQuery("CONSTRUCT { ?s ?p ?o } WHERE { ?s ?p ?o }")
					.evaluate()) {
				while (result.hasNext()) {
					result.next();
					statements++;
				}
			}
		}

		assertThat(statements).isEqualTo(2);
		List<SpanData> spans = otelTesting.getSpans();
		assertThat(spans).hasSize(1);
		assertThat(spans.get(0).getAttributes().get(DB_OPERATION_NAME)).isEqualTo("CONSTRUCT");
		assertThat(spans.get(0).getAttributes().get(DB_RESPONSE_RETURNED_ROWS)).isEqualTo(2L);
	}

	@Test
	void updateAndTransactionBoundariesEmitSpans() {
		Rdf4jTelemetry telemetry = Rdf4jTelemetry.create(otelTesting.getOpenTelemetry());
		Repository wrapped = telemetry.wrap(repository, "test-repo");

		try (RepositoryConnection conn = wrapped.getConnection()) {
			conn.begin();
			conn.prepareUpdate("INSERT DATA { <http://example.org/a> <http://example.org/b> <http://example.org/c> }")
					.execute();
			conn.commit();
		}

		assertThat(otelTesting.getSpans())
				.extracting(span -> span.getAttributes().get(DB_OPERATION_NAME))
				.containsExactlyInAnyOrder("BEGIN", "UPDATE", "COMMIT");
	}

	@Test
	void queryEmitsDurationAndReturnedRowsMetrics() {
		Rdf4jTelemetry telemetry = Rdf4jTelemetry.create(otelTesting.getOpenTelemetry());
		Repository wrapped = telemetry.wrap(repository, "test-repo");

		try (RepositoryConnection conn = wrapped.getConnection();
				TupleQueryResult result = conn.prepareTupleQuery("SELECT ?s WHERE { ?s ?p ?o }").evaluate()) {
			while (result.hasNext()) {
				result.next();
			}
		}

		List<MetricData> metrics = otelTesting.getMetrics();

		MetricData duration = metrics.stream()
				.filter(metric -> "db.client.operation.duration".equals(metric.getName()))
				.findFirst()
				.orElseThrow(() -> new AssertionError("no duration metric found in: " + metrics));
		assertThat(duration.getHistogramData().getPoints())
				.anySatisfy(point -> {
					assertThat(point.getAttributes().get(DB_SYSTEM_NAME)).isEqualTo("rdf4j");
					assertThat(point.getAttributes().get(DB_OPERATION_NAME)).isEqualTo("SELECT");
					assertThat(point.getAttributes().get(DB_NAMESPACE)).isEqualTo("test-repo");
					// metrics must never carry high-cardinality attributes
					assertThat(point.getAttributes().get(DB_QUERY_TEXT)).isNull();
					assertThat(point.getSum()).isGreaterThan(0);
				});

		MetricData returnedRows = metrics.stream()
				.filter(metric -> "db.client.response.returned_rows".equals(metric.getName()))
				.findFirst()
				.orElseThrow(() -> new AssertionError("no returned_rows metric found in: " + metrics));
		assertThat(returnedRows.getHistogramData().getPoints())
				.anySatisfy(point -> assertThat(point.getSum()).isEqualTo(2));
	}

	@Test
	void enabledStateReflectsHowTelemetryWasCreated() {
		assertThat(Rdf4jTelemetry.create(otelTesting.getOpenTelemetry()).isEnabled()).isTrue();
		assertThat(Rdf4jTelemetry.noop().isEnabled()).isFalse();
		// the enable flag is not set in this test JVM, so fromConfiguration() must be disabled
		assertThat(Rdf4jTelemetry.fromConfiguration().isEnabled()).isFalse();
	}

	@Test
	void noopTelemetryRecordsNothing() {
		Rdf4jTelemetry telemetry = Rdf4jTelemetry.noop();
		Repository wrapped = telemetry.wrap(repository, "test-repo");

		long rows = 0;
		try (RepositoryConnection conn = wrapped.getConnection()) {
			conn.begin();
			try (TupleQueryResult result = conn.prepareTupleQuery("SELECT ?s WHERE { ?s ?p ?o }").evaluate()) {
				while (result.hasNext()) {
					result.next();
					rows++;
				}
			}
			conn.commit();
		}

		// The wrapped connection still works...
		assertThat(rows).isEqualTo(2);
		// ...but a no-op telemetry emits nothing to the SDK.
		assertThat(otelTesting.getSpans()).isEmpty();
	}
}
