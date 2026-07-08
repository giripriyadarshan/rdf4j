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
 * Immutable description of a single instrumented RDF4J operation, used as the {@code REQUEST} type of the OpenTelemetry
 * {@code Instrumenter}. It carries only what is needed to populate span attributes; the raw query text is kept here and
 * sanitized (or dropped) later by the attributes extractor according to configuration.
 */
final class Rdf4jRequest {

	private final SparqlOperation operation;
	private final String rawQueryText;
	private final String namespace;
	private final String serverAddress;
	private final Long serverPort;

	private Rdf4jRequest(SparqlOperation operation, String rawQueryText, String namespace, String serverAddress,
			Long serverPort) {
		this.operation = operation;
		this.rawQueryText = rawQueryText;
		this.namespace = namespace;
		this.serverAddress = serverAddress;
		this.serverPort = serverPort;
	}

	static Rdf4jRequest query(SparqlOperation operation, String rawQueryText, String namespace) {
		return new Rdf4jRequest(operation, rawQueryText, namespace, null, null);
	}

	static Rdf4jRequest transaction(SparqlOperation operation, String namespace) {
		return new Rdf4jRequest(operation, null, namespace, null, null);
	}

	static Rdf4jRequest remote(SparqlOperation operation, String rawQueryText, String namespace, String serverAddress,
			Long serverPort) {
		return new Rdf4jRequest(operation, rawQueryText, namespace, serverAddress, serverPort);
	}

	SparqlOperation operation() {
		return operation;
	}

	/**
	 * @return the unmodified query/update text, or {@code null} for operations that have none (e.g. transaction
	 *         boundaries). Sanitization is applied by the attributes extractor, not here.
	 */
	String rawQueryText() {
		return rawQueryText;
	}

	/**
	 * @return the target namespace (repository id or endpoint) if known, otherwise {@code null}.
	 */
	String namespace() {
		return namespace;
	}

	/**
	 * @return the remote server host if this is a remote operation, otherwise {@code null}.
	 */
	String serverAddress() {
		return serverAddress;
	}

	/**
	 * @return the remote server port if this is a remote operation and the port is known, otherwise {@code null}.
	 */
	Long serverPort() {
		return serverPort;
	}
}
