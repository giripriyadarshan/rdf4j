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

import org.eclipse.rdf4j.observability.otel.SemanticConventions.StabilityMode;

import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;

/**
 * Populates OpenTelemetry span attributes for an RDF4J operation using the database semantic conventions. The set of
 * attribute keys emitted (stable only, or stable plus legacy) is decided once at construction time from the resolved
 * {@link StabilityMode}.
 */
final class Rdf4jAttributesExtractor implements AttributesExtractor<Rdf4jRequest, Rdf4jResponse> {

	private final String dbSystemName;
	private final boolean captureQueryText;
	private final boolean sanitizeQueryText;
	private final StabilityMode stabilityMode;

	Rdf4jAttributesExtractor(String dbSystemName, boolean captureQueryText, boolean sanitizeQueryText,
			StabilityMode stabilityMode) {
		this.dbSystemName = dbSystemName;
		this.captureQueryText = captureQueryText;
		this.sanitizeQueryText = sanitizeQueryText;
		this.stabilityMode = stabilityMode;
	}

	@Override
	public void onStart(AttributesBuilder attributes, Context parentContext, Rdf4jRequest request) {
		String operation = request.operation().name();
		String namespace = request.namespace();
		String summary = (namespace == null || namespace.isEmpty()) ? operation : operation + " " + namespace;
		String queryText = queryText(request);

		if (stabilityMode.emitStable()) {
			attributes.put(SemanticConventions.DB_SYSTEM_NAME, dbSystemName);
			attributes.put(SemanticConventions.DB_OPERATION_NAME, operation);
			attributes.put(SemanticConventions.DB_QUERY_SUMMARY, summary);
			if (namespace != null && !namespace.isEmpty()) {
				attributes.put(SemanticConventions.DB_NAMESPACE, namespace);
			}
			if (queryText != null) {
				attributes.put(SemanticConventions.DB_QUERY_TEXT, queryText);
			}
			if (request.serverAddress() != null) {
				attributes.put(SemanticConventions.SERVER_ADDRESS, request.serverAddress());
			}
			if (request.serverPort() != null) {
				attributes.put(SemanticConventions.SERVER_PORT, request.serverPort());
			}
		}
		if (stabilityMode.emitLegacy()) {
			attributes.put(SemanticConventions.DB_SYSTEM_LEGACY, dbSystemName);
			attributes.put(SemanticConventions.DB_OPERATION_LEGACY, operation);
			if (queryText != null) {
				attributes.put(SemanticConventions.DB_STATEMENT_LEGACY, queryText);
			}
		}
	}

	@Override
	public void onEnd(AttributesBuilder attributes, Context context, Rdf4jRequest request, Rdf4jResponse response,
			Throwable error) {
		if (stabilityMode.emitStable() && response != null && response.returnedRows() != null) {
			attributes.put(SemanticConventions.DB_RESPONSE_RETURNED_ROWS, response.returnedRows());
		}
	}

	private String queryText(Rdf4jRequest request) {
		if (!captureQueryText) {
			return null;
		}
		String raw = request.rawQueryText();
		if (raw == null) {
			return null;
		}
		return sanitizeQueryText ? SparqlSanitizer.sanitize(raw) : raw;
	}
}
