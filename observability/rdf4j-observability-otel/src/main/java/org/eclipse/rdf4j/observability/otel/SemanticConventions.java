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

import java.util.Locale;

import io.opentelemetry.api.common.AttributeKey;

/**
 * OpenTelemetry database semantic convention attribute keys and the stability opt-in handling.
 * <p>
 * RDF4J is greenfield with respect to telemetry, so by default only the current <em>stable</em> database client
 * conventions are emitted ({@code db.system.name}, {@code db.query.text}, {@code db.operation.name},
 * {@code db.namespace}). The standard {@code OTEL_SEMCONV_STABILITY_OPT_IN} switch is honored so that operators running
 * a mixed fleet during the convention migration can request {@code database/dup} to additionally emit the older
 * experimental names ({@code db.system}, {@code db.statement}, {@code db.operation}).
 */
final class SemanticConventions {

	/** Instrumentation scope reported for every span and metric emitted by this module. */
	static final String INSTRUMENTATION_SCOPE_NAME = "org.eclipse.rdf4j.observability.otel";

	// Stable database client conventions.
	static final AttributeKey<String> DB_SYSTEM_NAME = AttributeKey.stringKey("db.system.name");
	static final AttributeKey<String> DB_NAMESPACE = AttributeKey.stringKey("db.namespace");
	static final AttributeKey<String> DB_QUERY_TEXT = AttributeKey.stringKey("db.query.text");
	static final AttributeKey<String> DB_QUERY_SUMMARY = AttributeKey.stringKey("db.query.summary");
	static final AttributeKey<String> DB_OPERATION_NAME = AttributeKey.stringKey("db.operation.name");
	static final AttributeKey<Long> DB_RESPONSE_RETURNED_ROWS = AttributeKey.longKey("db.response.returned_rows");
	static final AttributeKey<String> SERVER_ADDRESS = AttributeKey.stringKey("server.address");
	static final AttributeKey<Long> SERVER_PORT = AttributeKey.longKey("server.port");

	// Stable HTTP server conventions, used by the server-side tracing filter.
	static final AttributeKey<String> HTTP_REQUEST_METHOD = AttributeKey.stringKey("http.request.method");
	static final AttributeKey<String> HTTP_ROUTE = AttributeKey.stringKey("http.route");
	static final AttributeKey<Long> HTTP_RESPONSE_STATUS_CODE = AttributeKey.longKey("http.response.status_code");
	static final AttributeKey<String> URL_PATH = AttributeKey.stringKey("url.path");
	static final AttributeKey<String> ERROR_TYPE = AttributeKey.stringKey("error.type");

	// Legacy (experimental) database conventions, emitted only in dup mode.
	static final AttributeKey<String> DB_SYSTEM_LEGACY = AttributeKey.stringKey("db.system");
	static final AttributeKey<String> DB_STATEMENT_LEGACY = AttributeKey.stringKey("db.statement");
	static final AttributeKey<String> DB_OPERATION_LEGACY = AttributeKey.stringKey("db.operation");

	enum StabilityMode {
		/** Emit only the current stable database conventions (default). */
		STABLE,
		/** Emit both the stable and the legacy experimental conventions. */
		DUP;

		boolean emitStable() {
			return true;
		}

		boolean emitLegacy() {
			return this == DUP;
		}
	}

	private SemanticConventions() {
	}

	/**
	 * Resolves the stability mode from the standard {@code OTEL_SEMCONV_STABILITY_OPT_IN} environment variable (or the
	 * {@code otel.semconv-stability.opt-in} system property). A value containing {@code database/dup} selects
	 * {@link StabilityMode#DUP}; anything else (including unset) selects {@link StabilityMode#STABLE}.
	 */
	static StabilityMode resolveStabilityMode() {
		String raw = System.getProperty("otel.semconv-stability.opt-in");
		if (raw == null || raw.isEmpty()) {
			raw = System.getenv("OTEL_SEMCONV_STABILITY_OPT_IN");
		}
		if (raw == null) {
			return StabilityMode.STABLE;
		}
		for (String token : raw.toLowerCase(Locale.ROOT).split(",")) {
			if (token.trim().equals("database/dup")) {
				return StabilityMode.DUP;
			}
		}
		return StabilityMode.STABLE;
	}
}
