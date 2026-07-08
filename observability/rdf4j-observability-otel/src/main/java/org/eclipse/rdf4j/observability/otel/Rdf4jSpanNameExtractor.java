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

import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;

/**
 * Produces low-cardinality span names following the database semantic convention priority: use the query summary,
 * formatted as {@code "{db.operation.name} {target}"} (e.g. {@code "SELECT myRepo"}, {@code "COMMIT"}). The raw query
 * text is never used as a span name.
 */
final class Rdf4jSpanNameExtractor implements SpanNameExtractor<Rdf4jRequest> {

	@Override
	public String extract(Rdf4jRequest request) {
		String operation = request.operation().name();
		String namespace = request.namespace();
		if (namespace == null || namespace.isEmpty()) {
			return operation;
		}
		return operation + " " + namespace;
	}
}
