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

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.metrics.DoubleHistogram;
import io.opentelemetry.api.metrics.LongHistogram;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import io.opentelemetry.instrumentation.api.instrumenter.OperationListener;
import io.opentelemetry.instrumentation.api.instrumenter.OperationMetrics;

/**
 * Emits the OpenTelemetry database client metrics for every instrumented operation, following the database semantic
 * conventions:
 * <ul>
 * <li>{@code db.client.operation.duration} — histogram of operation durations in seconds; and</li>
 * <li>{@code db.client.response.returned_rows} — histogram of returned result rows, for streaming query results.</li>
 * </ul>
 * Only low-cardinality attributes are attached to metrics (never the query text). Registered on the
 * {@code Instrumenter} via {@code addOperationMetrics}, so metric timing is identical to span timing — including
 * streaming results, where the operation ends when the result is closed or exhausted.
 * <p>
 * This is a stable-API reimplementation of the (incubator-only) {@code DbClientMetrics}, kept local so that the module
 * never depends on alpha artifacts.
 */
final class Rdf4jClientMetrics implements OperationMetrics {

	/** Semantic-conventions advised bucket boundaries for {@code db.client.operation.duration}, in seconds. */
	private static final List<Double> DURATION_BUCKETS = Arrays.asList(0.001, 0.005, 0.01, 0.05, 0.1, 0.5, 1.0, 5.0,
			10.0);

	/** Low-cardinality attribute keys copied from span attributes onto metrics. */
	private static final List<AttributeKey<?>> METRIC_ATTRIBUTE_KEYS = Arrays.asList(
			SemanticConventions.DB_SYSTEM_NAME,
			SemanticConventions.DB_OPERATION_NAME,
			SemanticConventions.DB_NAMESPACE,
			SemanticConventions.SERVER_ADDRESS,
			SemanticConventions.SERVER_PORT,
			AttributeKey.stringKey("error.type"));

	private static final ContextKey<State> STATE_KEY = ContextKey
			.named("rdf4j-client-metrics-state");

	private static final Rdf4jClientMetrics INSTANCE = new Rdf4jClientMetrics();

	static OperationMetrics get() {
		return INSTANCE;
	}

	private Rdf4jClientMetrics() {
	}

	@Override
	public OperationListener create(Meter meter) {
		DoubleHistogram duration = meter.histogramBuilder("db.client.operation.duration")
				.setUnit("s")
				.setDescription("Duration of database client operations.")
				.setExplicitBucketBoundariesAdvice(DURATION_BUCKETS)
				.build();
		LongHistogram returnedRows = meter.histogramBuilder("db.client.response.returned_rows")
				.ofLongs()
				.setUnit("{row}")
				.setDescription("The actual number of records returned by the database operation.")
				.build();
		return new Listener(duration, returnedRows);
	}

	private static final class State {

		private final Attributes startAttributes;
		private final long startNanos;

		private State(Attributes startAttributes, long startNanos) {
			this.startAttributes = startAttributes;
			this.startNanos = startNanos;
		}
	}

	private static final class Listener implements OperationListener {

		private final DoubleHistogram duration;
		private final LongHistogram returnedRows;

		private Listener(DoubleHistogram duration, LongHistogram returnedRows) {
			this.duration = duration;
			this.returnedRows = returnedRows;
		}

		@Override
		public Context onStart(Context context, Attributes startAttributes, long startNanos) {
			return context.with(STATE_KEY, new State(startAttributes, startNanos));
		}

		@Override
		public void onEnd(Context context, Attributes endAttributes, long endNanos) {
			State state = context.get(STATE_KEY);
			if (state == null) {
				return;
			}
			Attributes attributes = metricAttributes(state.startAttributes, endAttributes);
			duration.record((endNanos - state.startNanos) / (double) TimeUnit.SECONDS.toNanos(1), attributes);
			Long rows = endAttributes.get(SemanticConventions.DB_RESPONSE_RETURNED_ROWS);
			if (rows != null) {
				returnedRows.record(rows, attributes);
			}
		}

		private static Attributes metricAttributes(Attributes startAttributes, Attributes endAttributes) {
			AttributesBuilder builder = Attributes.builder();
			for (AttributeKey<?> key : METRIC_ATTRIBUTE_KEYS) {
				copy(builder, key, startAttributes, endAttributes);
			}
			return builder.build();
		}

		@SuppressWarnings("unchecked")
		private static <T> void copy(AttributesBuilder builder, AttributeKey<T> key, Attributes startAttributes,
				Attributes endAttributes) {
			T value = endAttributes.get(key);
			if (value == null) {
				value = startAttributes.get(key);
			}
			if (value != null) {
				builder.put((AttributeKey<? super T>) key, value);
			}
		}
	}
}
