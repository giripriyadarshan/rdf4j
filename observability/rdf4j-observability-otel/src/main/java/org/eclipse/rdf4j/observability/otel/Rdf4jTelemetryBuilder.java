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

import java.util.Objects;

import org.eclipse.rdf4j.observability.otel.SemanticConventions.StabilityMode;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;

/**
 * Builds a {@link Rdf4jTelemetry} instance. All options have safe, privacy-preserving defaults; the only thing a caller
 * normally needs to provide is the {@link OpenTelemetry} instance.
 */
public final class Rdf4jTelemetryBuilder {

	private OpenTelemetry openTelemetry = OpenTelemetry.noop();
	private boolean openTelemetrySupplied = false;
	private String dbSystemName = "rdf4j";
	private boolean captureQueryText = true;
	private boolean statementSanitizationEnabled = true;

	Rdf4jTelemetryBuilder() {
	}

	/**
	 * Sets the {@link OpenTelemetry} instance spans and metrics are reported to. Defaults to
	 * {@link OpenTelemetry#noop()} so that, unless a real instance is supplied, the instrumentation records nothing and
	 * costs effectively nothing.
	 */
	public Rdf4jTelemetryBuilder setOpenTelemetry(OpenTelemetry openTelemetry) {
		this.openTelemetry = Objects.requireNonNull(openTelemetry, "openTelemetry");
		this.openTelemetrySupplied = true;
		return this;
	}

	/**
	 * Sets the value emitted as {@code db.system.name}. Defaults to {@code "rdf4j"}; deployments that brand their store
	 * differently (e.g. a specific RDF database product) may override it.
	 */
	public Rdf4jTelemetryBuilder setDbSystemName(String dbSystemName) {
		this.dbSystemName = Objects.requireNonNull(dbSystemName, "dbSystemName");
		return this;
	}

	/**
	 * Controls whether the query/update text is captured as {@code db.query.text}. Defaults to {@code true}.
	 */
	public Rdf4jTelemetryBuilder setCaptureQueryText(boolean captureQueryText) {
		this.captureQueryText = captureQueryText;
		return this;
	}

	/**
	 * Controls whether captured query text is sanitized (literal values redacted) before being recorded. Defaults to
	 * {@code true}; disable only when the recorded queries are known not to contain sensitive data.
	 */
	public Rdf4jTelemetryBuilder setStatementSanitizationEnabled(boolean statementSanitizationEnabled) {
		this.statementSanitizationEnabled = statementSanitizationEnabled;
		return this;
	}

	public Rdf4jTelemetry build() {
		StabilityMode stabilityMode = SemanticConventions.resolveStabilityMode();
		Rdf4jAttributesExtractor attributesExtractor = new Rdf4jAttributesExtractor(dbSystemName, captureQueryText,
				statementSanitizationEnabled, stabilityMode);
		Instrumenter<Rdf4jRequest, Rdf4jResponse> instrumenter = Instrumenter
				.<Rdf4jRequest, Rdf4jResponse>builder(openTelemetry, SemanticConventions.INSTRUMENTATION_SCOPE_NAME,
						new Rdf4jSpanNameExtractor())
				.addAttributesExtractor(attributesExtractor)
				.addOperationMetrics(Rdf4jClientMetrics.get())
				.buildInstrumenter(SpanKindExtractor.alwaysClient());
		return new Rdf4jTelemetry(new OperationInstrumenter(instrumenter), openTelemetrySupplied);
	}
}
