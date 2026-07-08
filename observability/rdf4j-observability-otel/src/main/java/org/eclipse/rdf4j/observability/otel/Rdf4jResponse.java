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

/**
 * Result information collected when an instrumented operation ends, used as the {@code RESPONSE} type of the
 * OpenTelemetry {@code Instrumenter}.
 */
final class Rdf4jResponse {

	static final Rdf4jResponse EMPTY = new Rdf4jResponse(null);

	private final Long returnedRows;

	private Rdf4jResponse(Long returnedRows) {
		this.returnedRows = returnedRows;
	}

	static Rdf4jResponse ofRows(long returnedRows) {
		return new Rdf4jResponse(returnedRows);
	}

	/**
	 * @return the number of rows/statements returned by a streaming result, or {@code null} when not
	 *         applicable/counted.
	 */
	Long returnedRows() {
		return returnedRows;
	}
}
